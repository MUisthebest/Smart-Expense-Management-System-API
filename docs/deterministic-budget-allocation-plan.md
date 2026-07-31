# Deterministic Budget Allocation API — Implementation Plan

> Status: **Implemented** (2026-07-11) — all listed files created/changed, tests green.
> Author: engineering
> Scope: a pure-code, synchronous budget allocation endpoint that replaces the LLM
> round-trip for the "how should I split my money across categories this month?" question.

---

## 1. Motivation

The current allocation flow (`GenerateBudgetAllocationPlanUseCase` →
`POST /api/v1/ai/budget/generate`) does **not** allocate anything in Java. It serializes a
feature payload (`safe_spending`, traits, per-category spend ratios/averages/stddevs) onto a
Redis stream with duty `BUDGET_ALLOCATION_PLAN`, and an external AI worker returns a
`BUDGET_ALLOCATION_RESULT` asynchronously over WebSocket (`ResultAIWorker`).

Every input the model receives is already computed deterministically in Java, and the task
itself — distribute a spendable envelope across ~9 fixed categories using historical
proportions and user traits — is arithmetic. A pure-code implementation is therefore:

- **Deterministic & unit-testable** (like the existing `SpendingInsightAnalyzer`).
- **Synchronous** — no queue, no WebSocket round-trip, instant response.
- **Free** — no per-call model cost.
- **More robust on totals** — no hallucinated numbers, no rounding drift that
  `ResultAIWorker` currently has to defensively re-parse from a JSON string.

This endpoint is designed to **coexist** with the AI endpoint, not necessarily replace it.

---

## 2. Architecture

Mirrors the established "pure domain service + Spring use case" pattern already used by
`SpendingInsightAnalyzer` / `GenerateInsightsUseCase`.

```
BudgetAllocationCalculator        pure domain service — all math, no Spring, fully unit-testable
        ▲
ComputeBudgetAllocationUseCase    @Service — loads data from repos, calls analyzer helpers + calculator
        ▲
BudgetAllocationController        POST /api/v1/budgets/allocation/compute  (synchronous)
```

The pure calculator takes primitives in and returns a plan out, so all repository/I/O work
stays in the use case — exactly how `SpendingInsightAnalyzer` stays Spring-free and
`GenerateInsightsUseCase` does the loading.

---

## 3. The algorithm

### 3.0 Core decomposition

Budgeting is two independent questions kept separate so each is testable alone:

1. **How much can I spend?** — a finance function of income, project commitments, and the
   savings target (the *envelope*).
2. **In what proportions?** — a statistics function of past behaviour (the *weights*).

```
envelope = spendableEnvelope(user, projects)
weights  = categoryWeights(history, user)
plan     = distribute(envelope, weights, history)
plan     = guard(plan, history, month)
```

### 3.1 Stage 0 — Spendable envelope

```
savingCapacity = user.getSavingCapacity()                 // income × {0.10 | 0.20 | 0.35}
targetSavings  = max(0, savingCapacity − projectCommitment)
envelope       = safeSpending − targetSavings             // safeSpending = income − projectCommitment
if envelope <= 0 → over-committed plan (all-zero limits + overCommitted flag)
```

**Why `max(0, savingCapacity − projectCommitment)`?** `safeSpending` already subtracts
project commitments, and `savingCapacity` is a *separate* trait-based target off gross income.
Naively doing `safeSpending − savingCapacity` deducts savings twice for anyone who commits to
projects. Because projects in this app are press-to-contribute **savings goals**, project
contributions should *count toward* the savings ambition. This yields the clean identity
`envelope = income × (1 − rate)` when commitments are within the savings target, and
`envelope = income − commitment` when they already exceed it.

`savingCapacity` rates come straight from `User.getSavingCapacity()` — do not re-derive them:

| Trait                                          | Rate |
|------------------------------------------------|------|
| `RELAXED` savingPace or `TRACK_ONLY` focus     | 0.10 |
| `AGGRESIVE` savingPace or `SAVE_MORE` focus    | 0.35 |
| otherwise (`BALANCED`)                          | 0.20 |

### 3.2 Stage 1 — Category weights

For each of the **9 expense categories only** (`FOOD, TRANSPORTATION, CLOTHING, UTILITIES,
ENTERTAINMENT, HEALTH, EDUCATION, SHOPPING, OTHER` — income/system categories such as
`SALARY`, `PROJECT_CONTRIBUTION`, `PROJECT_REFUND` are excluded):

```
median_c    = analyzer.trailing3MonthMedian(history, c, month)
recurring_c = analyzer.recurringCostByCategory(history, month).get(c)   // new helper (§4.1)
variable_c  = max(0, median_c − recurring_c)
```

**Why these three choices:**

1. **Expense categories only.** The `Category` enum mixes expense, income, and system values;
   iterating `Category.values()` blindly would hand a budget to "salary."
2. **Trailing median, not the mean-ratio the AI receives.** A single large one-off drags a
   mean; the analyzer already treats the trailing median as its robust resolved value.
3. **Split each category into fixed floor + variable part.** The median already *includes*
   recurring spend, so a category that is 100% subscriptions has `variable_c ≈ 0`.
   Distributing on `variable_c` prevents fixed obligations from soaking up discretionary
   headroom they will never use.

### 3.3 Stage 2 — Distribute (Philosophy B: needs-based, surplus → savings)

```
buffer   = { HARD: 0.05, GENTLE: 0.10, NOTIFY: 0.20 }[interventionLevel]
target_c = recurring_c + variable_c × (1 + buffer)

if Σtarget ≤ envelope:                       // user has room
    alloc_c         = target_c
    surplusToSavings = envelope − Σtarget     // → savings / autoInvestSurplus, NOT a bigger budget
else:                                        // must tighten
    alloc_c = target_c × (envelope / Σtarget) // scale everything down proportionally to fit
```

**Why Philosophy B over "fill the envelope" (Philosophy A).** Philosophy A always spreads the
entire envelope across categories, inflating budgets above real needs whenever there is
headroom (a 5M food habit becomes an 8.5M budget purely because money was left over — textbook
lifestyle creep). For a **savings** product the correct default is to cap each category near
its historical need (median + a small variance buffer) and route the leftover to savings — a
destination this app already has (`autoInvestSurplus`, settlement, projects). The `else` branch
(scale-down) is the genuinely valuable case: it turns "you want to spend more than you can"
into concrete per-category limits.

`interventionLevel` sets the buffer — a high-intervention user gets a tighter band. This is a
more honest use of the trait than letting a model vaguely "consider" it.

### 3.4 Stage 3 — Guards (all regimes)

```
alloc_c = max(alloc_c, analyzer.currentMonthTotal(history, c, month))   // never born over-limit
round(alloc_c)
reconcile rounding remainder into the largest category so Σ is exact
```

**Why the `currentMonthTotal` floor.** This is the mid-month invocation guard (see §6). Without
it, a plan computed late in the month could set a limit below what is already spent, so the
budget would be born already-exceeded the moment `spent` is backfilled — an instant false
overspend alert on a brand-new budget.

**Floor overage claw-back.** Floors only expand, so they can push Σ past the envelope. The
overage is clawed back proportionally from the other categories' unspent headroom (never below
their own spent floors). `TRACK_ONLY` users opt out of the squeeze: their savings ambition
absorbs the overage first, and categories shrink only for what savings can't cover. Whatever
neither can absorb is reported as `savingsShortfall` for the caller to fund from
`targetSavings`.

**Floored-category forward allowance (top-up).** A floored category would otherwise be born
fully consumed (`limit == spent`, zero headroom for the rest of the month). Each floored
category is therefore granted a forward allowance of `buffer × max(spent, median)`, funded in
order: (1) the envelope's unallocated surplus, (2) the other categories' headroom (same
proportional squeeze), (3) the savings ambition (reported via `savingsShortfall`).
`TRACK_ONLY` users are never squeezed for this; and if they also have **no active project**,
their surplus is left untouched too — the allowance is billed to the savings ambition alone
(which no project has earmarked yet), so `surplusToSavings` is never reduced for them. This is
why the calculator receives `hasActiveProjects`.

### 3.5 Cold start (no usable history)

```
if monthsOfHistory == 0:
    variable_c = defaultTemplate[c] × focusTilt(user)   // fixed trait-tilted proportions
    then fill the envelope (Philosophy A) — there is no "need" baseline to cap against
```

Needs-based capping requires history to define "needs." With none, fall back to a trait-tilted
template that fills the envelope — which is exactly what the current use case does today when it
sends no `spendingHistory`. The two regimes hand off cleanly once ~3 months of data exist.

Starting default template (proportions across the 9 expense categories, tuned later):

| FOOD | TRANSPORTATION | UTILITIES | SHOPPING | ENTERTAINMENT | HEALTH | EDUCATION | CLOTHING | OTHER |
|------|----------------|-----------|----------|---------------|--------|-----------|----------|-------|
| 0.30 | 0.12           | 0.12      | 0.10     | 0.08          | 0.08   | 0.08      | 0.07     | 0.05  |

### 3.6 Worked example

`income 20M`, `BALANCED` (rate 0.20 → capacity 4M), `projectCommitment 3M`:

- `targetSavings = max(0, 4M − 3M) = 1M` → `envelope = 17M − 1M = 16M`
- recurring: `UTILITIES 2M`, `ENTERTAINMENT 0.3M`
- medians: `FOOD 5M, TRANSPORT 1.5M, SHOPPING 1M, HEALTH 0.5M, UTILITIES 2M, ENT 0.3M`
- variable: `FOOD 5M, TRANSPORT 1.5M, SHOPPING 1M, HEALTH 0.5M, UTILITIES 0, ENT 0`

With `GENTLE` buffer 0.10, `Σtarget ≈ 2.3M + 8M×1.1 = 11.1M ≤ 16M` → allocate targets,
`surplusToSavings ≈ 4.9M`. (Philosophy A would instead inflate FOOD to ~8.5M — the creep B
avoids.)

---

## 4. Files to create / change

### 4.1 `SpendingInsightAnalyzer.java` — add one public helper

`estimatedMonthlyRecurringCost` returns a total only; the floor needs **per-category** recurring
cost. Add a thin wrapper over the existing private `findRecurringCharges(...)` (which already
carries `category`):

```java
public Map<Category, BigDecimal> recurringCostByCategory(List<Transaction> history, YearMonth period)
```

Keeps all recurring logic in one place. Add a focused unit test.

### 4.2 `BudgetAllocationCalculator.java` (new pure domain service)

- No Spring, no I/O. `BudgetAllocationPlan allocate(BudgetAllocationInputs inputs)`.
- `BudgetAllocationInputs` (record): `envelope`, `Map<Category,BigDecimal> median`,
  `recurring`, `currentMonthSpent`, `Focus_Mode focusMode`, `Intervention_Level interventionLevel`,
  `int monthsOfHistory`.
- Implements §3.1–§3.5. Pure primitives in, plan out.

### 4.3 `ComputeBudgetAllocationUseCase.java` (new `@Service`)

Combines the data-loading of `GenerateInsightsUseCase` with the envelope inputs of
`GenerateBudgetAllocationPlanUseCase`:

- `validateUserSetup` via `user.hasCompletedFinancialSetup()` → 400 if incomplete.
- `activeProjectCommitment` via `projectRepository` (lift the existing helper).
- `safeSpending = user.calculateSafeSpending(commitment)`; `savingCapacity = user.getSavingCapacity()`.
- Load trailing history (`TRAILING_WINDOW` months → now); build per-category `median`
  (`trailing3MonthMedian`), `recurring` (new helper), `currentMonthSpent` (`currentMonthTotal`).
- Call the calculator; map to the response DTO.
- `@Transactional(readOnly = true)` + a deterministic `execute(userId, asOf)` overload for tests.

### 4.4 DTO — `BudgetAllocationPlanResponse`

```
month, year, income, safeSpending, targetSavings, envelope,
surplusToSavings, coldStart, overCommitted,
List<CategoryAllocation> { category, amount, recurring, basis (HISTORY | TEMPLATE | FLOORED) }
```

Returned inside the existing `CheckResponse<T>` wrapper.

### 4.5 `BudgetAllocationController.java` (new)

`POST /api/v1/budgets/allocation/compute` → `CheckResponse<BudgetAllocationPlanResponse>`.
Same `actorId(auth)` pattern as `BudgetAIController`.

### 4.6 `UpsertBulkBudgetsUseCase.java` — spent backfill (Option A, see §6)

Add spent-backfill for **newly created** budgets, mirroring
`CreateBudgetUseCase.spentThisMonth(...)`: sum EXPENSE for the budget's category over
`[month day 1, month end]` and `budget.updateSpent(spent)` before save. Existing budgets keep
their live `spent`. Also populate the response `spent`/`remaining`/`progressPercent`/`alertLevel`
from the real figure instead of the current hard-coded `ZERO`.

---

## 5. Tests

- **`BudgetAllocationCalculatorTest`** (pure, table-driven like `SpendingInsightAnalyzerTest`):
  envelope math; projects-as-savings identity; under-envelope (surplus) vs over-envelope
  (scale-down) branches; mid-month `currentMonthSpent` floor; cold-start template; over-committed
  (envelope ≤ 0); rounding reconciliation (Σ exact).
- **`ComputeBudgetAllocationUseCaseTest`** — mocked repos, deterministic `asOf`.
- **`SpendingInsightAnalyzerTest`** — new case for `recurringCostByCategory`.
- **`UpsertBulkBudgetsUseCaseTest`** — new-budget spent backfill; existing-budget spent preserved.

---

## 6. Mid-month invocation & the `spent` field

`spent` is **not** part of the allocation — the calculator only produces full-month **limits**,
using prior completed months only (date-of-invocation independent, so the same user gets the
same plan regardless of which day they click). `spent` is populated separately when a budget is
actually created.

Today only single `CreateBudgetUseCase` backfills `spent` (from day 1 → month end of the current
month); the bulk apply path `UpsertBulkBudgetsUseCase` sets `spent = ZERO`. So a plan applied
mid-month via bulk would ignore money already spent this month.

**Decision: Option A (adopted).** Add spent-backfill to `UpsertBulkBudgetsUseCase` (§4.6) so
applying a mid-month plan reflects real spend. Combined with the Stage-3 `currentMonthTotal`
floor, this guarantees a mid-month budget is never born already-exceeded and its `spent` counter
is correct from creation. This also fixes an existing inconsistency between the single and bulk
create paths.

---

## 7. Baked-in recommendations

- **Philosophy B** (cap to needs, surplus → savings) for established users; template-fill only
  at cold start.
- **Projects count as savings** (`targetSavings = max(0, savingCapacity − commitment)`).
- **Pure trailing median** weights; **full-month** limits; mid-month handled solely by the
  `currentMonthTotal` floor + Option A backfill.

## 8. Open tuning knobs (non-blocking, revisit after v1)

- Recency blend: pure 3-month median vs `0.5·last_1m + 0.5·median` (faster adaptation, more noise).
- Cold-start template proportions and `focusTilt` weighting.
- `interventionLevel` → buffer mapping (currently 0.05 / 0.10 / 0.20).
- Whether `surplusToSavings` should auto-feed `autoInvestSurplus` or only be surfaced as a
  suggestion.
```
