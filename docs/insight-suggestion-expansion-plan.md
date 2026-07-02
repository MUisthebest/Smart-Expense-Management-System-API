# Insight & Suggestion System — Expansion Plan

> Status: IN PROGRESS. Ordered easiest → hardest by *total* effort
> (detection + action + trigger + plumbing + risk), not just detection difficulty.
> Author: analysis pass over the insight/suggestion pipeline (2026-07-02).
>
> **Progress:** ✅ **Wave 1 shipped** (items 1–3: `SET_CATEGORY_LIMIT`, `REDUCE_BUDGET` +
> `BUDGET_UNDERSPEND`, notification-only `LARGE_TRANSACTION` + `DUPLICATE_CHARGE`) — code,
> unit tests, i18n, FE guide, and Postman scenarios all in.
> ✅ **Wave 2 shipped** (items 4–5: `REVIEW_SUBSCRIPTION` + `SUBSCRIPTION_PRICE_HIKE`,
> `REBALANCE_BUDGETS` with the new multi-budget payload/executor and the budget-write trigger) —
> same deliverable bar; both open decisions resolved (see as-built notes). Waves 3–4 not started.

## Context — how the system works today

- **Insights (deterministic facts)** — `SpendingInsightAnalyzer` emits 6 types:
  `OVERSPEND_ANOMALY`, `BUDGET_PACE`, `CATEGORY_TREND`, `RECURRING`,
  `SAVINGS_CAPACITY`, `BUDGET_OVER_INCOME`.
- **Suggestions (yes/no actions)** — only 3: `RAISE_BUDGET`, `CREATE_BUDGET`,
  `CONTRIBUTE_TO_PROJECT`.
- **Events (triggers)** — effectively one: `CreateTransactionUseCase`
  (EXPENSE → `maybeProduce`, INCOME → `maybeProduceSavingsContribution`).
  Plus a weekly digest scheduler, disabled by default.
- **Traits** gate everything: `Intervention_Level` (push gate), `Focus_Mode`
  (ordering), `Saving_Pace` (contribution sizing).

## The three gaps this plan closes

1. **Insight → suggestion coverage is lopsided.** `CATEGORY_TREND`, `RECURRING`,
   and `BUDGET_OVER_INCOME` are computed but never produce a suggestion.
2. **The system can only ratchet spending up.** No `REDUCE_BUDGET`, no underspend
   detection, no subscription review — odd for a `SAVE_MORE`/`REDUCE_SPENDING` product.
3. **One trigger = a narrow, reactive worldview.** Everything keys off logging a
   transaction. Nothing fires at month boundaries, on budget/project edits, or when
   a saving project drifts past its deadline. Zero positive reinforcement.

---

## Shared groundwork (touches every item — understand once, up front)

Every new type flows through five seams:

1. **`InsightType` enum** — adding a value **breaks compilation** in
   `GenerateInsightsUseCase.focusRank()` (two exhaustive switches: REDUCE_SPENDING
   and SAVE_MORE arms). The compiler forces you to place every new insight in the
   focus ordering.
2. **`SuggestionType` enum** — adding a value **breaks compilation** in
   `RespondToSuggestionUseCase.executeAction()` (exhaustive switch, no default).
   No suggestion can ship without a wired executor.
3. **`SuggestionProducer`** — a `Candidate` builder + wiring into a trigger.
   `persistAndPush()` already provides dedup, dismissal-suppression, cooldown,
   daily-cap, and CRITICAL-bypass. New candidates need a unique `dedupKey`
   (`TYPE:scope:period`).
4. **`SuggestionPayload.ProposedAction`** — fields today:
   `category, budgetId, month, year, projectId, resolvedValue`. Some new actions
   need new fields here.
5. **i18n keys + docs** — `notification.suggestion.*`, `insight.*` keys, plus the
   Postman collection and the FE guide under `docs/frontend/`.

Per-item test targets: `SpendingInsightAnalyzerTest`, `SuggestionProducerTest`,
`RespondToSuggestionUseCaseTest`, `GenerateInsightsUseCaseTest`.

---

## Wave 1 — Pure additions (existing insight *or* executor, existing trigger) — ✅ SHIPPED

> All three items implemented. New `SuggestionType`s `SET_CATEGORY_LIMIT`, `REDUCE_BUDGET`; new
> `InsightType`s `BUDGET_UNDERSPEND`, `LARGE_TRANSACTION`, `DUPLICATE_CHARGE`. Tests:
> `SpendingInsightAnalyzerTest`, `SuggestionProducerTest`, `RespondToSuggestionUseCaseTest` extended;
> full suite green. Postman folders 7–10; i18n + FE guide updated.

### 1. `SET_CATEGORY_LIMIT` — easiest, do first — ✅ DONE
- **Insight:** `CATEGORY_TREND` — *already emitted*, currently dead-ends. No analyzer change.
- **Suggestion (new):** `SET_CATEGORY_LIMIT`. Fires when a category trends up **and has
  no budget**. Accept = create a budget at the resolved limit → **reuse existing
  `createBudget()` executor** (`category`, `month`, `year`, `resolvedValue` already exist).
- **Trigger:** existing expense-create path in `SuggestionProducer.maybeProduce()`.
  Add a third candidate branch keyed off `CATEGORY_TREND`.
- **Resolved value:** `trailing3MonthMedian` (public helper) + `capByIncome`.
- **Effort:** enum + 1 candidate builder + 1 executeAction arm (delegates to createBudget)
  + focusRank arms + i18n + tests. No new payload fields/executor/trigger.
- **Gotcha:** precedence vs `CREATE_BUDGET` (anomaly) when both hit the same budgetless
  category in one create — anomaly should win (already overspending).

### 2. `REDUCE_BUDGET` — closes the "up-only ratchet" — ✅ DONE
- **Insight (new):** `BUDGET_UNDERSPEND`. Inverse of `BUDGET_PACE`: for a budgeted
  category, if trailing-median actual sits consistently ≤ ~60% of limit and pace confirms,
  emit INFO. Reuses `trailing3MonthMedian` + monthly-totals helper.
- **Suggestion (new):** `REDUCE_BUDGET`. Accept = **reuse existing `updateBudget()`
  executor** (`budgetId` + `resolvedValue` exist). Resolved value = median actual, banded.
- **Trigger:** existing expense-create path. SAVE_MORE users benefit most.
- **Effort:** new insight + new suggestion + candidate builder + executeAction arm
  (reuses updateBudget) + 2 focusRank arms + tests.
- **Gotcha:** don't oscillate raise/reduce month to month — only fire underspend when
  pace is well below limit; fold into the fatigue story.

### 3. `LARGE_TRANSACTION` + `DUPLICATE_CHARGE` — notification-only, synchronous — ✅ DONE
- **Insights (new ×2):** `LARGE_TRANSACTION` (single expense is an outlier vs that
  category's typical per-transaction size) and `DUPLICATE_CHARGE` (same
  category+amount+normalized-description within a short window — reuse recurring
  normalization).
- **No suggestion / no accept action** — informational pushes, skip the
  `Suggestion`/`RespondTo` machinery. That's what makes them cheap.
- **Trigger:** synchronous, inside `CreateTransactionUseCase` after save (want immediacy).
  Gate by `Intervention_Level`, route through `CreateNotificationUseCase`.
- **Effort:** 2 detection methods + 2 focusRank arms + small synchronous check + i18n.
- **Gotcha:** `DUPLICATE_CHARGE` false positives — require exact amount + tight window,
  severity INFO.

---

## Wave 2 — New action *shape* or new insight semantics — ✅ SHIPPED

> Both items implemented (5 first, per sequencing). New `SuggestionType`s `REBALANCE_BUDGETS`,
> `REVIEW_SUBSCRIPTION`; new `InsightType` `SUBSCRIPTION_PRICE_HIKE`. Decisions resolved: item 4 =
> **(b) acknowledge-only**; item 5 = **honor the snapshot**. As-built notes below.

### 4. `REVIEW_SUBSCRIPTION` (+ `SUBSCRIPTION_PRICE_HIKE`) — ✅ DONE
- **Insight:** extend `RECURRING` to also emit `SUBSCRIPTION_PRICE_HIKE` when the latest
  month's amount breaks the recurring group's upward tolerance band (`allWithinTolerance`
  already computes it).
- **Suggestion (new):** `REVIEW_SUBSCRIPTION`. **Design wrinkle:** no "cancel" action
  exists. Pick one:
  - (a) Accept = create a capping budget for the category (reuse `createBudget`), or
  - (b) Acknowledge-only (like Wave 3 positives) — no state change, marks reviewed.
- **Trigger:** expense-create path (when the recurring charge posts).
- **Effort:** medium — mostly the product decision on what "yes" does.

### 5. `REBALANCE_BUDGETS` — first multi-entity action — ✅ DONE
- **Insight:** `BUDGET_OVER_INCOME` — *already emitted*. No analyzer change.
- **Suggestion (new):** `REBALANCE_BUDGETS`. Accept scales all of the month's budget
  limits proportionally to fit declared income.
- **New payload shape:** add `List<BudgetAdjustment>` (`{budgetId → newLimit}`) to
  `ProposedAction` (nullable, only this type uses it — payload is documented as
  polymorphic per type).
- **New executor:** executeAction arm looping adjustments through `updateBudget` in the
  existing transaction; validate each budget belongs to the user.
- **Trigger:** best on a **budget create/update event** (new trigger in
  `CreateBudgetUseCase`/`UpdateBudgetUseCase` → `maybeProduceRebalance`) so over-commitment
  shows while editing. Fallback: income-create path.
- **Effort:** medium-hard — new payload field + multi-budget executor + new trigger.
- **Gotcha:** snapshot resolved per-budget limits at *ask* time (consent is to concrete
  numbers). If budgets change before answering, honor the snapshot (recommended) or no-op.

---

## Wave 3 — New time-based triggers (scheduler infrastructure)

### 6. `INCOME_DROP` / `CASHFLOW_NEGATIVE` + month-rollover scheduler
- **Insights (new):** `INCOME_DROP` (z-score on monthly INCOME totals — mirror of
  `detectOverspendAnomalies` on the income series; `averageMonthlyIncome` partially
  assembles it) and `CASHFLOW_NEGATIVE` (month expenses > income).
- **Why coupled to a scheduler:** mid-month cashflow-negative is normal; meaningful only
  at month close → new month-rollover trigger.
- **New infra:** scheduler modeled on `WeeklyInsightDigestScheduler` (fan-out, per-user
  try/catch, `@ConditionalOnProperty` disabled-by-default) running a new
  `SendMonthlyRecapUseCase` (reuse `GenerateInsightsUseCase` for data, like the digest).
- **Effort:** new scheduler + recap use case + 2 insights + 2×2 focusRank arms.
- **Gotcha:** server-timezone month boundaries; the digest's `findAllForDigest` native-enum
  query is noted "unproven end-to-end" — validate the fan-out against a live DB first.

### 7. Month-end sweep (day ~25 scheduler)
- **What:** decouple `CONTRIBUTE_TO_PROJECT` from income arrival — late-month nudge to
  sweep leftover budget headroom into a saving project.
- **Reuse:** `contributeCandidate` + `pickContributionTarget` + `scaleByPace` all exist;
  just trigger from a schedule instead of an income event.
- **Effort:** second scheduler (or a day-of-month branch in #6) + call existing savings
  producer. Low-medium once #6's scheduler exists.

---

## Wave 4 — Hardest (project lifecycle, positive reinforcement, non-consentable actions)

### 8. `PROJECT_OFF_TRACK` + `INCREASE_CONTRIBUTION`
- **Insight (new):** `PROJECT_OFF_TRACK` — `remaining ÷ months-to-deadline > observed
  contribution rate`. Needs project reads; the pure analyzer intentionally doesn't load
  projects, so this lives in the producer / a new project-aware service (like
  `detectBudgetOverIncome` is handled outside the analyzer).
- **Suggestion (new):** `INCREASE_CONTRIBUTION` — reuse contribute executor with a higher
  resolved amount.
- **Trigger:** project scheduler (deadline-approaching / stalled = no contribution in N
  weeks) — more new time-based infra.
- **Effort:** hard — project-rate math, contribution-history tracking, new trigger.

### 9. `PROJECT_MILESTONE` / `GOOD_MONTH` — positive reinforcement (new message *kind*)
- **What:** "hit 50% of your goal," "stayed under every budget this month." Retention.
- **Why hard despite simple detection:** no accept action, so it doesn't fit the
  `Suggestion` model — needs its own lightweight positive-notification path *and* its own
  "already congratulated at this milestone?" dedup state (reuse a `SuggestionTopicStat`-style
  marker).
- **Trigger:** contribution-made event (milestone) + month-rollover (good-month, rides #6).
- **Effort:** hard-ish — new category of message + distinct dedup semantics.

### 10. `CREATE_SAVING_PROJECT` — hardest, needs a UX decision
- **What:** persistent `SAVINGS_CAPACITY` with no active project → suggest creating one.
- **Why hardest:** a project needs a name, target, and deadline the system cannot invent.
  Breaks the one-tap "consent to a resolved number" model. Accept must open a **pre-filled
  create-project form** the user edits — new payload shape, different FE flow, and a
  decision on whether accept runs server-side at all.
- **Recommendation:** treat as a deep-link nudge (notification → project-create screen
  pre-seeded with the disposable amount), *not* an auto-executed yes/no. Design last.

---

## Suggested sequencing

~~Ship **1 → 2 → 3** first~~ ✅ **done** (pure additions, max reuse, close the up-only ratchet,
exercise both the suggestion path and the notification-only path with no new infra).
~~Then **5 (REBALANCE)** for the first payoff on `BUDGET_OVER_INCOME`, then 4~~ ✅ **done** (Wave 2
shipped 5-then-4). **Next up:** Wave 3 schedulers (month-rollover recap, day-25 sweep), then the
Wave 4 project/positive work.

### Wave 1 — as-built notes
- **`SET_CATEGORY_LIMIT`:** third branch in `SuggestionProducer.maybeProduce` (budgetless path), tried
  *after* `CREATE_BUDGET` so an anomaly still wins. Reuses the `createBudget` executor. `resolvedValue`
  = `trailing3MonthMedian` capped by income.
- **`BUDGET_UNDERSPEND` / `REDUCE_BUDGET`:** analyzer fires INFO only when *both* the trailing median
  and the current-month linear projection sit ≤ 60% of the limit (`UNDERSPEND_RATIO`), with the same
  `MIN_PACE_DAYS` early-month guard as `BUDGET_PACE` (no raise/reduce oscillation). Because it's INFO,
  the producer's reduce branch isn't severity-gated (mirrors the savings nudge). `resolvedValue` =
  `medianActual × 1.15` headroom, only asked when below the current limit. Reuses `updateBudget`.
- **`LARGE_TRANSACTION` / `DUPLICATE_CHARGE`:** two public analyzer detectors (per-charge z-score;
  exact category+amount+normalized-description within `DUPLICATE_WINDOW_HOURS` = 72h). Wired through
  `SuggestionProducer.maybeNotifyTransactionAnomalies`, called synchronously from
  `CreateTransactionUseCase` on expense create, gated by `Intervention_Level`, pushed via
  `CreateNotificationUseCase` with deep-link `app://transactions`. **Not** added to `analyze()`, so
  they never surface in `GET /insights` or as suggestion cards.

### Wave 2 — as-built notes

- **`REBALANCE_BUDGETS` (item 5, built first):**
  - **Payload:** `ProposedAction` gained a nullable `List<BudgetAdjustment>` (`budgetId`, `category`,
    `currentLimit`, `newLimit` — the last two for the FE's before→after render). `resolvedValue`
    carries the **new committed total** for display. Only this type uses the list.
  - **Math:** factor = `income ÷ committed` (scale 6, DOWN); each `newLimit` = `limit × factor`
    rounded **DOWN** to the cent (floored at 0.01), so the rebalanced total can never exceed income.
    Budgets whose floor makes the "reduction" a non-reduction are dropped from the snapshot.
  - **Executor:** new `rebalanceBudgets` arm validates the whole snapshot up front, then loops
    `UpdateBudgetUseCase.execute(..., rebalanceCheck=false)` inside the accept transaction —
    ownership is enforced per budget by the reused use case. **Snapshot is honored verbatim**
    (decision: consent is to concrete numbers); a budget deleted since the ask is skipped, never
    recomputed. `RespondToSuggestionUseCase` gained a `BudgetRepository` to make that skip explicit.
  - **Trigger:** `maybeProduceRebalance(userId, month, year)` runs after `CreateBudgetUseCase` saves
    and after `UpdateBudgetUseCase` saves a **limit** change. The 4-arg `execute(..., boolean
    rebalanceCheck)` overload exists solely so the rebalance accept loop doesn't re-check a
    half-applied state (the 3-arg form defaults to `true`, so RAISE/REDUCE accepts *do* re-check —
    a raise that over-commits the month legitimately asks to rebalance). The income-create fallback
    was **not** wired: `BUDGET_OVER_INCOME` compares against *declared* income (on the User), which
    an income transaction doesn't change.
  - **Gate:** the insight is WARN, so GENTLE users get the ask and HARD (CRITICAL-only) users don't —
    the same `atLeast(gate)` rule as the expense-path candidates. `dedupKey` is account-wide per
    month (`REBALANCE_BUDGETS:2026-07`).
- **`SUBSCRIPTION_PRICE_HIKE` / `REVIEW_SUBSCRIPTION` (item 4):**
  - **Detector:** the recurring grouping was extracted to a shared `recurringGroups()` helper; the
    new `detectSubscriptionPriceHikes` (in `analyze()`, so it *does* surface in `GET /insights`)
    fires WARN when ≥ `RECURRING_MIN_MONTHS` *prior* months sit within the ±15% tolerance of their
    median and the **current** month breaks the band **upward**. Metrics: `description`,
    `previousAmount` (prior median), `newAmount`, `increaseRatio`, `monthsObserved`. Such a group
    simultaneously stops qualifying as `RECURRING` (the hiked month fails the all-months tolerance),
    so the hike replaces it rather than duplicating it.
  - **Decision (b) — acknowledge-only:** accepting executes **nothing** (`case REVIEW_SUBSCRIPTION
    -> {}` in the whitelist); the payload has **no `proposedAction`** and the card renders from the
    insight snapshot. Rationale: a category budget can't stop a subscription charging, and creating
    one would immediately feed BUDGET_PACE noise.
  - **Producer:** `reviewSubscriptionCandidate` is the lowest-precedence branch in `maybeProduce`
    (any budget-related candidate wins). The `dedupKey` embeds a 40-char slug of the subscription's
    normalized description (`REVIEW_SUBSCRIPTION:ENTERTAINMENT:netflix-premium:2026-07`) so two
    subscriptions in one category are independent topics. Push key:
    `notification.suggestion.review_subscription|<description>`.
- **Docs/tests:** analyzer/producer/respond tests extended (full suite green: 293 tests). Postman
  folders 11–12 + README rows; FE guide gained the new insight row, both card rows (incl. the
  `budgetAdjustments` render contract), enum/i18n entries; `docs/frontend/i18n.en.reference.json`
  (previously a dangling reference) now exists with starter copy for every Appendix-B key,
  including the Wave 1 keys that were missing from Appendix B.

## Open decisions to resolve before building

- ~~**Item 4:** what does "yes" on `REVIEW_SUBSCRIPTION` execute?~~ **Resolved: (b) acknowledge-only**
  (user decision, 2026-07-02) — no state change; the flip to ACCEPTED is the entire effect.
- ~~**Item 5:** on stale budgets at accept time?~~ **Resolved: honor the snapshot** (user decision,
  2026-07-02) — apply the consented numbers verbatim; skip budgets deleted since the ask.
- **Item 10:** is `CREATE_SAVING_PROJECT` a deep-link nudge or a server-executed action?

## Quick-reference: new type inventory

| # | Insight (new/existing) | Suggestion (new) | Reuses executor | New trigger? | Status |
|---|---|---|---|---|---|
| 1 | CATEGORY_TREND (existing) | SET_CATEGORY_LIMIT | createBudget | no | ✅ done |
| 2 | BUDGET_UNDERSPEND (new) | REDUCE_BUDGET | updateBudget | no | ✅ done |
| 3 | LARGE_TRANSACTION, DUPLICATE_CHARGE (new) | — (notification only) | — | sync in create | ✅ done |
| 4 | SUBSCRIPTION_PRICE_HIKE (new, on RECURRING) | REVIEW_SUBSCRIPTION | none (acknowledge-only) | no | ✅ done |
| 5 | BUDGET_OVER_INCOME (existing) | REBALANCE_BUDGETS | updateBudget (×N) | budget write | ✅ done |
| 6 | INCOME_DROP, CASHFLOW_NEGATIVE (new) | — (recap) | — | month-rollover cron | — |
| 7 | SAVINGS_CAPACITY (existing) | CONTRIBUTE_TO_PROJECT (reused) | contribute | day-25 cron | — |
| 8 | PROJECT_OFF_TRACK (new) | INCREASE_CONTRIBUTION | contribute | project cron | — |
| 9 | PROJECT_MILESTONE, GOOD_MONTH (new) | — (positive push) | — | contribution + cron | — |
| 10 | SAVINGS_CAPACITY persistent (existing) | CREATE_SAVING_PROJECT | createProject / deep-link | no | — |
