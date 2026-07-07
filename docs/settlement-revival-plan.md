# Settlement Engine Revival — Income + Traits Integration Plan

> Revive the dormant monthly settlement engine and make it work with the new
> `users.income` + user-traits model (`Focus_Mode` / `Intervention_Level` / `Saving_Pace`),
> instead of the removed `user_income` table.

> **STATUS (2026-07-07): IMPLEMENTED — full suite green (349 tests, 0 failures).** Steps 1–6 all
> landed; Step 7 is left as the env flag (`SETTLEMENT_ENABLED`, yaml default still `false`) per the
> resolved open item. Correctness hardening beyond the plan, from a post-implementation review:
> (a) per-project completion skips a project already flipped to `EXPIRED` this run; (b) a project
> **frozen this run** is not also completed (would carry debt ≥ cap and fire a contradictory
> frozen+completed notification pair) — it can complete in a later run once debt clears;
> (c) **unfreeze-on-zero-debt now applies to TRACK_ONLY** too (only the punitive freeze/expire
> transitions are gated on penalties) so a debt-cleared frozen project isn't stranded; (d)
> `updateFinancialSetup` with a null `autoInvestSurplus` now **preserves** the existing preference
> instead of resetting it to true. Next up:
> [`settlement-insights-followup.md`](./settlement-insights-followup.md) (item 0/0a/0b is a blocker).

## Status of the code today

- `PerformMonthlySettlementUseCase` is **already a full working implementation** (not the
  no-op husk older notes describe — the `origin/test` merge restored it). It already gates on
  `user.hasCompletedFinancialSetup()` and falls back to `user.getIncome()`.
- `SettlementEngine` (pure math: `calculatePenalty`, `recalculateMonthLeft`) needs **no changes**;
  `SettlementEngineTest` stays green untouched.
- `MonthlySettlementScheduler` — cron `0 0 0 1 * *`, gated `@ConditionalOnProperty app.settlement.enabled`
  (**default false**).
- `AdminSettlementController` — `POST /api/v1/admin/settlement/run` manual trigger, live.
- `SafeSpendingService.recalculateAndSaveSafeSpending()` is a confirmed **no-op** (old `user_income`
  table gone) but still called at the end of settlement.
- `PerformMonthlySettlementUseCaseTest` is **fully commented out** (references the deleted
  `UserIncomeRepository`).
- Tunables in `application.yml`: `app.project.{freeze-mult:3, unfreeze-mult:0, max-frozen-months:3}`.
- **Next migration = V25.**

The work is **trait integration + reactivation + test restoration**, not a rewrite.

> **After this lands, see [`settlement-insights-followup.md`](./settlement-insights-followup.md)** —
> reviving settlement re-introduces overlaps with the insight/suggestion system (funding-model
> source-of-truth, contribute-nudge double-asks, etc.). Item 0 there (Option 2: settlement appends a
> `ProjectContribution`) is a correctness blocker, not optional.

## Design decisions (confirmed with product)

| # | Question | Decision |
|---|----------|----------|
| 1 | How does `Saving_Pace` affect the saving target? | **Cap total goals by `user.getSavingCapacity()`** |
| 2 | How does `Focus_Mode` change settlement? | **`TRACK_ONLY` = no penalties / no debt** |
| 3 | How does `Intervention_Level` affect settlement? | **Only gate notifications** (math identical for all) |
| 4 | Should surplus auto-invest respect a preference? | **Revive the preference, wired through financial setup** |

Trait enums: `Saving_Pace{RELAXED,BALANCED,AGGRESIVE}`, `Intervention_Level{NOTIFY,GENTLE,HARD}`,
`Focus_Mode{SAVE_MORE,REDUCE_SPENDING,TRACK_ONLY}`.

---

## Step 1 — Saving pace caps the expected saving (Decision 1)

In `settleUserProjects`, after computing `totalSavingGoals = Σ monthlySaving of ACTIVE trackings`:

```java
BigDecimal capacity = user.getSavingCapacity();             // income × 0.10/0.20/0.35 by pace/focus
BigDecimal effectiveGoals = totalSavingGoals.min(capacity); // never expect more than capacity
```

Use `effectiveGoals` (not `totalSavingGoals`) to compute `netPenalty` / `netSurplus`. A RELAXED or
low-income user is never penalized for failing to save beyond their means. Per-project
**distribution** still uses each project's own `monthlySaving` unchanged.

## Step 2 — TRACK_ONLY suppresses penalties/debt (Decision 2)

```java
boolean penaltiesEnabled = user.getFocusMode() != Focus_Mode.TRACK_ONLY;
```

- `penaltiesEnabled == false`: skip the entire `netPenalty` branch (no `moneyOwed` accrual) **and**
  skip the freeze/unfreeze/expire lifecycle block. Savings still credit, `currentMonth` advances,
  `monthLeft` recalcs, projects still auto-complete on target.
- `penaltiesEnabled == true` (SAVE_MORE / REDUCE_SPENDING): full debt + freeze engine as today.
- Deadline-based expiry (`Project.expireIfDeadlinePassed`, lazy-on-read) is independent and still
  applies to TRACK_ONLY — intended.

## Step 3 — Intervention level gates settlement notifications (Decision 3)

Settlement currently only logs. Add real notifications via the existing
`CreateNotificationUseCase(userId, content, deepLink)`, gated by `interventionLevel`. Each event has
an inherent severity; the level sets the push threshold. **The debt/freeze math runs identically for
everyone** — only notification loudness differs.

| Event | Severity | NOTIFY | GENTLE | HARD |
|-------|----------|:------:|:------:|:----:|
| Project COMPLETED | INFO | – | – | ✓ |
| Project FROZEN | WARN | – | ✓ | ✓ |
| Project EXPIRED | CRITICAL | ✓ | ✓ | ✓ |

Higher intervention = more messages; deep-links to the project. **This mapping is a judgment call —
open item #1.** Inject `CreateNotificationUseCase` into the use case.

## Step 4 — Auto-invest-surplus preference, wired through financial setup (Decision 4)

**Domain (`User`)**
- Add `Boolean autoInvestSurplus` + `isAutoInvestSurplus()` returning
  `autoInvestSurplus == null ? true : autoInvestSurplus` (null = today's always-invest behavior).
- Extend the setup mutator with a fifth param:
  ```java
  updateFinancialSetup(BigDecimal income, Saving_Pace savingPace,
                       Intervention_Level interventionLevel, Focus_Mode focusMode,
                       Boolean autoInvestSurplus)
  ```
  Sets `this.autoInvestSurplus = (autoInvestSurplus == null) ? Boolean.TRUE : autoInvestSurplus;`.
  Existing null-guards on the other three fields stay. **`hasCompletedFinancialSetup()` is
  unchanged** — auto-invest has a safe default, so it does not gate completeness.

**Request DTO (`UpdateFinancialSetupRequest`)**
- Add `Boolean autoInvestSurplus`, left **optional** (no `@NotNull`) → null coerces to `true` in the
  domain, so existing onboarding payloads and `AuthControllerTest` keep passing while the field
  becomes part of the contract. (Alternative: make it `@NotNull` to force an explicit onboarding
  choice — flag if preferred.)

**Use case (`UpdateFinancialSetupUseCase`)**
- Pass `request.autoInvestSurplus()` into `user.updateFinancialSetup(...)`. No other change
  (`GetFinancialSetupUseCase` benefits automatically via `fromDomain`).

**Response (`FinancialSetupResponse`)**
- Add `boolean autoInvestSurplus`, populated from `user.isAutoInvestSurplus()` in `fromDomain`, so
  `GET`/`PUT /me/financial-setup` both echo it back.

**Persistence + migration**
- `UserEntity`: `@Column(name="auto_invest_surplus") Boolean autoInvestSurplus;` + `UserMapper` both
  directions.
- **V25** `V25__add_user_auto_invest_surplus.sql`:
  `ALTER TABLE users ADD COLUMN auto_invest_surplus boolean;` (nullable; null = auto-invest on).

**Engine (`PerformMonthlySettlementUseCase`)**
- Surplus branch: always repay debt (phase 1); run the auto-invest phase (phase 2) **only if
  `user.isAutoInvestSurplus()`**, else leave leftover surplus as free cash (`surplusInvestedMap`
  stays zero, reflected in the history snapshot).

## Step 5 — Remove the dead SafeSpending call (cleanup)

Drop `safeSpendingService.recalculateAndSaveSafeSpending(ownerId)` (confirmed no-op) and the
`SafeSpendingService` dependency from `PerformMonthlySettlementUseCase`. Leave the class itself alone
(still referenced by vestigial group code, per deprecate-don't-delete).

## Step 6 — Restore the test

`PerformMonthlySettlementUseCaseTest` is fully commented out (references deleted
`UserIncomeRepository`). Rewrite against the new model: mock `UserRepository` returning users with
`income` + the three traits. Cover:
- capacity capping reduces / erases penalty,
- `TRACK_ONLY` → zero `moneyOwed`, no freeze,
- `autoInvestSurplus` true vs false surplus routing,
- notification gating per level (verify `CreateNotificationUseCase` interactions).

`SettlementEngineTest` stays green untouched. Target: **full suite green.**

## Step 7 — Reactivation switch

The cron stays gated by `app.settlement.enabled` (default false in yaml, so tests/CI never
auto-fire). **Recommendation:** enable per-environment via `SETTLEMENT_ENABLED=true` rather than
flipping the code default — reversible, and `AdminSettlementController` already allows manual/staging
runs meanwhile. **Open item #2:** env flag only vs. flip the yaml default for everyone.

---

## Files touched

| File | Change |
|------|--------|
| `application/usecases/settlement/PerformMonthlySettlementUseCase.java` | Steps 1–5 core logic + notifications, drop SafeSpending dep |
| `domain/models/user/User.java` | `autoInvestSurplus` field + helper + 5-arg `updateFinancialSetup` |
| `application/dtos/requests/users/UpdateFinancialSetupRequest.java` | add optional `autoInvestSurplus` |
| `application/usecases/user/UpdateFinancialSetupUseCase.java` | pass the flag through |
| `application/dtos/responses/FinancialSetupResponse.java` | echo `autoInvestSurplus` |
| `infrastructure/persistence/entities/UserEntity.java` | `auto_invest_surplus` column |
| `infrastructure/persistence/mappers/UserMapper.java` | map both directions |
| `db/migration/V25__add_user_auto_invest_surplus.sql` | add nullable column |
| `test/.../settlement/PerformMonthlySettlementUseCaseTest.java` | rewrite against new model |
| existing financial-setup tests | update to 5-arg mutator / new response field |

## Open items — RESOLVED

1. **Notification mapping** (Step 3 table) — ✅ **Accepted as-is.**
2. **Reactivation** (Step 7) — ✅ **Env flag only; yaml default stays `false`.** User will flip the
   reactivation switch (`SETTLEMENT_ENABLED=true`) later, per-environment. No code/yaml change here.
