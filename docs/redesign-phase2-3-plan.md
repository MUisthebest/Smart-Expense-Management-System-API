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

## P2-A — Dismissal-suppression learning loop  ✅ IMPLEMENTED (2026-06-30)

> Shipped the **topic-stats table** (open decision #1, recommended option). New `suggestion_topic_stats`
> table (`V23`, surrogate `id` + unique `(user_id, dedup_key)`); domain `SuggestionTopicStat`
> (`start` / `recordDismissal`) + `SuggestionTopicStatRepository` (+ entity / Jpa / impl, manual mapping).
> `RespondToSuggestionUseCase` now increments the topic counter on a *real* dismissal (only when the
> PENDING→DISMISSED flip actually happened, in the same `@Transactional`; best-effort, never fails the
> dismissal). `SuggestionProducer.isSuppressed` holds back a topic once `dismissCount >= SUPPRESS_AFTER_DISMISSALS`
> (=2) **unless** the ask is CRITICAL (escalation always breaks through). Tests: dismiss increments, no-op
> respond doesn't, producer suppresses past threshold, CRITICAL breaks through.

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

## P2-B — More action types  ✅ IMPLEMENTED (2026-06-30)

> `SuggestionType` gained `CREATE_BUDGET` + `CONTRIBUTE_TO_PROJECT` (code-only, varchar). `ProposedAction`
> is now polymorphic (`category`/`budgetId`/`month`/`year`/`projectId`/`resolvedValue`); each accept branch
> validates only its own fields. `RespondToSuggestionUseCase.executeAction` switch extended:
> `CREATE_BUDGET → CreateBudgetUseCase`, `CONTRIBUTE_TO_PROJECT → AddProjectContributionUseCase`.
> **Producer triggers:** CREATE_BUDGET on an expense when `OVERSPEND_ANOMALY` fires for a category with
> **no** budget (resolvedValue = `trailing3MonthMedian`); CONTRIBUTE_TO_PROJECT on an **income** create
> (the natural "got paid → save?" moment) when `SAVINGS_CAPACITY` shows headroom and the user has an active
> non-group saving project with room. `CreateTransactionUseCase` now calls `maybeProduceSavingsContribution`
> on INCOME. Tests: each type's producer payload + accept-side use-case call + null-field guards.

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

## P2-C — More insight types (analyzer extensions, still pure)  ✅ IMPLEMENTED (2026-06-29)

> Shipped: `InsightType` gained `CATEGORY_TREND`, `RECURRING`, `SAVINGS_CAPACITY`; all three
> detectors live in `SpendingInsightAnalyzer` (still pure, `asOf`-driven). `GenerateInsightsUseCase.focusRank`
> extended so `REDUCE_SPENDING` ranks spending-control first and `SAVE_MODE` ranks saving/recurring
> first. +12 analyzer tests (25 total in `SpendingInsightAnalyzerTest`); full suite 221/0/0.
> Decisions made: CATEGORY_TREND emits only **upward** trends (slope>0) with growthRate (slope/mean)
> ≥0.10 WARN / ≥0.25 CRITICAL, min 3 months; RECURRING needs a **non-blank description**, ≥3 distinct
> months, all monthly amounts within ±15% of their median (INFO); SAVINGS_CAPACITY derives net income
> from INCOME rows in `history` (avg over months with income in the window) — **no P2-D plumbing needed
> yet** — `disposable = avgIncome − estimatedMonthlyRecurringCost − Σ budget.amountLimit`, emits INFO
> only when positive. Public `estimatedMonthlyRecurringCost(history, period)` exposed for P2-D reuse.

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

## P2-D — Recurring-cost detector → Pivot A `disposable` (net-income-only)  ✅ IMPLEMENTED (2026-06-30)

> Largely satisfied by P2-C: `disposable = avgMonthlyIncome − estimatedMonthlyRecurringCost − Σ committed
> budgets` is computed in `SpendingInsightAnalyzer.detectSavingsCapacity`, with income derived from INCOME
> ledger rows (no `usableIncome`/`safeSpending` resurrected — Workstream A's gutted fields stay vestigial).
> P2-B's CONTRIBUTE producer consumes that `disposable` (read from the SAVINGS_CAPACITY insight's metrics)
> as the basis for the suggested amount. Cold-start (no income signal) → no SAVINGS_CAPACITY insight →
> no contribution ask (degrades to silence rather than a bad number). The 20% rule stays advisory (D3).

**Goal:** finish Pivot A — replace any remaining declared-obligation thinking with **learned**
recurring costs, yielding a `disposable` figure used by `SAVINGS_CAPACITY`.

- Consume P2-C's `RECURRING` insights to estimate monthly committed spend.
- Net income comes from the income ledger (`TransactionRepository.getTotalIncomeBetween`) or the
  existing income flags (`incomeSetupCompleted`); **do not** resurrect `usableIncome`/`safeSpending`
  (Workstream A gutted those — they remain vestigial, untouched).
- The 20% project-saving rule stays **advisory** (already true post-D3 in `ProjectBudgetAdvisorUseCase`).
- **Tests:** disposable = income − recurring − budgets across a few fixtures; cold-start (no
  recurring history) degrades gracefully to a conservative capacity.

## P2-E — `savingPace` (goalPush) scales savings amounts  ✅ IMPLEMENTED (2026-06-30)

> `SuggestionProducer.scaleByPace` multiplies `disposable` by the pace tier (RELAXED 0.10 / BALANCED 0.20 /
> AGGRESIVE 0.30; cold-start null → BALANCED), then caps the result at the project's `remaining()` so a
> suggestion never overshoots the goal. Unit-tested across all three tiers + the cap.

`User.getSavingPace()` (`RELAXED | BALANCED | AGGRESIVE`) already drives `ProjectBudgetAdvisorUseCase`
(D3). Extend it to scale the **suggested contribution amount** in `CONTRIBUTE_TO_PROJECT`
(P2-B): e.g. AGGRESIVE = larger share of `disposable`, RELAXED = smaller. Pure multiplier in the
producer; unit-test the three tiers + cold-start default `BALANCED`.

## P2-F — Scheduled digest producer (for SAVE_MORE / TRACK_ONLY)  ✅ IMPLEMENTED (2026-06-30)

> `SendInsightDigestUseCase.sendFor(userId, asOf)` reuses `GenerateInsightsUseCase`, counts the `>= WARN`
> insights, and (if any) sends one digest notification `notification.digest.weekly|<count>` deep-linking
> `app://insights`. `WeeklyInsightDigestScheduler` (`@Scheduled` `0 0 9 * * MON`, gated by
> `@ConditionalOnProperty app.digest.enabled` — **off by default**, mirroring `MonthlySettlementScheduler`)
> fans out over `UserRepository.findAllForDigest()` (active + notification-enabled users whose focus is
> SAVE_MORE/TRACK_ONLY; new `JpaUserRepository.findForDigest` JPQL). Per-user failures are isolated. Pure
> per-user logic is unit-tested; the JPQL native-enum query + fan-out share the live-DB caveat (kept off).
> NOTE: plan referenced `SAVE_MODE` — the V22 merge renamed it `SAVE_MORE`.

`focusMode` `SAVE_MORE`/`TRACK_ONLY` users want fewer interruptions. Add a scheduled job
(`@Scheduled`, gated by `@ConditionalOnProperty` like `MonthlySettlementScheduler`) that, on a
cadence (weekly?), batches that period's `>= WARN` insights into a single digest notification
instead of per-event pushes. Reuses `GenerateInsightsUseCase` for the data. Default the flag **off**
until validated.

---

# PHASE 3 — Polish

## P3-A — Cooldown / daily cap (global fatigue ceiling)  ✅ IMPLEMENTED (2026-06-30)

> Shipped the third fatigue layer on top of dedup (P1) + dismissal suppression (P2-A): a per-user
> **daily push cap** and a **per-topic cooldown**, both enforced in `SuggestionProducer.persistAndPush`
> before insert and both **bypassed by a CRITICAL escalation** (same escalation-always-breaks-through
> rule as P2-A). Pieces:
> - **`V24__add_topic_stats_last_pushed_at.sql`** adds `last_pushed_at timestamptz` to
>   `suggestion_topic_stats`. Domain `SuggestionTopicStat` gained `lastPushedAt` + `recordPush(at)`;
>   entity + repo impl map the new column. (Migration dir is gitignored — left in place on disk.)
> - **Cooldown:** `isInCooldown(stat, now)` — skip while `now < lastPushedAt + cooldownHours`. The
>   topic stat is now created/updated on a successful push too (`recordPush`, best-effort), not just
>   on dismissal.
> - **Daily cap:** `dailyCapReached(userId, now)` counts the user's suggestions created since
>   start-of-day via new `SuggestionRepository.countByUserIdAndCreatedAtAfter` (each push = one row);
>   `>= dailyCap` → skip. Non-positive cap = unlimited.
> - **Tunables:** `app.suggestion.daily-cap` (default 3) / `app.suggestion.cooldown-hours` (default 24),
>   bound via `SuggestionFatigueProperties`. A `Clock` bean (`DomainServiceConfig`) is injected so the
>   time-sensitive rules stay deterministic in tests.
> - **Tests:** +5 in `SuggestionProducerTest` (cooldown blocks within window / allows after it; daily
>   cap blocks; CRITICAL bypasses both; a successful push stamps `lastPushedAt`). Full suite 242/0/0
>   (1 skipped = the DB-context boot). Live-DB caveat: V24 column validate is unproven (Phase-1.5 V1).

On top of dedup (P1) + dismissal suppression (P2-A): a per-user **daily push cap** and a
**per-topic cooldown** (min time between asks on the same topic). Store last-push timestamps
(reuse the P2-A topic-stats table). Enforce in the producer before insert. Tunable via
`application.yml` (`app.suggestion.daily-cap`, `app.suggestion.cooldown-hours`).

## P3-B — Exponential-smoothing forecast  ✅ IMPLEMENTED (2026-06-30)

> Augmented `detectBudgetPace` with an exponentially-smoothed forecast as an alternative to the
> linear projection, selectable by flag (the analyzer stays pure + `asOf`-driven).
> - **`ForecastMethod` enum** `{LINEAR, EXP_SMOOTHING}` (new `domain/services`). `SpendingInsightAnalyzer`
>   gained a `forecastMethod` field with a no-arg ctor defaulting to `LINEAR` (so every existing caller /
>   test is unchanged) plus a `ForecastMethod` ctor. `DomainServiceConfig` reads
>   `app.insight.forecast-method` (default `LINEAR`) and passes it in.
> - **EXP_SMOOTHING:** builds a per-day spend series for the category from `history` (so
>   `detectBudgetPace` now takes `history`), runs simple exponential smoothing (α=0.5, includes
>   zero-spend days) to get an EWMA daily burn rate, then `projected = spent + rate × remainingDays`.
>   An isolated early spike decays away instead of being linearly extrapolated; sustained spend tracks
>   the linear figure. **Falls back to LINEAR when no daily series is available** (budget-only spend,
>   e.g. the producer path) and labels the method honestly in `metrics.forecastMethod`
>   (+`metrics.smoothedDailyRate` when smoothing actually ran).
> - **Default LINEAR** = no live behaviour change (decision: the live DB/insight path is still
>   unproven, so EXP_SMOOTHING is opt-in and studied against the linear control rather than silently
>   swapped in). `projectedEndOfMonth` key/shape unchanged, so the producer's RAISE_BUDGET resolved
>   value is unaffected.
> - **Tests:** +4 in `SpendingInsightAnalyzerTest` (early spike → exp emits nothing while linear WARNs;
>   sustained spend → exp ≈ linear 1200 + rate 40; budget-only → exp falls back to LINEAR; linear is
>   labelled). Full suite 246/0/0 (1 skipped). Live path unproven (Phase-1.5 V1) as ever.

Replace/augment the linear `BUDGET_PACE` projection with exponential smoothing (or
seasonally-naive) for a smoother end-of-month estimate that reacts to recent days without
overshooting on a single spike. Keep the linear version behind a flag for comparison; the analyzer
stays pure and `asOf`-driven. Unit-test against the same fixtures as the linear method.

## P3-C — LLM narration layer (the only place the LLM enters)  ✅ IMPLEMENTED (2026-06-30)

> Shipped `InsightNarrator` (`application/usecases/insight`) — the single LLM touch-point. Reuses the
> existing `LlmPort` (Gemini adapter). `narrate(Insight[, language])`:
> - **Gated + best-effort:** `app.insight.narration.enabled` (default **false**). Disabled / null insight
>   → returns `null`; any LLM failure or blank reply → `null`; **never throws**. Callers fall back to the
>   i18n `code` (Phase-1 behaviour), so narration is never on the critical path.
> - **LLM phrases, never computes:** `buildPrompt` (package-visible for the guardrail test) embeds the
>   exact `metrics` figures + `code` and instructs the model to *use only the numbers given; do not
>   invent, recompute, convert, round, or add any figure.* The deterministic facts stay authoritative.
> - **Wiring (both optional):** `InsightResponse` gained a nullable `narrative` field, set per-insight in
>   `GenerateInsightsUseCase.toResponse` (best-effort). `SuggestionProducer.pushText` narrates the push
>   body, falling back to the raw i18n key — this finally gives the notification human text (closes the
>   [[suggestion-notification-ux]] push-body gap). Both no-op when the flag is off.
> - **Tests:** new `InsightNarratorTest` (5): disabled → never calls LLM; LLM failure → null; blank → null;
>   success → trimmed; **guardrail** — the captured prompt carries the computed figures + code and the
>   "do not invent" instruction (the "feed fixed metrics, check the template path" check). Existing
>   producer/insight tests pass a disabled/mock narrator (push stays the i18n key). Full suite 251/0/0.

**Goal:** phrase the already-computed insights/suggestions in natural language; the LLM **never**
computes a number.

- New `LlmPort`-based narrator that takes an `Insight`/`Suggestion` (structured `metrics` + `code`)
  and returns a localized sentence. Gemini key already configured (`app.gemini.api-key`).
- Wire optionally into `InsightResponse` (a `narrative` field) and the push content — **best-effort**:
  if the LLM is down, fall back to the `code` (Phase-1 behavior). Never on the critical path.
- **Guardrail tests:** narrator is bypassed when the port fails; the numbers in the prose match the
  `metrics` (assert the LLM didn't invent figures — feed fixed metrics, check the template path).

## P3-D — Trait onboarding endpoint (D2, skipped in Phase 1)  ✅ ALREADY DONE (verified 2026-06-30)

> Satisfied by the **V22 "financial setup" merge** — no Phase-3 code needed. `AuthController`
> (`/api/v1/auth`) already exposes `GET /me/financial-setup` (`GetFinancialSetupUseCase`) and
> `PUT /me/financial-setup` (`UpdateFinancialSetupUseCase` → `User.updateFinancialSetup(income,
> savingPace, interventionLevel, focusMode)`), with `User.hasCompletedFinancialSetup()` for cold-start
> detection and `FinancialSetupResponse` (covered by `AuthControllerTest`). This is a superset of the
> D2 ask (also captures `income`).
>
> The only literal gap — *persisting* default trait values (`BALANCED`/`NOTIFY`/`TRACK_ONLY`) at
> registration — is intentionally left unbuilt: the least-spammy defaults are already the effective
> cold-start behaviour via null-handling (`interventionLevel == null` → no push = NOTIFY;
> `savingPace == null` → BALANCED in `scaleByPace`/`ProjectBudgetAdvisorUseCase`; `focusMode == null`
> → severity-only ordering = TRACK_ONLY). Storing explicit defaults would also make
> `hasCompletedFinancialSetup()` ambiguous (defaulted vs chosen). Kept null-driven. **Polish, not a
> blocker — and already covered.**

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
