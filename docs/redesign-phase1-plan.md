# Redesign — Phase 1 Implementation Plan

> Actionable checklist for **Phase 1** of the redesign. Derives from
> `redesign-what-and-why.md` (rationale) and `redesign-technical-implementation.md` (design).
> This doc is the *ordered build plan*; those two are the *why* and *how*.
>
> **Strategy decisions (2026-06-29):**
> - **Deprecate, don't delete.** Superseded code/columns (settlement engine,
>   `UserFinancialProfile`, `usable_income`/`safe_spending`) get unwired from behavior and
>   left vestigial (classes kept, columns nullable) until the redesign fully ships. No
>   destructive migrations this phase — keeps rollback cheap.
> - Phase 1 goal = **prove the round trip** end to end with the *smallest* surface, then expand.

## Current state (verified 2026-06-29)

- ✅ **Pivot B step 1 done** — `Project.totalContributed()` now sums the `contributions`
  list (`Project.java`), not settlement `tracking`. Compiles; settlement test still green.
- 🟡 **Pivot D partially scaffolded** — `User` already has `focusMode` / `interventionLevel`
  / `savingPace` (enums `Focus_Mode` / `Intervention_Level` / `Saving_Pace`) = the redesign's
  `focus` / `overspendHelp` / `goalPush`. **Not yet** mapped in `UserMapper` (compiler warns
  "unmapped"), not persisted-through, no onboarding. User also has `incomeSetupCompleted` /
  `financialSetupCompleted` flags.
- 🟡 **Pivot A partially drifted** — `UserFinancialDataService` has income/profile reads
  commented out already.
- ❌ **Pivot C not started** — no `Insight`, `Suggestion`, `SpendingInsightAnalyzer`,
  `GenerateInsightsUseCase`, or `suggestion` table anywhere.
- Still fully live (to be deprecated): `PerformMonthlySettlementUseCase`,
  `MonthlySettlementScheduler` (cron `0 0 0 1 * *`), `SettlementEngine`, `ProjectTracking`
  debt half, `UserFinancialProfile` + 9 enums + CRUD, `SafeSpendingService`,
  `MonthlyObligationPolicy`, `ProjectBudgetAdvisorRequest.Mode {URGENT,RELAXED}`.

---

## Workstream B — Press-to-contribute (finish Pivot B)

- [x] B1. Re-source `totalContributed()` from `contributions`. *(done)*
- [x] B2. `AddProjectContributionUseCase` `@Transactional`: writes a real EXPENSE ledger
      `Transaction` (new `Category.PROJECT_CONTRIBUTION`, carrying `projectId`) **and** appends
      the `ProjectContribution` atomically. `Transaction` gained a nullable `projectId`
      (entity + mapper + `V19` migration); endpoint `POST /projects/{id}/contributions`. *(done)*
- [x] B3. Completion folded into B2: after `addContribution`, if `totalContributed ≥ target`
      → `complete()`. Synchronous, no cron. Covered by `AddProjectContributionUseCaseTest`. *(done)*
- [x] B4. **Expiry** is lazy-on-read via `Project.expireIfDeadlinePassed()`
      (`deadline < today && progress < 100 → EXPIRED`, returns whether it changed). Wired into
      `GetProjectDetailUseCase` + `ListMyProjectsUseCase` (now writable, persist on transition)
      and guarded in the contribute path (new `PROJECT_EXPIRED` error code). *(done)*
- [x] B5. **Deprecated** the settlement cron: `MonthlySettlementScheduler` is now gated by
      `@ConditionalOnProperty(app.settlement.enabled, matchIfMissing=false)` — **disabled by
      default** (`app.settlement.enabled: ${SETTLEMENT_ENABLED:false}`). `SettlementEngine`,
      `PerformMonthlySettlementUseCase`, `ProjectTracking.moneyOwed`, `systemFreeze/Unfreeze`,
      and the manual `AdminSettlementController` trigger are left in place but dormant. *(done)*
      *(The settlement use case was already a gutted no-op since `user_income` removal; the group
      auto-completion it once held no longer existed there — B6 added a fresh driver.)*
- [x] B6. `netSavedAmount()`-direct readers reconciled (all read contributions via the alias —
      consistent). Group-project completion/expiry moved off settlement into a new
      `GroupProjectCompletionService`: lazily expires sub-projects, then COMPLETES when the
      participating aggregate meets the participating target / EXPIRES when no participants
      remain. Wired lazy-on-read into `GetGroupProjectDetailUseCase` (now writable) and
      synchronously into the sub-personal contribution path. Covered by
      `GroupProjectCompletionServiceTest` (5 tests). *(done)*
      *(Deferred: group completion/drop notifications — were tied to the old settlement flow.)*
- [ ] B7. Keep owner `freeze` / `abandon` (user-driven). Update/junk settlement-only tests
      (`PerformMonthlySettlementUseCaseTest`) — mark `@Disabled` rather than delete.

## Workstream D — Traits cold-start (finish Pivot D)

- [x] D1. Wired the existing `focusMode` / `interventionLevel` / `savingPace` end-to-end:
      `UserEntity` columns (`@Enumerated STRING`, nullable, using the domain enums) + `UserMapper`
      (both directions; trait unmapped warning cleared) + `V20` migration (nullable VARCHAR(20))
      + `UserResponse`. Enums map to traits: `Saving_Pace`→goalPush, `Intervention_Level`→
      overspendHelp, `Focus_Mode`→focus. 54 user/auth tests green. *(done)*
      *(Note: the `incomeSetupCompleted/financialSetupCompleted` unmapped warning is separate
      (Pivot A) and left as-is. Enum value naming, e.g. `AGGRESIVE`, not normalized — defer.)*
- [ ] D2. 3-question onboarding endpoint that sets the three traits (defaults if skipped).
      *(SKIPPED for now — revisit.)*
- [x] D3. Rebuilt `ProjectBudgetAdvisorUseCase` (was a throwing stub): reads the user's
      `savingPace` trait instead of the per-request `Mode` (which was **removed** from
      `ProjectBudgetAdvisorRequest`); computes a monthly-saving plan (AGGRESIVE finishes sooner /
      RELAXED stretches +1mo / BALANCED tracks deadline; defaults BALANCED at cold start) and
      **never throws** — the 20% affordability gate is gone (advisory only). 4 advisor tests +
      32 controller tests green. *(done)*
      *(Affordability vs net income / learned disposable is deferred to Pivot A/C.)*
- [x] D4. **Deprecated** `UserFinancialProfile` — verified the goal (stop reading; leave
      classes/table) was already fully satisfied by the earlier "comment all unused code" commit:
      the domain model, 9 enums, repo interface+impl, entity, mapper, DTOs, all CRUD use cases,
      `UserFinancialProfileController`, `BudgetAIController`, and `GenerateBudgetAllocationPlanUseCase`
      are all commented-out husks (package line + `/* … */`); `UserFinancialDataService`'s PROFILE
      branch is commented. No live read remains; the `user_financial_profiles` table is untouched
      (vestigial, as intended). 165 tests green. *(done — no new churn needed)*

## Workstream A — Net-income-only (deprecate)

- [x] A1. No active code reads `usableIncome`/`safeSpending` for enforcement: advisor (D3),
      AI summary (commented), personal-project 20% rule (commented), and — gutted this pass at
      the user's request, beyond redesign scope — the **Group** rules in `CreateGroupUseCase`,
      `AcceptGroupInviteUseCase`, `CreateSubPersonalProjectUseCase` (removed dead income blocks,
      no-op `SafeSpendingService` call, and unused `MonthlyObligationPolicy` import; capacity now
      hardcoded ZERO with a clear "no longer income-derived" note). `MonthlyObligationPolicy` now
      has **zero active callers** (fully vestigial); `SafeSpendingService` is a no-op still called
      from 8 vestigial spots (income CRUD + personal-project use cases) — left per deprecate-don't-
      delete. The `user_income` table / income CRUD still *store* the fields (nothing consumes
      them); no drop migration. 72 group/project tests green. *(done)*
- [x] A2. Advisor computes against the `savingPace` trait + target/deadline, no income gate
      (done in D3). Learned `disposable` (net income − recurring) deferred to Phase 2/Pivot C. *(done)*

## Workstream C — Insight + Suggestion round trip (the thesis core)

> **Detailed breakdown: `docs/redesign-workstream-c-plan.md`** — sub-tasks, file anchors,
> algorithms/formulas, the `V21` schema, build order, test plan, and open decisions. The bullets
> below are the summary; build from the detailed doc.

- [ ] C1. `SpendingInsightAnalyzer` pure domain service (no Spring) — **Phase 1 methods only**:
      z-score anomaly (rolling mean+stddev over trailing N months/category) + linear budget-pace
      projection (`spent / dayOfMonth × daysInMonth`). Reuse `GetTransactionAnalyticsUseCase`'s
      `GROUP BY` SQL for baselines. Fully unit-tested.
- [ ] C2. `Insight` domain value object (ephemeral): type, category, severity, period, metrics,
      code. `GenerateInsightsUseCase` (load via repos → analyzer → return). Takes `UserTraits`:
      `focus` filters which insight types surface.
- [ ] C3. In-app **insights screen** (pull-only) backed by C2.
- [ ] C4. `suggestion` table — Postgres, one JSONB `payload`, partial-unique dedup index
      `(user_id, dedup_key) WHERE status='PENDING'`. `SuggestionRepository` + entity + migration.
- [ ] C5. **Single action type `RAISE_BUDGET`.** Producer on transaction-create that, gated by
      `interventionLevel` (overspendHelp) severity, INSERTs a PENDING `Suggestion` and enqueues
      an Expo push (existing Redis `NotificationQueuePort`) with `data:{suggestionId,type}`.
- [ ] C6. Idempotent `POST /suggestions/{id}/respond {accept}`: conditional
      `UPDATE … WHERE id=? AND status='PENDING'`; action runs only if a row changed; status flip
      + mutation in the **same `@Transactional`**. Execute via a `switch` on type → existing
      `UpdateBudgetUseCase`. Store decisions only (no suppression yet).

## Explicitly deferred to Phase 2–3

Dismissal/suppression loop (store `dismiss_count` now, act later) · more action types ·
recurring-cost detector → Pivot A `disposable` · scheduled digest producer · cooldown/daily cap ·
exponential-smoothing forecast · full LLM narration over structured insights · the destructive
migrations that finally drop the deprecated columns/classes.

## Suggested build order

`B2→B3→B4` (contribute works end-to-end) → `D1→D3` (traits give the producer a gate) →
`C1→C2→C3` (insights, no push yet) → `C4→C5→C6` (the yes/no round trip) → `B5/B7, A1, D4`
(deprecation cleanup once the new paths are proven).
