# Redesign — Technical Implementation

> Companion to `redesign-what-and-why.md`. Describes *how* to build the redesign in the
> existing Clean/Hexagonal architecture (domain models → use cases → infrastructure,
> Spring Boot, PostgreSQL + Redis). Design-level; no code yet. File references point at
> the current tree to ground each change.

## 1. Core architectural principle

Separate **computing facts** from **phrasing facts**:

```
transactions / budgets / projects  →  [deterministic analyzer]  →  Insight[]  →  [LLM narrator]  →  text
        (data already stored)          (pure domain logic)         (structured)   (existing LlmPort)
```

Consequences:
- The analyzer is a pure domain service — no Spring, no network — fully unit-testable.
  This is the "algorithm" core, and the part to defend in a thesis.
- The LLM never does arithmetic (it's bad at it and you can't verify it). If Gemini is
  down, raw insights still return.
- `UserFinancialDataService` (`application/services/UserFinancialDataService.java`)
  currently *fuses* compute + prompt-rendering into one Vietnamese string
  (lines 74–157). First refactor: split "compute structured facts" from "render to
  prompt."

## 2. New domain objects

Three distinct lifetimes — keep them separate; do **not** overload `Notification`.

| Object | Lifetime | Role |
|---|---|---|
| `Insight` | ephemeral, computed | the finding — "FOOD is 2σ over baseline" |
| `Suggestion` | **persisted, stateful** | the decision object — insight snapshot + proposed action + status |
| `Notification` (exists) | delivery record | the message shown; `data`/`deepLink` points at the suggestion |

### 2.1 `Insight` (domain value object, ephemeral)

```
Insight {
  type      // OVERSPEND_ANOMALY | BUDGET_PACE | RECURRING_DETECTED | SAVINGS_CAPACITY | CATEGORY_TREND
  category  // Category, nullable
  severity  // INFO | WARN | CRITICAL
  period    // month/year
  metrics   // { baseline, actual, zScore, projectedEndOfMonth, ... } — structured, not prose
  code      // stable key for i18n / front-end
}
```
Plain domain model alongside `Budget` / `Transaction`. Produced by a pure domain
service `SpendingInsightAnalyzer` (same shape as the existing `AuthDomainService`).

### 2.2 `Suggestion` (persisted, stateful) — only for actionable yes/no pushes

```
Suggestion {
  id, userId
  type            // RAISE_BUDGET, CREATE_BUDGET_FROM_RECURRING, CONTRIBUTE_TO_PROJECT, ...
  status          // PENDING → ACCEPTED | DISMISSED | EXPIRED
  payload         // jsonb: { insightSnapshot, proposedAction: { category, resolvedValue } }
  dedupKey        // (type + category + period) → one open ask at a time
  createdAt, expiresAt, decidedAt
}
```

## 3. The analyzer — methods and why each (not the fancier alternative)

| Signal | Method | Why this |
|---|---|---|
| "Unusual for you" | **rolling mean + stddev → z-score** over trailing N months/category | simplest defensible anomaly detector; works at low volume; explainable in one sentence |
| "Suggest a budget" | **trailing 3-month median** per category | robust to a single big purchase inflating the figure |
| "You'll exceed this budget" | **linear pace projection** `spent / dayOfMonth × daysInMonth` | trivial; all data already in `Budget` |
| "Your fixed costs" | **recurring detection** — bucket by amount + ~monthly cadence | reconstructs the dropped "obligations" from real behavior |
| "Next-month forecast" (optional) | **exponential smoothing** (one-line recurrence) | beats linear on noisy series, no library; build only if needed |
| natural-language phrasing | **existing `LlmPort`**, fed structured `Insight[]` | LLM good at wording, bad at math; strictly downstream of verified numbers |

**Explicitly not building:** ML training pipeline, clustering/neural libs, Kafka/event
sourcing, a separate analytics DB, per-transaction real-time recompute, microservice
split. Unjustifiable at capstone data scale; each adds defense surface.

Reuse for baselines: `GetTransactionAnalyticsUseCase` already does monthly/category
`GROUP BY` aggregation in SQL — the same queries feed the analyzer.

## 4. Layering (mirrors the existing structure)

- **Domain** — `Insight`, `Suggestion` models; `SpendingInsightAnalyzer` domain service
  (pure: `(transactions, budgets, projects) → List<Insight>`).
- **Application** — `GenerateInsightsUseCase` (load via repos → analyzer → optional LLM
  narration → return); `RespondToSuggestionUseCase` (yes/no handler); a producer that
  decides which insights become persisted `Suggestion`s.
- **Infrastructure** — reuse `GeminiLlmAdapter`/`LlmPort`, the Expo push stack, and the
  Redis `NotificationQueuePort`. Add a `SuggestionRepository` + table.

## 5. Project changes (Pivot B — press-to-contribute)

### 5.1 The one trap: progress reads the wrong source today
`Project.totalContributed()` (`domain/models/project/Project.java:194`) reads the
settlement `tracking` (`netSaved = moneySaved − moneyOwed`) and **ignores** the
`contributions` list. Wiring a contribute button without changing this means the bar
won't move.

**Fix:** re-source progress from the `contributions` list (or have `addContribution`
write into `tracking.moneySaved`), and delete the `moneyOwed`/debt half of
`ProjectTracking`.

### 5.2 Delete
- `PerformMonthlySettlementUseCase` + its monthly cron.
- Debt mechanic: `moneyOwed`, `systemFreeze()` / `systemUnfreeze()`
  (`Project.java:279-290`), `freeze-mult` / `unfreeze-mult` / `max-frozen-months`
  tunables, auto-invest-surplus, surplus-repays-debt.
- `netSaved` collapses to `sum(contributions)`.

### 5.3 Keep / move
- Owner actions `freeze` / `abandon` stay (user-driven, not settlement).
- **Completion** moves into the contribute use case: after `addContribution`, if
  `totalContributed ≥ target`, call `complete()`. Synchronous, no cron.
- **Expiry** becomes lazy-on-read: `deadline < today && progress < 100 → EXPIRED`
  (or fold into a digest job). No dedicated cron.

### 5.4 Contribution = a real transaction (recommended)
Make contributing **one atomic `@Transactional` use case** that writes the ledger entry
and advances the project together — they can never diverge. Prefer *a `Transaction`
carrying a `projectId`* over parallel `ProjectContribution` + `Transaction` rows
(avoids double bookkeeping). Cost: a small `Category`/type tag for project transfers
(precedent: `PROJECT_REFUND` already exists in the `Category` enum). Bonus: the insight
engine then sees savings as just another categorized transaction.

### 5.5 Advisor becomes advisory
`ProjectBudgetAdvisorUseCase` (`application/usecases/project/`) currently enforces the
20% usable-income rule and throws `PROJECT_USABLE_INCOME_LIMIT_EXCEEDED`. After Pivot A
it computes against net income (or learned disposable) and **suggests** without blocking.

## 6. The yes/no suggestion feature — mechanics

### 6.1 Where state lives
**Postgres**, not Redis. A `Suggestion` is a record of financial consent — durable and
auditable. Redis stays the *transport* (the existing notification queue).

### 6.2 Minimal schema — one JSONB column, not twelve
```sql
suggestion (
  id          uuid primary key,
  user_id     uuid not null,
  type        text not null,            -- RAISE_BUDGET, ...
  status      text not null,            -- PENDING | ACCEPTED | DISMISSED | EXPIRED
  payload     jsonb not null,           -- { insightSnapshot, proposedAction:{category, resolvedValue} }
  dedup_key   text not null,
  created_at  timestamptz not null,
  expires_at  timestamptz,
  decided_at  timestamptz
)
```
JSONB lets the action shape evolve without a migration per idea. Promote a field to a
column only once it stabilizes and you need to query inside it.

### 6.3 Round trip
```
producer → INSERT suggestion(PENDING) → enqueue push (existing Redis queue)
                                            → Expo `data`: { suggestionId, type }
device   → deep-links into an IN-APP card with two buttons (Notification.deepLink)
tap      → POST /suggestions/{id}/respond { accept: bool }
server   → guarded transition + (execute | dismiss)
```
**Decision:** do **not** use OS-level notification action buttons (inconsistent across
Expo/FCM/APNs). The push deep-links to an in-app card; yes/no are two normal API calls.
The Expo sender already forwards an arbitrary `data` object
(`ExpoPushNotificationSender.java:21-28`), so carrying `suggestionId` is free.

### 6.4 Correctness pushed into the database
**Idempotent decision** (double-tap / retry executes once):
```sql
UPDATE suggestion SET status='ACCEPTED', decided_at=now()
WHERE id=? AND status='PENDING';   -- 0 rows affected → already decided → no-op
```
The action runs only if that update touched a row. No locks.

**Dedup** (one open ask per topic):
```sql
CREATE UNIQUE INDEX ON suggestion (user_id, dedup_key) WHERE status='PENDING';
```
Producer just inserts; a conflict means an identical ask is pending → skip.

### 6.5 Action execution
- Whitelist a tiny set of action types; a `switch` on `type` mapping to existing use
  cases. **No** generic command/workflow/rules engine.
- **Store the resolved value, not a recompute recipe.** The user consents to a specific
  number (e.g. "raise FOOD to 2.5M"); re-validate only for sanity at execution, but
  honor what they tapped.
- Status flip **and** mutation in the **same `@Transactional`** (all actions are
  internal). No outbox/saga yet.

### 6.6 Triggering — reuse, don't add infra
Two producers feed the existing `NotificationWorker` + Redis queue:
- **Synchronous** on transaction-create — "you just blew past your food budget — raise it?"
- **Scheduled digest** — mid-month pace check / weekly summary. (Deleting the settlement
  cron frees a slot; the scheduling pattern is already proven in the codebase.)

The analyzer (pure, stateless) is shared: it serves the in-app insights screen on demand
*and* feeds the proactive path that persists selected insights as `Suggestion`s.

### 6.7 Fatigue control (where the real effort goes)
- **Dedup** — partial unique index above.
- **Severity gate** — only push `WARN`/`CRITICAL`; the rest stay pull-only in-app.
- **Cooldown / daily cap** — deferrable; the dedup index already blocks duplicate asks.

### 6.8 Suggestion catalog

Two classes — keep both; not every smart thing is a yes/no push (forcing every insight
into a mutation is an over-engineering trap and a fatigue source):
- **Actionable** — yes/no `Suggestion`, action = thin map to a whitelisted use case.
- **Informational** — `Insight` only; pull screen or digest; no mutation.

Grouped by the `focus` trait (§7), which selects the surfacing bucket. `goalPush` scales
amounts; `overspendHelp` sets the push-severity threshold. The same catalog therefore
behaves very differently per user — that is the personalization, for free.

**A. Savings & projects** (`focus = BUILD_SAVINGS`, scaled by `goalPush`)

| Suggestion | Trigger (deterministic) | Action → use case |
|---|---|---|
| Suggest a project | positive disposable for N months **and** no active project | `CREATE_PROJECT`, pre-filled (target ≈ surplus run-rate × `goalPush`, suggested deadline) |
| Contribute your surplus | this month's disposable > 0 **and** active project exists | `CONTRIBUTE_TO_PROJECT`, amount = surplus |
| Catch-up nudge | project behind pace vs deadline | `CONTRIBUTE_TO_PROJECT`, catch-up amount |
| Almost there | project ≥ ~90% | `CONTRIBUTE_TO_PROJECT` |
| Re-plan a stuck goal | chronically behind **and** disposable can't support pace | `UPDATE_PROJECT` (extend deadline / lower target) |

**B. Waste & budgets** (`focus = CUT_WASTE`)

| Suggestion | Trigger | Action |
|---|---|---|
| Raise a budget (canonical / Phase 1) | chronic overspend vs limit | `UPDATE_BUDGET` ↑ to trailing median |
| Lower a budget | chronic underspend (<50% for 3 months) | `UPDATE_BUDGET` ↓, optional redirect to project |
| Budget an unbudgeted category | recurring spend in a category with no budget | `CREATE_BUDGET` at observed median |
| Draft my budgets (cold start) | transactions exist but **no budgets** | `CREATE_BULK_BUDGETS` (existing `CreateBulkBudgetsRequest`) |
| Subscription review | recurring-charge detector fires | **INFO / `ACKNOWLEDGE`** — can't cancel external subs; not a mutation |

**C. Awareness & accuracy** (`focus = STAY_AWARE`)

| Suggestion | Trigger | Action |
|---|---|---|
| Budget pace alert | mid-month projection > limit | INFO, or `UPDATE_BUDGET` if `overspendHelp ≥ NUDGE` |
| Spending anomaly | z-score spike vs baseline | INFO |
| Category creep | category rising 3 months straight | INFO |
| Income drift | logged income diverges from declared net income (Pivot A) | `UPDATE_INCOME` |
| Safe-to-spend / digest | periodic | INFO |

**Action whitelist** (extends §6.5; all map to existing or trivial use cases, still a
`switch` on `type`, no generic engine): `CREATE_PROJECT`, `CONTRIBUTE_TO_PROJECT`,
`UPDATE_PROJECT`, `CREATE_BUDGET`, `UPDATE_BUDGET`, `CREATE_BULK_BUDGETS`, `UPDATE_INCOME`,
`ACKNOWLEDGE` (no-op/snooze).

**In-bounds guardrails:** no automation — every action is one user tap, never auto-applied
(auto-execution is the deleted settlement engine); deterministic trigger + LLM narration
only; traits gate all of it. **Phase 1 ships only `RAISE_BUDGET`** — this catalog is the
Phase 2–3 expansion, not a build-it-all-at-once list.

## 7. User traits (Pivot D) — cold-start policy knobs

### 7.1 What's removed
`UserFinancialProfile` (`domain/models/userprofile/UserFinancialProfile.java`) and its nine
required enum fields, the matching DTOs/repo, and the `PROFILE` branch in
`UserFinancialDataService` (lines 78–84). Those fields were only rendered into an LLM
prompt — nothing acted on them.

### 7.2 What replaces it
A small value object (3 enum fields) — keep it lean; this is policy, not a profile:
```
UserTraits {
  goalPush       // RELAXED | STEADY | AGGRESSIVE
  overspendHelp  // TELL_ME | NUDGE_ME | HARD_LIMIT
  focus          // BUILD_SAVINGS | CUT_WASTE | STAY_AWARE
}
```
Three columns on the user (or a tiny `user_traits` table). No JSONB needed — the shape is
fixed and small.

### 7.3 Trait → engine knob mapping (the whole point)
Each trait sets a parameter the engine already has; none is inert decoration.

| Trait | Option | Knob it sets |
|---|---|---|
| `goalPush` | RELAXED / STEADY / AGGRESSIVE | savings target as a fraction of `disposable` (Pivot A); **default for the project advisor**, replacing `ProjectBudgetAdvisorRequest.Mode` (URGENT/RELAXED) |
| `overspendHelp` | TELL_ME | analyzer emits, but **push gate = none**; insights are pull-only (in-app screen) |
| | NUDGE_ME | push gate = `WARN` → yes/no `Suggestion` (the §6 path) |
| | HARD_LIMIT | push gate = `CRITICAL`, immediate on budget cross; max intensity (not enforcement — passive tracker can't block) |
| `focus` | BUILD_SAVINGS | prioritize `SAVINGS_CAPACITY`, project-contribution nudges |
| | CUT_WASTE | prioritize `OVERSPEND_ANOMALY`, `CATEGORY_TREND`, `RECURRING_DETECTED` |
| | STAY_AWARE | low-frequency digest only; suppress action prompts |

### 7.4 Where it plugs in
- The **analyzer** (§3) takes `UserTraits` as input: `focus` weights/filters which insight
  types it surfaces; `goalPush` sets the savings-capacity target.
- The **producer** (§6.6) reads `overspendHelp` as the **default severity gate** — this is
  the cold-start prior. The Pivot C dismissal loop then adjusts the effective gate online.
  Same knob: trait = prior, dismissals = update.
- The **project advisor** reads `goalPush` instead of requiring a per-request `mode`.

## 8. Phase plan (ship the plumbing first)

1. **Phase 1 — prove the round trip.** Replace `UserFinancialProfile` with the 3-field
   `UserTraits` onboarding (§7) so the producer has a cold-start gate; analyzer with the
   z-score + budget-pace methods; `GenerateInsightsUseCase` + in-app insights screen
   (pull); `Suggestion` table with a **single** action type (`RAISE_BUDGET`); idempotent
   respond endpoint; one producer (transaction-create), gated by `overspendHelp`. Store
   decisions only.
2. **Phase 2 — adaptivity.** Add `dismiss_count` + a suppression rule once real dismissal
   data exists. Add more action types. Add the recurring-cost detector and wire it into
   Pivot A's `disposable` calculation. Add the digest producer.
3. **Phase 3 — polish.** Optional forecast (exponential smoothing); cooldown/daily cap if
   fatigue is observed; LLM narration layer over the structured insights.

## 9. Explicitly deferred

- Dismissal feedback/suppression loop (store now, act in Phase 2).
- Global throttle / daily cap (dedup index covers duplicates first).
- Push-delivery reliability (outbox/retries) — failed push still surfaces in-app.
- Multiple action types — one first, prove the plumbing, then expand.
- Persisted insight snapshots / feature store — compute-on-read until proactive
  notifications justify storage (which `Suggestion` already provides for the push path).
</content>
