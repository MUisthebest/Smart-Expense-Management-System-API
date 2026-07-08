# Insight & Suggestion System — Expansion Plan

> Status: ✅ **COMPLETE** — all four waves shipped. Ordered easiest → hardest by *total* effort
> (detection + action + trigger + plumbing + risk), not just detection difficulty.
> Author: analysis pass over the insight/suggestion pipeline (2026-07-02).
>
> **Progress:** ✅ **Wave 1 shipped** (items 1–3: `SET_CATEGORY_LIMIT`, `REDUCE_BUDGET` +
> `BUDGET_UNDERSPEND`, notification-only `LARGE_TRANSACTION` + `DUPLICATE_CHARGE`) — code,
> unit tests, i18n, FE guide, and Postman scenarios all in.
> ✅ **Wave 2 shipped** (items 4–5: `REVIEW_SUBSCRIPTION` + `SUBSCRIPTION_PRICE_HIKE`,
> `REBALANCE_BUDGETS` with the new multi-budget payload/executor and the budget-write trigger) —
> same deliverable bar; both open decisions resolved (see as-built notes).
> ✅ **Wave 3 shipped** (items 6–7: `INCOME_DROP` + `CASHFLOW_NEGATIVE` recap on a month-rollover
> scheduler, day-25 savings sweep, plus property-gated on-demand demo triggers) — schedulers off by
> default like the digest.
> ✅ **Wave 4 shipped** (items 8–10: `PROJECT_OFF_TRACK` + `INCREASE_CONTRIBUTION` on a weekly
> project-health scheduler, `PROJECT_MILESTONE` + `GOOD_MONTH` positive reinforcement, and the
> `CREATE_SAVING_PROJECT` deep-link nudge) — item 10 resolved as a nudge, not a suggestion (see
> as-built notes).

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

## Wave 3 — New time-based triggers (scheduler infrastructure) — ✅ SHIPPED

> Both items implemented. New notification-only `InsightType`s `INCOME_DROP`, `CASHFLOW_NEGATIVE`;
> two schedulers (`MonthlyRecapScheduler`, `MonthEndSweepScheduler`), both disabled by default with
> property-overridable crons; `SendMonthlyRecapUseCase` takes an explicit `asOf` for deterministic
> tests/demos; property-gated demo endpoints power Postman folders 13–14. As-built notes below.

### 6. `INCOME_DROP` / `CASHFLOW_NEGATIVE` + month-rollover scheduler — ✅ DONE
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

### 7. Month-end sweep (day ~25 scheduler) — ✅ DONE
- **What:** decouple `CONTRIBUTE_TO_PROJECT` from income arrival — late-month nudge to
  sweep leftover budget headroom into a saving project.
- **Reuse:** `contributeCandidate` + `pickContributionTarget` + `scaleByPace` all exist;
  just trigger from a schedule instead of an income event.
- **Effort:** second scheduler (or a day-of-month branch in #6) + call existing savings
  producer. Low-medium once #6's scheduler exists.

---

## Wave 4 — Hardest (project lifecycle, positive reinforcement, non-consentable actions) — ✅ SHIPPED

> All three items implemented. New `InsightType`s `PROJECT_OFF_TRACK`, `PROJECT_MILESTONE`,
> `GOOD_MONTH`; new `SuggestionType` `INCREASE_CONTRIBUTION`; item 10 deliberately adds **no**
> enum values (resolved as a deep-link nudge). New `ProjectHealthScheduler` (weekly, off by
> default) + `PositiveReinforcementUseCase`; demo endpoint `POST /demo/adaptive/project-health`.
> As-built notes below.

### 8. `PROJECT_OFF_TRACK` + `INCREASE_CONTRIBUTION` — ✅ DONE
- **Insight (new):** `PROJECT_OFF_TRACK` — `remaining ÷ months-to-deadline > observed
  contribution rate`. Needs project reads; the pure analyzer intentionally doesn't load
  projects, so this lives in the producer / a new project-aware service (like
  `detectBudgetOverIncome` is handled outside the analyzer).
- **Suggestion (new):** `INCREASE_CONTRIBUTION` — reuse contribute executor with a higher
  resolved amount.
- **Trigger:** project scheduler (deadline-approaching / stalled = no contribution in N
  weeks) — more new time-based infra.
- **Effort:** hard — project-rate math, contribution-history tracking, new trigger.

### 9. `PROJECT_MILESTONE` / `GOOD_MONTH` — positive reinforcement (new message *kind*) — ✅ DONE
- **What:** "hit 50% of your goal," "stayed under every budget this month." Retention.
- **Why hard despite simple detection:** no accept action, so it doesn't fit the
  `Suggestion` model — needs its own lightweight positive-notification path *and* its own
  "already congratulated at this milestone?" dedup state (reuse a `SuggestionTopicStat`-style
  marker).
- **Trigger:** contribution-made event (milestone) + month-rollover (good-month, rides #6).
- **Effort:** hard-ish — new category of message + distinct dedup semantics.

### 10. `CREATE_SAVING_PROJECT` — hardest, needs a UX decision — ✅ DONE (as a nudge)
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
shipped 5-then-4). ~~Then Wave 3 schedulers~~ ✅ **done** (month-rollover recap + day-25 sweep, both
flag-gated off). ~~Then the Wave 4 project/positive work~~ ✅ **done** — item 8 rode the Wave 3
scheduler pattern as predicted; item 9's GOOD_MONTH rode the recap; item 10 shipped as a
deep-link nudge. **The plan is complete.**

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

### Wave 3 — as-built notes

- **Detectors (item 6):** `detectIncomeDrop` / `detectCashflowNegative` are public analyzer methods
  taking an explicit `YearMonth` — **not** part of `analyze()` (mid-month both facts would be
  misleading), mirroring how `detectBudgetOverIncome` sits outside it. Income drop is the
  overspend-anomaly z-score on the income series, low tail, with a flat-baseline fallback (fixed
  salary → stddev 0): fire when ≤50% of the usual income arrived, CRITICAL when none did — the
  fallback matters because the vanished-salary case *is* the flat case. Cashflow-negative requires
  an income signal in the month (a zero-income month is the drop's territory) and escalates to
  CRITICAL at ≥1.25× income, the budget-pace ratio. Both category-null, notification-only, never in
  `GET /insights`.
- **Recap (item 6):** `SendMonthlyRecapUseCase.sendFor(userId, asOf)` judges the month *before*
  `asOf`, severity-gated per `interventionLevel` exactly like pushed suggestions (NOTIFY → nothing,
  and the gate is checked before any data is loaded). One push per finding, narrated when narration
  is on, fallback keys `notification.insight.income_drop` / `.cashflow_negative`, deep link
  `app://transactions`. `MonthlyRecapScheduler` (digest-pattern fan-out over `findAllForDigest`,
  per-user try/catch) runs it; `@ConditionalOnProperty app.recap.enabled` (default **off**), cron
  `${app.recap.cron:0 0 9 1 * *}` — property-overridable so a demo profile can fire every minute.
- **Sweep (item 7):** deliberately **zero new logic** — `MonthEndSweepScheduler` fans out to the
  existing `maybeProduceSavingsContribution`, so the trait gate, capacity math, pace scaling,
  dedup (per-month topic key), cooldown, and daily cap all apply unchanged. `app.sweep.enabled`
  (default off), cron `${app.sweep.cron:0 0 9 25 * *}`. Took the "second scheduler" option over a
  day-of-month branch in the recap — the two flows gate and fire independently.
- **Demo triggers:** `AdaptiveEngineDemoController` (`POST /api/v1/demo/adaptive/recap|sweep?asOf=…`)
  runs the identical use-case paths for the authenticated user only; registered solely when
  `app.demo-triggers.enabled=true`. This is the testing strategy discussed pre-build: schedulers
  stay thin, date-parameterized use cases carry the logic, demos never wait for a clock.
- **Tests/docs:** analyzer recap-fact cases, `SendMonthlyRecapUseCaseTest` (gating incl.
  HARD-sees-only-CRITICAL), fan-out don't-abort tests for both schedulers (full suite green: 309).
  Postman folders 13–14 need **no new seeds** — they judge the world folders 0–12 already built
  (July closed over-spent; August has no income vs a flat 20M baseline). FE guide + i18n reference
  updated; `application.yml` documents all three new flags.

### Wave 4 — as-built notes

- **`PROJECT_OFF_TRACK` / `INCREASE_CONTRIBUTION` (item 8):**
  - **Detection:** lives in `SuggestionProducer` (`offTrackInsight`) as pure math over the loaded
    `Project` aggregate — the analyzer stays project-free, as planned. Judged only for live,
    fundable, non-sub-personal projects **at least one month old**. Required rate = `remaining ÷
    monthsLeft` (deadline month and current partial month both count — money can still move in
    both); observed rate = contributions over the trailing **closed** months (up to
    `OFF_TRACK_OBSERVE_MONTHS = 3`, fewer for younger projects) ÷ that window — the current partial
    month is excluded because it isn't over yet. Off-track = required > observed. WARN by default;
    **CRITICAL when stalled** (zero contributed in the whole window — this covers the plan's
    "stalled = no contribution in N weeks" without a second concept) **or the shortfall is ≥2×**,
    so HARD users only hear about projects in real trouble.
  - **Suggestion:** `INCREASE_CONTRIBUTION` reuses the contribute executor verbatim
    (`case CONTRIBUTE_TO_PROJECT, INCREASE_CONTRIBUTION -> contributeToProject(...)`).
    `resolvedValue` = the required monthly rate capped at `remaining`. `dedupKey`
    `INCREASE_CONTRIBUTION:<projectId>:<yearMonth>` — one ask per project per month, several
    off-track projects each get their own topic (the daily cap arbitrates flooding).
  - **Trigger:** `ProjectHealthScheduler`, **weekly** (default 09:00 Monday) rather than monthly so
    a stall is caught within days, while the monthly dedup key + topic cooldown keep the ask itself
    at most monthly. `app.project-health.enabled` (default **off**), cron overridable via
    `app.project-health.cron`. Demo endpoint: `POST /api/v1/demo/adaptive/project-health?asOf=…`.
- **`PROJECT_MILESTONE` / `GOOD_MONTH` (item 9):** new `PositiveReinforcementUseCase` — the
  engine's first celebratory messages. Notification-only, INFO by nature, so the gate is
  **presence-of-push** (GENTLE *and* HARD celebrate; NOTIFY stays pull-only — user decision
  2026-07-03, matching the savings-nudge precedent: the severity gate is about warning fatigue and
  these aren't warnings). As predicted, they don't fit the `Suggestion` dedup model, so
  "already congratulated?" is a `SuggestionTopicStat` **marker row** whose presence is the answer:
  `MILESTONE:<projectId>:<pct>` / `GOOD_MONTH:<yearMonth>`.
  - **Milestones** (25/50/75/100%): checked synchronously in `AddProjectContributionUseCase` with
    the progress percent captured before/after the contribution; a jump over several thresholds
    celebrates only the highest. 100% doubles as the completion cheer. Deep link
    `app://projects/<id>`. Covers both the press-to-contribute button and suggestion accepts (both
    run through the same use case).
  - **GOOD_MONTH** rides the recap: only evaluated when the closed month produced **no negative
    finding at all** (found, not merely gated off — a bad month stays bad for HARD users too, no
    accidental sarcasm), then requires ≥1 budget and every budget's `spent ≤ limit`. Deep link
    `app://budgets`.
- **`CREATE_SAVING_PROJECT` (item 10) — resolved: deep-link nudge.** Deliberately **no new enum
  values**: when the savings producer finds capacity but `pickContributionTarget` comes up empty
  (exactly where the contribute ask used to dead-end), it pushes
  `notification.nudge.create_saving_project` with deep link
  `app://projects/create?amount=<pace-scaled seed>` — the FE opens the create form pre-filled; the
  user supplies the name/target/deadline only they can choose. At most once per month via a
  `CREATE_SAVING_PROJECT:<yearMonth>` marker. Fires from both the income-create path and the
  day-25 sweep (same code path), so users who never log income still get it. The `notification.nudge.*`
  namespace is new: a call-to-action push with no suggestion card and no accept step.
- **Docs/tests:** producer off-track cases (behind/on-pace/stalled-CRITICAL/young/NOTIFY), nudge
  once-per-month cases, `PositiveReinforcementUseCaseTest`, recap good-month integration,
  executor + scheduler fan-out tests (full suite green: 330 tests). Postman folders 15–17 +
  README rows; FE guide + `i18n.en.reference.json` extended (incl. the new nudge group);
  `application.yml` documents `app.project-health.*`.

## Open decisions to resolve before building

- ~~**Item 4:** what does "yes" on `REVIEW_SUBSCRIPTION` execute?~~ **Resolved: (b) acknowledge-only**
  (user decision, 2026-07-02) — no state change; the flip to ACCEPTED is the entire effect.
- ~~**Item 5:** on stale budgets at accept time?~~ **Resolved: honor the snapshot** (user decision,
  2026-07-02) — apply the consented numbers verbatim; skip budgets deleted since the ask.
- ~~**Item 10:** is `CREATE_SAVING_PROJECT` a deep-link nudge or a server-executed action?~~
  **Resolved: deep-link nudge** (user decision, 2026-07-03) — notification-only, no suggestion
  row, no enum value; the deep link carries the pre-fill seed amount. A project needs a name,
  target and deadline only the user can choose, so there is nothing a server-side "yes" could
  honestly execute.

## Quick-reference: new type inventory

| # | Insight (new/existing) | Suggestion (new) | Reuses executor | New trigger? | Status |
|---|---|---|---|---|---|
| 1 | CATEGORY_TREND (existing) | SET_CATEGORY_LIMIT | createBudget | no | ✅ done |
| 2 | BUDGET_UNDERSPEND (new) | REDUCE_BUDGET | updateBudget | no | ✅ done |
| 3 | LARGE_TRANSACTION, DUPLICATE_CHARGE (new) | — (notification only) | — | sync in create | ✅ done |
| 4 | SUBSCRIPTION_PRICE_HIKE (new, on RECURRING) | REVIEW_SUBSCRIPTION | none (acknowledge-only) | no | ✅ done |
| 5 | BUDGET_OVER_INCOME (existing) | REBALANCE_BUDGETS | updateBudget (×N) | budget write | ✅ done |
| 6 | INCOME_DROP, CASHFLOW_NEGATIVE (new) | — (recap) | — | month-rollover cron | ✅ done |
| 7 | SAVINGS_CAPACITY (existing) | CONTRIBUTE_TO_PROJECT (reused) | contribute | day-25 cron | ✅ done |
| 8 | PROJECT_OFF_TRACK (new) | INCREASE_CONTRIBUTION | contribute | weekly project cron | ✅ done |
| 9 | PROJECT_MILESTONE, GOOD_MONTH (new) | — (positive push) | — | contribution + recap | ✅ done |
| 10 | SAVINGS_CAPACITY (existing) | — (deep-link nudge, no enum) | — | income create + sweep | ✅ done |
