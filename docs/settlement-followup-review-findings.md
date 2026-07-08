# Settlement + Insight Follow-up — Code Review Findings

> **STATUS (2026-07-07): ALL 5 FINDINGS FIXED — full suite green (366 tests).**
> - **1 (offTrackInsight):** the project-health loop now re-fetches each project via
>   `projectRepository.findById(stub.getId())` so contributions (and `remaining`) are loaded before
>   `offTrackInsight` judges pace. Regression test `offTrackUsesRefetchedContributions`.
> - **2 (frozen never settled):** `execute()` now loads `findAllByStatus(ACTIVE)` ∪ `findAllByStatus(FROZEN)`.
> - **3 (complete vs expire):** completion is now gated on `funded` (`newSaved >= target`); a
>   horizon-elapsed (`monthLeft <= 0`) under-target project is marked EXPIRED (+notify), not COMPLETED.
>   Regression test `horizonElapsedUnderTargetExpires`.
> - **4 (rebalance rollback):** `rebalanceProjects` now pre-checks `projectRepository.findById(...)`
>   and skips a since-deleted project (a try/catch wouldn't work — `UpdateProjectUseCase` is
>   `@Transactional`, so its throw marks the shared tx rollback-only). Regression test
>   `rebalanceProjectsSkipsDeletedProject`.
> - **5 (dup filter/sum):** the ACTIVE-candidate filter is computed once in `maybeProduceProjectHealth`
>   and passed to both `overCommittedInsight` and `rebalanceProjectsCandidate`.
>
> Original findings preserved below for context.

---



> Recall-biased review of the working-tree diff after implementing the settlement revival
> (`docs/settlement-revival-plan.md`) and the insight/suggestion follow-up
> (`docs/settlement-insights-followup.md`). The revival half was reviewed and fixed inline earlier;
> these five findings came from reviewing the **insight/suggestion follow-up** surface (items 0, 0b,
> 1, 3, 4, 6). Findings **1 and 2 are pre-existing** but sit in code the follow-up modifies and
> undercut its real-world behaviour, so fix them before flipping `SETTLEMENT_ENABLED=true` or relying
> on the project-health pass.
>
> Full suite was green (363 tests) at review time — none of these are caught by existing tests
> (several are masked by mocks, see each finding).

---

## 1. `offTrackInsight` reads contributions that `findAllByUser` never loads → spurious CRITICAL nudges — **HIGH**

**Where:** `application/usecases/insight/SuggestionProducer.java` — `offTrackInsight(...)`, the
`remaining` (`~:692`) and `observedTotal` (`~:711`) computations; also reused by
`increaseContributionCandidate` (`~:771`). Root cause in
`infrastructure/persistence/repositories/ProjectRepositoryImpl.java:84`.

**Bug:** `maybeProduceProjectHealth` loads projects via `projectRepository.findAllByUser(...)`, which
calls `toDomain(p, null, tracking)` — **contributions are `null` → an empty list**. `offTrackInsight`
then computes:
- `remaining = project.remaining().amount()` = `target − Σcontributions` = `target` (nothing loaded),
- `observedTotal = project.getContributions()...` = `0`.

So for any ACTIVE, non-subpersonal project with a future deadline older than one month:
`requiredMonthly = target/monthsLeft > 0`, `observedMonthly = 0` → off-track, and
`observedMonthly == 0` → **CRITICAL "stalled"**. The `project-health` cron is enabled by default
(`app.project-health.enabled: true`).

**Failure scenario:** a GENTLE user with a normally-funded active project gets a CRITICAL
`INCREASE_CONTRIBUTION` push every week, sized off the full target. Item 3's new arrears path is also
affected — its catch-up amount is derived from the same overstated `remaining`.

**Why tests miss it:** every project-health test mocks `findAllByUser` to return projects
*with* contributions (`savingProject(...)` rehydrates a contributions list), so the null-load path is
never exercised.

**Fix direction:** in `offTrackInsight`, base progress on the loaded `tracking.getMoneySaved()`
(which item 0 now keeps settlement-synced) instead of `project.getContributions()` /
`project.remaining()`; OR make `findAllByUser` load contributions; OR re-fetch each project via
`findById` in the health pass (heavier). Prefer the tracking-based read for symmetry with item 3,
which already reads `tracking`. Then add a test that drives the real null-contributions load.

---

## 2. Settlement cron only loads ACTIVE projects → FROZEN never settles (unfreeze/expire dead in prod) — **MEDIUM (pre-existing)**

**Where:** `application/usecases/settlement/PerformMonthlySettlementUseCase.java:64` — `execute()` does
`projectRepository.findAllByStatus(ProjectStatus.ACTIVE)`.

**Bug:** only ACTIVE projects are grouped by owner and handed to `settleUserProjects`. A FROZEN
project is never in any group, so the whole `else if (p.getStatus() == FROZEN)` branch
(unfreeze-on-zero-debt, `frozenMonths++`, expire-at-max) is **unreachable via the scheduler**. Only
the admin trigger / unit tests (which pass FROZEN projects directly) exercise it.

**Failure scenario:** a project freezes one month; subsequent monthly runs never re-load it → it is
stuck FROZEN forever under the real cron and can never auto-recover or expire. This also makes the
follow-up's TRACK_ONLY-unfreeze fix and the freeze-lifecycle notifications inert in production.

**Fix direction:** load `findAllByStatus(ACTIVE)` ∪ `findAllByStatus(FROZEN)` in `execute()` (and
group both). Watch the interaction with finding 1's fix if project loading is refactored.

---

## 3. Settlement marks an underfunded project COMPLETED when `monthLeft` hits 0 — **MEDIUM (pre-existing)**

**Where:** `application/usecases/settlement/PerformMonthlySettlementUseCase.java:365` — the completion
condition `newMonthLeft <= 0 || newSaved.compareTo(target) >= 0`.

**Bug:** `SettlementEngine.recalculateMonthLeft` can decrement `monthLeft` to 0 while
`totalContributed < target`, so a project that ran out its horizon *under* target is flagged
**COMPLETED** rather than EXPIRED.

**Failure scenario:** saved 400 of 1200 with `monthLeft` counting down to 0 → COMPLETED with an 800
shortfall (and a "goal reached" notification for a goal that wasn't).

**Fix direction:** complete only on `newSaved >= target`; treat `monthLeft <= 0 && newSaved < target`
as EXPIRED (mind the existing `!frozenThisMonth && status != EXPIRED` guards already on that block).
Confirm against `SettlementEngineTest` expectations before changing.

---

## 4. `rebalanceProjects` accept rolls back the whole rebalance if any snapshot project was deleted — **MEDIUM**

**Where:** `application/usecases/suggestion/RespondToSuggestionUseCase.java:215` — the
`rebalanceProjects(...)` executor loop calling `updateProjectUseCase.execute(...)`.

**Bug:** a project deleted between ask and accept makes `UpdateProjectUseCase` throw
`PROJECT_NOT_FOUND`; inside the `@Transactional` accept that rolls back the status flip **and** every
other project's deadline change. `rebalanceBudgets` (`:169`) deliberately *skips* a since-deleted
budget instead — the two executors are inconsistent.

**Failure scenario:** user gets a 3-project rebalance card, deletes one project, then accepts → the
whole accept errors and nothing is applied, rather than rebalancing the surviving two.

**Fix direction:** skip a not-found project (pre-check via a repo, or catch the not-found
`ProjectException` per adjustment and continue) to match `rebalanceBudgets`' skip-deleted semantics.
Add a test for the deleted-project case.

---

## 5. `overCommittedInsight` and `rebalanceProjectsCandidate` duplicate the active-filter + committed-sum — **LOW (cleanup)**

**Where:** `application/usecases/insight/SuggestionProducer.java` — both methods call
`overCommitmentProjects(projects)` and re-run the `committed` reduce.

**Cost:** the same filter + reduce runs twice per over-committed fire, with drift risk if only one is
changed. The metrics map already carries `committedMonthly`; the detector could pass the computed
`active`/`committed` (or capacity) through to the candidate builder.

---

## Suggested order for the fix session
1. **Finding 1** (highest user-visible impact; small change to read `tracking.moneySaved`).
2. **Finding 2** (unblocks the frozen lifecycle; small `findAllByStatus` change).
3. **Finding 3** (completion-vs-expire correctness; verify against `SettlementEngineTest`).
4. **Finding 4** (executor parity with rebalance-budgets).
5. **Finding 5** (cleanup, fold into whichever of the above touches `SuggestionProducer`).

Each fix should land with a test that exercises the real path (findings 1 & 4 are currently masked by
mocks / happy-path snapshots).
