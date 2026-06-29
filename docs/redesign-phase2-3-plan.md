# Redesign — Phase 2 & 3 Detailed Plan (adaptivity + polish)

> Phase 1 is complete (B ✅ D ✅ A ✅ C ✅). This plan covers everything deliberately deferred
> from `redesign-workstream-c-plan.md` and the Pivot A/D follow-ups. Same philosophy as Phase 1:
> **deterministic facts first, LLM only phrases**; deprecate-don't-delete; one migration per change.
>
> Source of truth: `redesign-what-and-why.md` (why), `redesign-technical-implementation.md` (how),
> `redesign-workstream-c-implementation.md` (what shipped in Phase 1 C).
>
> **Phase 2 = adaptivity** (the system learns and acts on more). **Phase 3 = polish** (forecast
> quality, fatigue tuning, LLM narration). **Next migration number = V22.**

## 0. Reuse map (Phase-1 anchors to build on)

| Need | Anchor shipped in Phase 1 |
|---|---|
| Pure insight engine | `domain/services/SpendingInsightAnalyzer` (`analyze`, `trailing3MonthMedian`) |
| Insight VO + enums | `domain/models/insight/{Insight, InsightType, Severity}` |
| Pull use case / endpoint | `GenerateInsightsUseCase`, `InsightController` `GET /api/v1/insights` |
| Suggestion aggregate | `domain/models/suggestion/{Suggestion, SuggestionType, SuggestionStatus}` |
| Suggestion persistence | `SuggestionEntity`/`SuggestionMapper`/`SuggestionRepository(+Impl)`, table `suggestion` (V21) |
| Payload shape | `application/usecases/suggestion/SuggestionPayload` (`insightSnapshot` + `proposedAction`) |
| Idempotent respond | `RespondToSuggestionUseCase` (`markDecidedIfPending` conditional UPDATE + switch-on-type) |
| Producer | `application/usecases/insight/SuggestionProducer` (gated by `interventionLevel`) |
| Push | `CreateNotificationUseCase.createNotification(userId, content, deepLink)` |
| Traits | `User.getFocusMode()/getInterventionLevel()/getSavingPace()` (D1) |

**Golden rules carried forward:** analyzer stays pure (pass `asOf` in, no clock/IO); new
`SuggestionType`/`InsightType` values are code-only (varchar columns, no CHECK); the producer is
best-effort and must never break transaction-create; every `switch` on suggestion type is a
whitelist.

---

# PHASE 1.5 — Verification & hardening (do FIRST, blocks everything)

These are the known gaps from `redesign-workstream-c-implementation.md` §"Verification status".

- **V1 — Live boot against a dev DB.** Confirm `V21` applies and `SuggestionEntity` passes
  `ddl-auto: validate`. `SmartmoneyApplicationTests` is currently skipped (no DB); either provision
  a test Postgres (Testcontainers) or run the app once manually. *Until this passes, the suggestion
  path is unproven end-to-end.*
- **V2 — FE contract confirmation (plan open decision #4).** Confirm the deep-link scheme
  (`app://suggestions/{id}` vs an Expo `data` object — check `ExpoPushNotificationSender`) and add
  i18n entries for `notification.suggestion.raise_budget|<CATEGORY>` and every `insight.*` code the
  analyzer emits (`insight.overspend_anomaly.*`, `insight.budget_pace.*`).
- **V3 — Persistence test for the dedup index.** A slice/integration test proving a second PENDING
  insert for the same `(user_id, dedup_key)` is rejected, and that a decided row frees the slot.
  (Phase 1 covered the producer pre-check with mocks but not the DB constraint itself.)

---

# PHASE 2 — Adaptivity

## P2-A — Dismissal-suppression learning loop

**Goal:** stop re-asking what the user keeps saying no to (the #1 long-term fatigue risk).

- **Schema `V22`:** add `dismiss_count integer NOT NULL DEFAULT 0` to `suggestion` (or a small
  `suggestion_topic_stats(user_id, dedup_key, dismiss_count, last_dismissed_at)` table keyed by the
  dedup topic — preferred, because a `suggestion` row is per-instance and dismissals accumulate
  across instances of the same topic). Recommend the **topic-stats table**.
- **On dismiss** (`RespondToSuggestionUseCase`, the `!accept` branch): increment the topic's
  `dismiss_count`. Keep it in the same `@Transactional` as the status flip.
- **In the producer** (`SuggestionProducer`): before insert, read the topic's `dismiss_count`;
  apply a suppression policy — e.g. `dismiss_count >= 2` → suppress unless severity escalated to
  `CRITICAL`; back off with an exponential cooldown (see P2-D). This is the first real
  "learning" — store the signal in Phase 2, act on it gradually.
- **Tests:** dismiss increments the topic counter; producer suppresses a topic past the threshold;
  a `CRITICAL` escalation still breaks through.

## P2-B — More action types

Add to `SuggestionType` (code-only) + a new whitelist branch in `RespondToSuggestionUseCase.executeAction`.

- **`CREATE_BUDGET`** — when `OVERSPEND_ANOMALY` fires for a category with **no** budget. Producer
  builds `proposedAction = { category, resolvedValue = trailing3MonthMedian }` (no `budgetId` yet).
  Accept → call a `CreateBudgetUseCase` for that category/month with `amountLimit = resolvedValue`.
  This finally gives `OVERSPEND_ANOMALY` an action target (Phase 1 only acted on `BUDGET_PACE`).
- **`CONTRIBUTE_TO_PROJECT`** — when a `SAVINGS_CAPACITY` insight (P2-C) shows headroom, suggest
  contributing to an active saving project. Accept → `AddProjectContributionUseCase.execute(...)`
  (already exists from Pivot B). Amount scaled by `savingPace` (P2-E).
- **Gotcha:** `proposedAction` is now polymorphic across types. Keep storing the **resolved value**,
  never a recipe; validate required fields per type in the accept branch (mirror the Phase-1
  RAISE_BUDGET null-guard).
- **Tests:** each new type — producer builds the right payload; accept executes the right use case;
  unknown/missing fields rejected.

## P2-C — More insight types (analyzer extensions, still pure)

All in `SpendingInsightAnalyzer`; add to `InsightType`; keep `asOf`-driven & unit-tested.

- **`CATEGORY_TREND`** — slope of monthly totals over the trailing window (simple linear
  regression or month-over-month %). Surfaces "X is creeping up" before it's an anomaly.
- **`RECURRING`** — detect repeated near-equal charges in a category/description across consecutive
  months (the recurring-cost detector). Feeds P2-D's `disposable` calc and a future "is this
  subscription still worth it?" nudge.
- **`SAVINGS_CAPACITY`** — `disposable = netIncome − recurringCosts − committedBudgets`; positive
  capacity → a `CONTRIBUTE_TO_PROJECT` suggestion (P2-B). Requires net-income input (P2-D).
- **Tests:** trend up/down/flat; recurring detected vs one-off ignored; capacity positive/negative
  edges. This is the next chunk of "defensible core" for the thesis.

## P2-D — Recurring-cost detector → Pivot A `disposable` (net-income-only)

**Goal:** finish Pivot A — replace any remaining declared-obligation thinking with **learned**
recurring costs, yielding a `disposable` figure used by `SAVINGS_CAPACITY`.

- Consume P2-C's `RECURRING` insights to estimate monthly committed spend.
- Net income comes from the income ledger (`TransactionRepository.getTotalIncomeBetween`) or the
  existing income flags (`incomeSetupCompleted`); **do not** resurrect `usableIncome`/`safeSpending`
  (Workstream A gutted those — they remain vestigial, untouched).
- The 20% project-saving rule stays **advisory** (already true post-D3 in `ProjectBudgetAdvisorUseCase`).
- **Tests:** disposable = income − recurring − budgets across a few fixtures; cold-start (no
  recurring history) degrades gracefully to a conservative capacity.

## P2-E — `savingPace` (goalPush) scales savings amounts

`User.getSavingPace()` (`RELAXED | BALANCED | AGGRESIVE`) already drives `ProjectBudgetAdvisorUseCase`
(D3). Extend it to scale the **suggested contribution amount** in `CONTRIBUTE_TO_PROJECT`
(P2-B): e.g. AGGRESIVE = larger share of `disposable`, RELAXED = smaller. Pure multiplier in the
producer; unit-test the three tiers + cold-start default `BALANCED`.

## P2-F — Scheduled digest producer (for SAVE_MODE / TRACK_ONLY)

`focusMode` `SAVE_MODE`/`TRACK_ONLY` users want fewer interruptions. Add a scheduled job
(`@Scheduled`, gated by `@ConditionalOnProperty` like `MonthlySettlementScheduler`) that, on a
cadence (weekly?), batches that period's `>= WARN` insights into a single digest notification
instead of per-event pushes. Reuses `GenerateInsightsUseCase` for the data. Default the flag **off**
until validated.

---

# PHASE 3 — Polish

## P3-A — Cooldown / daily cap (global fatigue ceiling)

On top of dedup (P1) + dismissal suppression (P2-A): a per-user **daily push cap** and a
**per-topic cooldown** (min time between asks on the same topic). Store last-push timestamps
(reuse the P2-A topic-stats table). Enforce in the producer before insert. Tunable via
`application.yml` (`app.suggestion.daily-cap`, `app.suggestion.cooldown-hours`).

## P3-B — Exponential-smoothing forecast

Replace/augment the linear `BUDGET_PACE` projection with exponential smoothing (or
seasonally-naive) for a smoother end-of-month estimate that reacts to recent days without
overshooting on a single spike. Keep the linear version behind a flag for comparison; the analyzer
stays pure and `asOf`-driven. Unit-test against the same fixtures as the linear method.

## P3-C — LLM narration layer (the only place the LLM enters)

**Goal:** phrase the already-computed insights/suggestions in natural language; the LLM **never**
computes a number.

- New `LlmPort`-based narrator that takes an `Insight`/`Suggestion` (structured `metrics` + `code`)
  and returns a localized sentence. Gemini key already configured (`app.gemini.api-key`).
- Wire optionally into `InsightResponse` (a `narrative` field) and the push content — **best-effort**:
  if the LLM is down, fall back to the `code` (Phase-1 behavior). Never on the critical path.
- **Guardrail tests:** narrator is bypassed when the port fails; the numbers in the prose match the
  `metrics` (assert the LLM didn't invent figures — feed fixed metrics, check the template path).

## P3-D — Trait onboarding endpoint (D2, skipped in Phase 1)

Add the cold-start onboarding endpoint that sets `UserTraits {savingPace, interventionLevel,
focusMode}` and sensible defaults (`BALANCED` / `NOTIFY` / `TRACK_ONLY` — least-spammy until the
user opts in). Until then `interventionLevel == null` already means "no push" (producer treats
null as NOTIFY), so this is polish, not a blocker.

---

## Build order (dependencies)

```
Phase 1.5 (V1 boot+validate, V2 FE/i18n, V3 dedup persistence test)   ← unblockers, do first
   └─ P2-C insight types (CATEGORY_TREND, RECURRING, SAVINGS_CAPACITY) ← pure, feeds everything
       ├─ P2-D disposable / net-income (needs RECURRING)
       │    └─ P2-E savingPace scaling
       ├─ P2-B action types (CREATE_BUDGET needs nothing; CONTRIBUTE needs SAVINGS_CAPACITY)
       └─ P2-A dismissal suppression (independent; needs V22 topic-stats table)
            └─ P3-A cooldown / daily cap (reuses topic-stats)
P3-B forecast (independent analyzer swap)
P3-C LLM narration (independent; last — pure-data path must be solid first)
P3-D onboarding endpoint (independent polish)
```

## Test plan (carry the Phase-1 discipline)

- **Pure unit (the defensible core):** every new analyzer rule in `SpendingInsightAnalyzerTest`
  style — deterministic `asOf`, explicit fixtures, edge guards (cold start, zero variance, single
  outlier, day-1).
- **Use-case unit (mocked repos):** new action types execute the right use case once; dismissal
  increments + suppresses; cooldown/cap blocks; digest batches.
- **Persistence:** V22 column/table validates; dedup + cooldown constraints actually enforced.
- **LLM:** fallback-on-failure and no-fabricated-numbers guardrails.
- Mirror the existing **active** mock-based style; keep the project's `@Disabled` use-case tests
  out of scope.

## Risks

- **Fatigue is still #1.** Layer the controls: dedup (done) → dismissal suppression (P2-A) →
  cooldown/cap (P3-A) → digest for low-intervention users (P2-F). Ship them in that order.
- **Polymorphic payload drift** (P2-B): more action types = more `proposedAction` shapes. Keep
  per-type validation strict and the accept-time `switch` a closed whitelist.
- **LLM creep** (P3-C): resist letting the LLM compute. It phrases; it never decides the number or
  the action. Keep it off the critical path and behind a fallback.
- **Recurring-detector false positives** (P2-C/D): a misdetected "subscription" corrupts
  `disposable`. Require multiple consecutive months + amount tolerance; unit-test the boundary.

## Open decisions for the implementing session

1. **Dismissal storage:** `dismiss_count` column on `suggestion` vs a `suggestion_topic_stats`
   table keyed by `(user_id, dedup_key)`. (Recommend the table — dismissals accrue across instances.)
2. **Recurring detection window/tolerance:** how many consecutive months and what amount delta
   counts as "recurring"? (Suggest ≥3 months, ±10–15%.)
3. **Digest cadence + channel:** weekly push vs in-app-only digest for `SAVE_MODE`/`TRACK_ONLY`.
4. **Forecast method (P3-B):** exponential smoothing α, or seasonally-naive; keep linear as the
   control behind a flag.
5. **LLM provider/runtime (P3-C):** confirm Gemini vs OpenRouter (`app.openrouter.api-key` also
   configured) and per-locale prompt templates.
