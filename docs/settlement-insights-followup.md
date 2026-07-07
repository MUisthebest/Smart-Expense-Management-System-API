# Post-Revival: Insight & Suggestion Follow-ups

> Companion to [`settlement-revival-plan.md`](./settlement-revival-plan.md). That plan revives the
> monthly settlement engine on the income+traits model. This doc covers what changes in the
> **insight/suggestion system** once settlement is live again — the two systems were built while
> settlement was *off* (Pivot B, press-to-contribute), so reviving it re-introduces overlaps that
> must be reconciled.
>
> **Do the revival plan first; this is the immediately-after work.** Nothing here is optional in the
> long run — item 0 in particular is a correctness blocker, not a nice-to-have.

---

> **STATUS (2026-07-07): Items 0 (+0a/0b), 1, 3, 4-OVER_COMMITTED, 5, and 6 DONE/reconciled — full
> suite green (363 tests).** Remaining: item 4's settlement-recap fold (minor) and item 2's full
> capacity unification (deliberately deferred — see below). Item 5/6 disposition:
>
> **Item 6 (done):** TRACK_ONLY now softens the manual save/contribute nudges — a new
> `suppressContributionNudges(user)` gate (`focusMode == TRACK_ONLY || settlementFundsProjectsFor`)
> skips CONTRIBUTE_TO_PROJECT and INCREASE_CONTRIBUTION, keeping trait interpretation consistent with
> settlement (which never penalizes TRACK_ONLY). OVER_COMMITTED is exempt (rebalancing down fits
> passive tracking). The **one-project-one-message** seam was already closed by item 3: `offTrackInsight`
> is ACTIVE-only, and settlement only notifies on FROZEN/EXPIRED/COMPLETED — so a frozen/expired project
> never also gets a suggestion. The **NOTIFY-gets-EXPIRED-but-no-suggestions** asymmetry is by design:
> settlement notifications are out-of-band real-money-state alerts (always delivered), suggestions are
> opt-in nudges gated by `interventionLevel`.
>
> **Item 5 (reconciled):** the recap already runs *after* settlement — settlement cron `0 0 0 1 * *`
> (00:00) precedes recap `0 0 9 1 * *` (09:00) on the 1st, so the recap reflects settled state with no
> code change. **Deferred:** routing the soft COMPLETED settlement ping through the suggestion daily
> cap (cross-system plumbing; low value while settlement is off by default and COMPLETED is already
> HARD-only). FROZEN/EXPIRED are correctly privileged (always delivered).
>
> **Item 2 (partially done, rest deferred):** `User.getSavingCapacity()` is the chosen authoritative
> capacity and is now used consistently by settlement's Step-1 cap and by OVER_COMMITTED. NOT unified:
> the CONTRIBUTE_TO_PROJECT nudge still sizes its amount from the analyzer's budget-aware
> `SAVINGS_CAPACITY.disposable × pace` — changing that basis would alter established nudge amounts and
> its P2-E tests for unclear benefit. Flag if full unification of the user-facing "you can save ~X"
> line is wanted.
>
> **Item 4 OVER_COMMITTED (done):** new `InsightType.OVER_COMMITTED` + `SuggestionType.REBALANCE_PROJECTS`.
> The project-health pass fires it (portfolio-level, BEFORE the item-1 suppression, since it's
> capacity- not funding-driven) when Σ ACTIVE-project `monthlySaving` > `User.getSavingCapacity()`.
> The actionable ask carries a per-project new-deadline snapshot (`ProjectAdjustment`) computed by
> scaling `monthLeft` up by `committed/capacity` (uses only loaded tracking fields — no dependence on
> contributions the project list doesn't load); accept applies each deadline via `UpdateProjectUseCase`
> (which recalibrates monthly saving down), all-or-nothing in the response transaction. PROJECT_IN_ARREARS
> is intentionally NOT built — item 3 already turns settlement arrears into the off-track nudge, so a
> separate arrears suggestion would double-ask the same debt.
>
> **Item 3 (done):** `offTrackInsight` now prefers the authoritative settlement arrears signal —
> when `tracking.moneyOwed > 0` the project is off-track regardless of the observed contribution rate
> (deep arrears ≥ 2× requiredMonthly, or frozenMonths > 0, escalates to CRITICAL); the pace heuristic
> is the fallback when settlement has said nothing. Metrics carry `moneyOwed` + a `source`
> (`settlement`/`heuristic`) marker. FROZEN/EXPIRED projects stay skipped (still ACTIVE-only), so this
> never double-notifies against settlement's own freeze/expire pings (item 6).
> **Item 2 decision:** `User.getSavingCapacity()` (income × pace) is the authoritative capacity figure.
> **Item 4 decision:** OVER_COMMITTED surfaces as an *actionable* suggestion (not notification-only).
>
> **Item 1 (done):** `maybeProduceSavingsContribution` (CONTRIBUTE_TO_PROJECT) and
> `maybeProduceProjectHealth` (INCREASE_CONTRIBUTION) are suppressed only when
> `settlement.enabled && user.isAutoInvestSurplus()` — a new `settlementFundsProjectsFor(user)` gate.
> The settlement-enabled half is essential: `autoInvestSurplus` defaults to true, so gating on it
> alone would silence contribute-nudges for every user while settlement is off (the default). Added a
> `SettlementProperties` (`app.settlement.enabled`) bean for the flag.
>
> Notes on what landed for item 0:
> - **0a decision:** ProjectContribution-only, **no** ledger transaction row (chosen with product).
> - **0/0a:** settlement now credits each ACTIVE project's monthly saving (+ any auto-invested
>   surplus) by appending an auto `ProjectContribution` via `Project.recordAutoSaving(...)`, not by
>   bumping `tracking.moneySaved`. Progress = sum(contributions) everywhere; `tracking.moneySaved` is
>   kept as a mirror. Surplus auto-invest now targets ACTIVE projects only (can't contribute to a
>   frozen one).
> - **0b:** settlement's income/expense sums exclude `PROJECT_CONTRIBUTION` + `PROJECT_REFUND`.
> - **Bonus (latent data-loss bug found & fixed):** `ProjectRepository.save()` treats the in-memory
>   contribution list as authoritative, and `findAllByStatus` loads none — so the revived engine would
>   have **deleted every project's contribution history** on save. Settlement now re-fetches each
>   project via `findById` (full aggregate) before crediting/saving.

## 0. Funding-model decision — **Option 2 (chosen)**

### The problem
`Project.progressPercent()` / `remaining()` / completion all read **`sum(contributions)`**, and the
code says so explicitly (`Project.java:188`): *"Settlement `tracking` (moneySaved/moneyOwed) no
longer feeds progress."* But the revived engine writes to **`tracking.moneySaved` / `moneyOwed`** and
completes projects off `moneySaved >= target`. Result: **two independent balances that never talk**:

- Settlement advances `moneySaved` monthly → the progress bar does not move.
- Settlement can flip a project to `COMPLETED` / `FROZEN` / `EXPIRED` while the UI still shows 0%.
- `moneyOwed` (debt/freeze) is a concept the contribution model has no idea exists.

### Decision: Option 2 — settlement appends a contribution, not a tracking bump
When settlement credits a project for the month, it **appends an auto-`ProjectContribution`** (an
"auto-saved this month" ledger entry) instead of bumping `tracking.moneySaved` for progress purposes.
The auto-engine and manual presses then **sum into the same progress bar** — one number, one source
of truth, full audit trail.

**Why Option 2** (over "settlement owns progress" / "keep them fully separate"): it keeps the visible
progress model the entire app already reads, preserves the ledger audit trail (every unit of progress
is a row), lets auto-saving and manual pressing coexist without double-counting, and has the smallest
blast radius on the insight system.

### Two sub-decisions Option 2 forces (both correctness issues)

**0a. The auto-contribution must NOT create an EXPENSE ledger row.**
The manual `AddProjectContributionUseCase` writes a real `EXPENSE` transaction
(`Category.PROJECT_CONTRIBUTION`). Settlement computes `actualSavings = income − expenses` from the
ledger. If the *auto* contribution also wrote an EXPENSE row, next month's `income − expenses` would
subtract it and suppress the computed surplus — the engine would eat its own tail. So the auto-saved
contribution appends a `ProjectContribution` **without** a matching EXPENSE ledger transaction (or
tags it so settlement's expense sum skips it).

**0b. Settlement's income/expense sums must EXCLUDE contribution-category transactions.**
Even for *manual* contributions today: they are `EXPENSE` rows in `Category.PROJECT_CONTRIBUTION`.
Once settlement is on, its broad `TransactionType.EXPENSE` query counts them as spending → a user who
contributes to a goal looks like they overspent → the engine accrues **debt/penalty for saving**.
Fix: settlement must filter out `PROJECT_CONTRIBUTION` (and `PROJECT_REFUND`) categories when summing
expenses. **This is a latent bug the revival introduces regardless of Option 2 — worth calling out on
its own.**

> Net effect of 0/0a/0b: progress and completion read `sum(contributions)` everywhere (unchanged);
> settlement contributes into that same list; `moneyOwed`/freeze stay tracking-only as an *arrears*
> signal (surfaced via item 4), not as "progress."

---

## Follow-ups (given settlement is live)

### 1. Gate the "contribute" nudges on `autoInvestSurplus`
`maybeProduceSavingsContribution` (INCOME → `CONTRIBUTE_TO_PROJECT`) and `maybeProduceProjectHealth`
(→ `INCREASE_CONTRIBUTION`) assume *manual* funding.
- **`autoInvestSurplus` ON** → settlement already routes surplus into projects; nudging "contribute
  more" double-dips and reads as broken. **Suppress** these, or reframe as a confirmation
  ("auto-saved X toward Goal Y this month").
- **`autoInvestSurplus` OFF** → these nudges are the *primary* funding path; keep them loud.

This is where the new `autoInvestSurplus` flag earns its keep beyond the engine.

### 2. Unify the three "how much can you save" numbers
Today the user can be shown all of:
- `SAVINGS_CAPACITY.disposable` = avgIncome − recurring − Σ budget limits (analyzer),
- settlement `actualSavings` = income − expenses,
- `User.getSavingCapacity()` = income × pace rate.

They will disagree. Drive the user-facing "you can save ~X" line from **one shared calculation** (or
explicitly reconcile which one is authoritative) so the app speaks with one voice.

### 3. Project-health should read settlement state, not re-derive it
`maybeProduceProjectHealth` computes off-track from *contribution rate vs deadline*. Settlement now
holds **authoritative** signals — `moneyOwed > 0`, `frozenMonths`, `FROZEN`/`EXPIRED`. A frozen
project is a far stronger "off track" than the heuristic, and re-deriving risks the two systems
disagreeing (and double-notifying — see item 5). Key the off-track insight off settlement tracking
when it exists; fall back to the heuristic only when there is no tracking signal.

### 4. Add the insight/suggestion types settlement unlocks
The engine produces rich new state that currently surfaces only as log lines:
- **`PROJECT_IN_ARREARS` (debt accrued)** — settlement added `moneyOwed` this month → `REDUCE_BUDGET`
  / cut spending next month.
- **`OVER_COMMITTED`** — Step 1's capacity cap *silently* drops the excess when
  `Σ monthlySaving > getSavingCapacity()`. Without an insight, the cap is invisible and the user just
  quietly under-saves. Surface it → lower a target, relax pace, or `REBALANCE`.
- **Settlement recap** — "saved X of Y goal, 1 project frozen" at month close, folded into the
  existing `GOOD_MONTH` / `CASHFLOW_NEGATIVE` recap rather than a new channel.

### 5. Coordinate notifications so the 1st of the month isn't a blast
Settlement's new notifications (revival Step 3) bypass the SuggestionProducer fatigue stack
(dedup / cooldown / daily-cap) and fire the same day as `MonthEndSweep` / `MonthlyRecap` / weekly
digest.
- **Sequence** the recap to run *after* settlement so it reflects settled state.
- Decide cap treatment: **privilege** `FROZEN`/`EXPIRED` (real-money state changes), but route the
  softer settlement notifications through the same daily cap.

### 6. Keep trait interpretation consistent across both systems
Both systems now read the same three traits for different levers. Two seams to align:
- **`TRACK_ONLY`** — settlement won't penalize; the suggestion system should likewise *soften* project
  nudges for these users, not keep pushing `INCREASE_CONTRIBUTION`.
- **`interventionLevel`** — gates *both* settlement notifications and suggestion pushes, but via
  different tables. Avoid a `HARD` user getting settlement-freeze + a suggestion about the *same*
  project, and reconcile that a `NOTIFY` user gets settlement `EXPIRED` alerts yet zero suggestions.
  One project problem → one message.

---

## Suggested order

1. **Item 0 + 0a + 0b** — funding-model correctness (blocker; ship with / immediately after revival).
2. **Item 1** — gate contribute-nudges on `autoInvestSurplus` (prevents the most visible "double
   ask" bug).
3. **Item 3 + 4** — project-health reads settlement state; add `PROJECT_IN_ARREARS` / `OVER_COMMITTED`.
4. **Item 5 + 6** — notification coordination + trait consistency (polish, but user-visible).
5. **Item 2** — unify capacity numbers (cross-cutting; can trail).

## Open questions
- **0a:** append auto-contribution with no ledger row, or a new non-expense transaction
  type/category so it is auditable but excluded from the expense sum?
- **4 (OVER_COMMITTED):** notification-only, or an actionable suggestion (which action —
  lower target / relax pace / rebalance)?
- **2:** which of the three capacity figures becomes the single authoritative "you can save ~X"?
