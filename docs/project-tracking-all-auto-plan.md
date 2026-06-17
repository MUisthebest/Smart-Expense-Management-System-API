# Project Tracking — All-Auto Variant (implementation plan)

Status: IMPLEMENTED (core) — see "Implementation status" at the bottom for what shipped
and what was deliberately deferred.
Supersedes the contribution-based recovery in `project-tracking-redesign.md` §3.3.
That doc is kept as the exploration of alternatives; THIS doc is the chosen direction.

Scope: personal projects, sub-personal projects, group projects, monthly settlement,
arrears, safe spending. App holds **no real money** and is **not** bank-synced.

---

## 1. Decision

Drop the manual "contribute" concept entirely. Saving is **fully automatic**:

- The budget reserves each member's `monthly_saving` out of safe spending every month.
- Monthly settlement credits that reservation to `money_saved` (if the user didn't overspend),
  applies an overspend penalty as debt (`money_owed`), and repays debt from any underspend
  surplus.
- "Saving" therefore means "not spending". There is no user action that adds money — there is
  no money to add. The only lever a user has is **how much they spend**.

Consequence: debt can only ever be repaid by underspending. The design must guarantee that loop
is **bounded** and **always able to recover**. That guarantee is the heart of this plan.

---

## 2. Core concepts

### 2.1 `netSaved` is the single source of truth
```
netSaved = money_saved - money_owed
```
- `money_saved` (gross): the auto-save reservation credited each settled month, capped at target.
- `money_owed` (debt): overspend clawback.
- `netSaved` (DERIVED, not stored): real progress. **Progress %, project completion, and group
  auto-complete all read `netSaved`.**

Completion requires `netSaved >= target`, which (because gross caps at target) means debt must be
fully repaid before anything completes. No project can "complete" while in debt.

### 2.2 Bounded, self-healing debt
- Debt per project is **capped at the freeze threshold** = `FREEZE_MULT * monthly_saving`
  (default `FREEZE_MULT = 3`). A project hits the cap exactly when it freezes.
- A **FROZEN** project's debt can only **decrease** (repaid from surplus); no new penalty is added.
- Frozen debt **still suppresses safe spending** until repaid (see §5) — this is what keeps
  pressure on the user to recover.
- **Auto-unfreeze** when debt drops below `UNFREEZE_MULT * monthly_saving`
  (default `UNFREEZE_MULT = 1`). Hysteresis between 3x and 1x prevents month-to-month flapping.

### 2.3 The recovery guarantee (why no debt spiral is possible)
```
safe_spending = usable_income - Σ monthly_saving(active) - Σ money_owed(all)   (floored at 0)
```
- Obligation rule already enforces `Σ monthly_saving <= 20% * usable_income`.
- Debt cap (§2.2) enforces `Σ money_owed <= FREEZE_MULT * Σ monthly_saving <= 60% * usable_income`.

Therefore **`safe_spending >= 20% * usable_income`, always.** The user can never be pinned to zero
spendable money by debt alone, so there is always headroom to underspend, generate surplus, and
repay. Recovery is mathematically always reachable. (The current *uncapped* design lacks this:
debt can exceed income, pin safe spending at 0, and spiral forever.)

---

## 3. Project state machine

```
ACTIVE ──(debt reaches FREEZE_MULT*monthly)──▶ FROZEN
FROZEN ──(debt drops below UNFREEZE_MULT*monthly)──▶ ACTIVE
ACTIVE ──(netSaved >= target)──▶ COMPLETED            [terminal, latch]
ACTIVE/FROZEN ──(monthLeft <= 0 and netSaved < target)──▶ EXPIRED   [terminal]
FROZEN ──(frozen for MAX_FROZEN_MONTHS with no net repayment)──▶ EXPIRED   [terminal]
admin dissolve / owner abandon ──▶ ABANDONED            [terminal]
```

- `FROZEN` is no longer "excluded from everything". It stops *crediting new savings* but keeps
  *participating in debt repayment and calendar advance*.
- `EXPIRED` is a NEW terminal status: deadline reached under target, or stuck-frozen too long.
  On EXPIRED, the project's obligation is **released** (debt stops affecting safe spending) so a
  failed goal doesn't haunt the user forever.

---

## 4. Settlement engine — target pseudocode

Settlement is the single engine and the sole owner of the calendar. It now processes
**ACTIVE + FROZEN** projects (FROZEN for repayment only).

```
settleUserProjects(owner, projects):
    income = userIncome(owner); if none -> skip
    track = projects with a tracking row, status in {ACTIVE, FROZEN}
    if empty -> return

    actualExpenses   = Σ(owner EXPENSE tx in previous calendar month)
    totalDebt        = Σ money_owed(track)
    baseSafeSpending = currentSafeSpending + totalDebt
    netSurplus / netPenalty = compare(actualExpenses, baseSafeSpending)

    // 1. credit reservation to ACTIVE only; cap gross at target
    for p in track:
        if p.ACTIVE: gross[p] = min(money_saved + monthly_saving, target[p])
        else:        gross[p] = money_saved          // FROZEN: no new savings
        owed[p] = money_owed

    // 2. surplus: repay debt first (HIGH->LOW), then auto-invest into ACTIVE (HIGH->LOW)
    if netSurplus > 0:
        repay owed[p] from netSurplus across track (HIGH->LOW)
        if autoInvestSurplus:
            add remaining surplus to gross[p] of ACTIVE up to target room (HIGH->LOW)

    // 3. penalty: grow debt on ACTIVE only, capped at FREEZE_MULT*monthly (LOW->HIGH)
    else if netPenalty > 0:
        for p in ACTIVE (LOW->HIGH):
            cap   = FREEZE_MULT * monthly_saving(p)
            room  = max(0, cap - owed[p])
            toAdd = min(netPenalty, room, perProjectShare)
            owed[p] += toAdd; netPenalty -= toAdd
        // leftover penalty with no ACTIVE room is discarded (no real money; cap is the consequence)

    // 4. persist + advance calendar ONCE + transitions + history
    for p in track:
        newMonthLeft = recalc(target, gross[p], monthly_saving, monthLeft) // monthLeft - 1 effectively
        net = gross[p] - owed[p]
        save tracking(gross[p], currentMonth+1, newMonthLeft, owed[p])
        write history snapshot

        if p.ACTIVE and owed[p] >= FREEZE_MULT*monthly:   p.freeze(); frozenMonths=0
        if p.FROZEN and owed[p] <  UNFREEZE_MULT*monthly:  p.unfreeze()
        if p.FROZEN: frozenMonths += 1

        if net >= target[p]:        p.complete(); if sub: groupCompleteCheck(p)
        elif newMonthLeft <= 0:     p.expire();   releaseObligation(p); if sub: groupShrink(p)
        elif p.FROZEN and frozenMonths >= MAX_FROZEN_MONTHS:
                                    p.expire();   releaseObligation(p); if sub: groupShrink(p)

    recalcSafeSpending(owner)   // counts ACTIVE monthly + ALL non-terminal debt (incl FROZEN)
```

Notes:
- Completion checked on `net` → a project finishes only after debt is fully repaid.
- Surplus repays debt before investing → no deadlock; frozen projects climb back to ACTIVE.
- Penalty cap → frozen debt only goes down → recovery is finite.

---

## 5. Safe spending changes

`SafeSpendingService.sumObligationsForUser` currently counts only ACTIVE projects. Change to:

- ACTIVE project   -> add `monthly_saving + money_owed`
- FROZEN project   -> add `money_owed` only (we stopped reserving its monthly_saving)
- COMPLETED/EXPIRED/ABANDONED -> add nothing (obligation released)

This keeps recovery pressure on frozen debt (load-bearing, per §2.3) and stops the perverse
"freeze to get your spending money back" incentive.

---

## 6. Group handling

- **Required target and aggregate are computed from participating members**, defined as members
  whose sub-project is NOT terminal (`EXPIRED`/`ABANDONED`). FROZEN members **stay in** both the
  required target and the aggregate — freezing is a temporary stumble that self-heals, so the
  group simply waits; it does not prematurely shrink the goal.
  ```
  required = Σ subTarget(participating)
  aggregate = Σ netSaved(participating)
  complete when aggregate >= required
  ```
- When a member's sub-project goes terminal (`EXPIRED` via stuck-frozen/deadline, or `ABANDONED`),
  it drops out of both `required` and `aggregate` permanently → the remaining members can still
  complete. Prefer **computing** `required` dynamically over mutating a stored target, to avoid
  reduce-then-restore bugs (`GroupProject.reduceTargetAmount` becomes unnecessary).
- `COMPLETED` is a one-way latch; a later unfreeze of some member does not reopen it.
- Group auto-complete is now a **monthly** event (checked in settlement), since there is no
  contribution path to trigger it intra-month. This is acceptable and simpler.

---

## 7. Removals (the all-auto simplification)

Delete the manual-contribution surface and the redundant arrears job:

| Remove / gut | Reason |
|---|---|
| `AddProjectContributionUseCase` + its controller endpoint | no manual saving in all-auto |
| `ListProjectContributionsUseCase`, `GetProjectContributionSummaryUseCase` + endpoints | nothing to list |
| `Project.addContribution(...)` + the `contributions` list | dead |
| `ProjectContribution`, `ContributionId`, `ProjectContributionEntity`, its mapper + repo | dead |
| `AddProjectContributionRequest`, `ProjectContributionResponse` | dead |
| `AssessProjectArrearsUseCase` (contribution-shortfall + month-advance) | debt now comes only from overspend, in settlement |
| `GroupDebtAssessmentWorker` (00:30 job) | freeze/unfreeze folded into settlement |
| `Category.PROJECT_REFUND` + refund emission in abandon/dissolve | contributions never hit the ledger, so there is nothing to refund (see decisions doc §3.4) |

Decision to confirm: keep the `project_contributions` table read-only for history, or drop it.
Recommended: **drop** via Flyway migration (no production data in a capstone). Fallback: leave the
table, remove only the write/read code paths.

---

## 8. Files affected (changes, not removals)

| File | Change |
|---|---|
| `domain/models/project/Project.java` | `netSaved()`; `progressPercent()`/`totalContributed()` use net; `expire()`; allow settlement-driven `freeze`/`unfreeze` (not owner-only); remove contribution API |
| `domain/models/project/ProjectTracking.java` | cap `money_owed` on write; `frozenMonths` counter (or store on project) |
| `domain/models/project/enums/ProjectStatus.java` | add `EXPIRED` |
| `application/usecases/settlement/PerformMonthlySettlementUseCase.java` | process ACTIVE+FROZEN; net-completion; penalty cap; freeze/unfreeze; expire; sole calendar advance (per §4) |
| `domain/services/SafeSpendingService.java` | count FROZEN debt; drop terminal obligations (per §5) |
| `application/usecases/group/GetGroupProjectDetailUseCase.java` | participating-member aggregate & required target (per §6) |
| `application/usecases/group/DissolveGroupProjectUseCase.java`, `application/usecases/project/DeleteProjectUseCase.java` | drop `PROJECT_REFUND` emission; release obligation only |
| `domain/models/group/GroupProject.java` | `reduceTargetAmount` deprecated in favour of dynamic required target |
| `application/usecases/project/CreateProjectUseCase.java` | max-3 count and unique-priority check: `status == ACTIVE` -> `status in {ACTIVE, FROZEN}` (count non-terminal; see §8.1) |
| `application/usecases/group/CreateSubPersonalProjectUseCase.java` | same: count `{ACTIVE, FROZEN}` for the max-3 and priority checks |
| response DTOs (`ProjectTrackingResponse`, `ProjectDetailResponse`, `ProjectListItemResponse`, `GroupProjectMemberProgressResponse`) | expose `moneySaved`, `moneyOwed`, `netSaved`, `status` |

### 8.1 Slot/priority occupancy: count non-terminal, not just ACTIVE

Today both create paths count only `status == ACTIVE` for the max-3 limit and the unique-priority
rule (`CreateProjectUseCase:125-153`, `CreateSubPersonalProjectUseCase:108-124`). So a FROZEN
project frees a slot **and** frees its priority — meaning with 1 of 3 frozen a user can create a
3rd ACTIVE project, and freezing becomes a way to escape the limit.

In the all-auto model a FROZEN project is a goal **in debt recovery**, not a finished one.
Letting the user pile on a new savings commitment while failing an existing one is backwards.

Rule: **the max-3 limit and the unique-priority check count all non-terminal projects =
`{ACTIVE, FROZEN}`.** A slot/priority reopens only on a terminal state
(`COMPLETED` / `EXPIRED` / `ABANDONED`). Result: with 1 of 3 frozen, the user **cannot** create
another until they recover (unfreeze) or the frozen project terminates.

This pairs with the §5 safe-spending change as a two-layer guard:
- **structural**: the count limit caps live goals at 3;
- **economic**: frozen `money_owed` still suppresses safe spending, so `requireCanAfford` also
  rejects new obligations until the debt is repaid.

---

## 9. Schema / migration

- New enum value `EXPIRED` for `projects.status` (Flyway migration only if a CHECK constraint
  restricts values).
- `project_tracking`: add `frozen_months INT NOT NULL DEFAULT 0` (or track on `projects`).
- `project_contributions`: drop table (see §7 decision).
- Retire `Category.PROJECT_REFUND` once no path emits it.
- **No backfill of `money_saved`** — its meaning (the reservation) is unchanged; only the
  completion interpretation (net) changes.

---

## 10. Tunable parameters (config, not hard-coded)

| Param | Default | Meaning |
|---|---|---|
| `app.project.freeze-mult` | 3 | freeze + debt cap = mult × monthly_saving |
| `app.project.unfreeze-mult` | 1 | auto-unfreeze when debt < mult × monthly_saving |
| `app.project.max-frozen-months` | 3 | stuck-frozen months before EXPIRED |

---

## 11. Testing

- Settlement: surplus repays debt; penalty grows debt but is **capped** at `FREEZE_MULT*monthly`.
- Freeze at cap; **frozen project still repaid** from surplus; auto-unfreeze below `UNFREEZE_MULT`.
- Hysteresis: debt oscillating between 1x and 3x does not flap status every month.
- `netSaved >= target` -> COMPLETED; deadline under target -> EXPIRED; stuck-frozen N months ->
  EXPIRED + obligation released.
- Safe spending: floor property holds (`>= 20% usable`); frozen debt still suppresses; terminal
  releases.
- Calendar advances exactly once per month (regression for the old double-advance bug).
- Group: FROZEN member keeps group target unchanged; EXPIRED/ABANDONED member shrinks required
  target so the rest can complete; COMPLETED does not reopen on later unfreeze.
- Removal: contribution endpoints gone; no `PROJECT_REFUND` emitted on abandon/dissolve.

---

## 12. Suggested sequencing

1. **Core**: `netSaved` + `EXPIRED` + completion-on-net (domain + settlement).
2. **Collapse**: process ACTIVE+FROZEN in settlement; fold freeze/unfreeze in; delete
   `AssessProjectArrearsUseCase` + `GroupDebtAssessmentWorker`; sole calendar owner.
3. **Bounded debt**: penalty cap, auto-unfreeze with hysteresis, stuck-frozen -> EXPIRED.
4. **Safe spending**: count frozen debt, release terminal obligations (§5).
5. **Group**: participating-member dynamic target & aggregate (§6).
6. **Remove contributions**: endpoints, use cases, domain/persistence, `PROJECT_REFUND`; table
   migration.
7. **DTO/UI**: surface gross / debt / net / status.

Phases 1–4 deliver the correct, recoverable all-auto core; 5–6 are simplification & cleanup;
7 is presentation.

---

## 13. Open decisions to confirm before coding

1. Default values for the three tunables in §10 (3 / 1 / 3) — acceptable? --> (3/0/3)
2. Drop `project_contributions` entirely, or keep it read-only for history? (Recommend drop.) --> drop
3. Group required target: compute dynamically (recommended) vs keep mutating stored target? --> compute
4. Should EXPIRED (failed goal) notify the user / group, reusing the existing notification flow? --> notify
5. Slot/priority occupancy (§8.1): count `{ACTIVE, FROZEN}` toward max-3 and unique-priority
   (recommended — blocks new goals while one is frozen), or keep counting `ACTIVE` only? --> count ACTIVE and FROZEN

---

## 14. Implementation status

Shipped (compiles + existing tests pass, JDK 17 / Maven):
- `netSaved` + `EXPIRED` status; progress/completion are net-based (Project, DTOs).
- Settlement rewrite (`PerformMonthlySettlementUseCase`): processes ACTIVE+FROZEN, sole calendar
  owner, penalty capped at `freeze-mult × monthly`, surplus repays debt (incl. frozen) then
  auto-invests ACTIVE, freeze at cap, auto-unfreeze at `<= unfreeze-mult × monthly`,
  stuck-frozen/deadline → EXPIRED, EXPIRED notifies owner.
- Deleted `AssessProjectArrearsUseCase` + `GroupDebtAssessmentWorker` (debt now only from
  settlement; no double month-advance, no double debt-writer).
- Safe spending counts FROZEN debt, releases terminal obligations (`SafeSpendingService`).
- Group: dynamic participating-member target + net aggregate (`GetGroupProjectDetailUseCase`,
  settlement group check); `requiredTarget` exposed on the detail response.
- Slot/priority occupancy counts {ACTIVE, FROZEN} (both create use cases).
- Removed contribution write surface: 3 controller endpoints + `AddProjectContributionUseCase`,
  `ListProjectContributionsUseCase`, `GetProjectContributionSummaryUseCase`.
- Removed `PROJECT_REFUND` emission from abandon (`DeleteProjectUseCase`) and dissolve
  (`DissolveGroupProjectUseCase`) — obligation release only.
- Config tunables in `application.yml` (3 / 0 / 3); `frozen_months` column via `V16`.
- Tests: `PerformMonthlySettlementUseCaseTest` (7 cases) — surplus-repays-debt + single
  calendar advance, penalty cap + freeze, frozen-repaid + auto-unfreeze, net-completion,
  deadline-expiry + notify, stuck-frozen-expiry + notify, healthy-active no-freeze/no-notify.

Deferred (safe to do as a focused follow-up; not done to keep the build green):
- Physical teardown of the dormant contribution persistence/domain: `ProjectContribution`,
  `ContributionId`, `ProjectContributionEntity`, its mapper + repo, `Project.contributions` +
  `addContribution`, the `contributions` param on `ProjectPersistenceMapper`/`Project.rehydrate`,
  the unused contribution DTOs, and the `DROP TABLE project_contributions` migration. These are
  now unreachable (no endpoint writes them, nothing reads them for logic) but still wired, so the
  drop must be done as one coherent change incl. removing the JPA entity before the table drop
  (otherwise `ddl-auto: update` re-creates it).
- Group-level settlement tests (FROZEN keeps target, EXPIRED/ABANDONED shrinks, COMPLETED latch).
  The per-project transitions are covered by `PerformMonthlySettlementUseCaseTest`; the group
  aggregation path (`checkGroupProjectCompletion` / `GetGroupProjectDetailUseCase`) is not yet
  unit-tested.
