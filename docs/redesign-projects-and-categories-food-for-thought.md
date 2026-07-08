# Redesign — Projects & Categories: Food for Thought

> Discussion notes, not a build plan. Captures open questions and honest critique around
> two areas the redesign hasn't formally addressed yet: the **transaction categories** and
> the **project model after the settlement engine was removed**. Treat this as input for a
> later decision/planning session — nothing here is committed.
>
> Companion to `redesign-what-and-why.md` (pivots), `redesign-technical-implementation.md`
> (how), and the Phase 1/2-3 plans. Date: 2026-06-30. Branch: `feat/learnable_app`.

---

## Part 1 — Transaction Categories

### Current state

`domain/models/transaction/enums/Category.java` — one flat enum doing three different jobs:

- **User spending buckets:** `FOOD, TRANSPORTATION, CLOTHING, UTILITIES, ENTERTAINMENT,
  HEALTH, EDUCATION, SHOPPING, OTHER`
- **System bookkeeping:** `PROJECT_CONTRIBUTION` (EXPENSE on press-to-contribute),
  `PROJECT_REFUND` (INCOME on project abandon)
- **Income:** *(undefined)* — income transactions (`type=INCOME`) have no natural category
  in this list.

Stored as the enum **name string** in a `varchar` column (`t.category = CAST(:category AS
varchar)`), not an ordinal/FK. So renames are a *data* migration (backfill), not a schema change.

### Why this matters more than it looks

**Category is the unit of analysis for the entire insight engine.** z-score anomaly,
budget-pace, `RAISE_BUDGET`, and the future recurring detector are all computed *per category*.
The quality of every insight is therefore capped by the quality of the categories. They are a
**feature-engineering decision, not a UI dropdown** — but right now they're treated as the latter.

### Honest problems

1. **`OTHER` is a poison pill.** A catch-all breaks every algorithm: "your OTHER spend is 2σ
   high" is meaningless and unactionable; "raise your OTHER budget" is nonsense. Worse, users
   dump into `OTHER`, starving real categories of the `n≥3` data the analyzer needs to fire.
   → **`OTHER`/`MISC` should be explicitly excluded from insights.**

2. **System categories in the analyzed enum is a latent bug.** If the analyzer iterates all
   categories, a one-time 5M `PROJECT_CONTRIBUTION` looks like a 3σ spending anomaly → it could
   push "your PROJECT_CONTRIBUTION is unusual, raise the budget?". **Live bug risk — verify the
   analyzer filters these out of both anomaly and pace.** (`SpendingInsightAnalyzer`
   `monthlyExpenseTotalsByCategory` ~L192, `analyze` ~L61.)

3. **INCOME and EXPENSE share one category set.** A salary is `type=INCOME` but has no sensible
   category (→ `OTHER`?). The `disposable = net_income − recurring` math (Pivot A) needs clean
   income separation. "How do you categorize a paycheck?" currently has no clean answer.

4. **Granularity fights recurring detection.** Subscriptions land in `ENTERTAINMENT`/`OTHER`;
   rent in `UTILITIES`/`OTHER`. Category-level detection can't separate "5M rent every month"
   from "5M variable utilities" in the same bucket. Real recurring detection wants
   merchant/description-level signal, which isn't captured. Known ceiling.

5. **Overlap inflates variance.** `CLOTHING` ⊂ `SHOPPING`; `SHOPPING` ↔ `OTHER`. The same
   purchase lands in different buckets on different days → higher variance → a *less* sensitive
   z-score (variance is the denominator). Fuzzy categories literally weaken anomaly detection.

### A taxonomy worth considering (organized by behavioral controllability)

The grouping itself is the idea: categories tiered by how *controllable* they are makes them
**actionable**, not just descriptive. ~10–12 expense categories in three tiers the analyzer can
treat differently:

**Tier 1 — Fixed / committed** (recurring, low controllability → feed `disposable`, low nudge value)
- `HOUSING` (rent/mortgage)
- `UTILITIES` (electricity, water, internet, phone)
- `INSURANCE`
- `DEBT_REPAYMENT` (loans, credit)
- `SUBSCRIPTIONS` (streaming, SaaS, gym) — **new and high-value**: cleanest input to recurring
  detection; "review your subscriptions" is one of the few genuinely useful non-nagging nudges.

**Tier 2 — Variable / controllable** (where overspend nudges actually help)
- `FOOD` (groceries + dining; see tradeoff below)
- `TRANSPORTATION` (fuel, transit, rideshare)
- `SHOPPING` (general retail, absorbs `CLOTHING`)
- `ENTERTAINMENT`
- `HEALTH`
- `EDUCATION`

**Tier 3 — Exceptional**
- `MISC` (honest catch-all, **excluded from insights**)

**Separate enums, not mixed in:**
- `IncomeSource { SALARY, BONUS, INVESTMENT, GIFT, OTHER_INCOME }` for `type=INCOME` rows.
- `PROJECT_CONTRIBUTION` / `PROJECT_REFUND` → out of `Category` entirely; they're a *system
  transaction kind* (e.g. a `TransactionKind` enum or `systemGenerated` flag), not a spending
  category.

**The one real tradeoff — FOOD:** splitting into `GROCERIES` (planned) vs `DINING_OUT`
(discretionary/impulse) is analytically much stronger ("your dining-out is 2σ up" is far more
actionable) but raises user categorization burden. For a capstone, keep `FOOD` merged and note
the split as defended future work — unless dining-out behavior is central to the demo.

### Code impact (if pursued)

Because category is stored as the enum **name string**, the type system stays simple but there's
a data-backfill concern.

| Area | File(s) | Change |
|---|---|---|
| The enum | `domain/models/transaction/enums/Category.java` | Rewrite values; remove `PROJECT_*` and income usage |
| System tx kind | `Transaction.java` (+ entity/mapper/migration) | New `TransactionKind`/`systemGenerated` to replace `PROJECT_*` as categories |
| Income classification | `Transaction.java` (+ entity/mapper) | Optional nullable `IncomeSource`; expense rows leave null |
| **Analyzer exclusion** | `SpendingInsightAnalyzer.java` (~L61, ~L192) | **Critical / latent bug fix** — skip `MISC` + system kinds so they never generate insights |
| i18n keys | analyzer `code()` (~L239) | Keys derive from `category.name().toLowerCase()` → renames change `insight.*.<cat>` keys; FE i18n must match |
| Budget uniqueness | `Budget.java`, `BudgetEntity`, `JpaBudgetRepository` | `UNIQUE(user_id, category, month, year)` holds; new categories just become valid budget targets |
| Analytics | `GetTransactionAnalyticsUseCase` (~L178) | Reads category as string — works unchanged; test assumptions update |
| Suggestion dedup | `Suggestion.java` dedupKey (`"RAISE_BUDGET:FOOD:2026-07"`) | Built from category name; old PENDING rows with retired names orphan (acceptable, they expire) |

**Migration reality:** existing rows hold old strings like `"CLOTHING"`. With `EnumType.STRING`,
Hibernate throws on read if it can't map the constant. So a **data-backfill migration** is needed:

```sql
UPDATE transactions SET category = 'SHOPPING' WHERE category = 'CLOTHING';
UPDATE transactions SET category = 'MISC'     WHERE category = 'OTHER';
UPDATE budgets      SET category = 'SHOPPING' WHERE category = 'CLOTHING';
-- ...etc
```
Data migration, not schema churn — the upside of storing names.

### Minimal, capstone-appropriate recommendation

Don't gold-plate. The two changes that actually move the thesis forward:
1. **Pull system categories (`PROJECT_*`) out of `Category`** and **exclude `MISC` from
   insights** — fixes a latent analyzer bug; small.
2. **Add `SUBSCRIPTIONS`** (and ideally `HOUSING`) — directly fuels recurring detection and
   `disposable`, the Phase-2 intelligence story.

Behavioral-tier grouping + income/food splits are nice-to-have and great to *write up* as design
rationale, but optional. Minimal version ≈ an hour + one backfill migration; full taxonomy ≈
half a day + more test churn.

**Open question to resolve first:** does `SpendingInsightAnalyzer` already exclude `OTHER` and the
`PROJECT_*` categories? That's the one item that's a *live bug* rather than a preference.

---

## Part 2 — The Project Model After Settlement

### Current state — a transitional "deprecate-don't-delete" mess

The press-to-contribute path is clean and working, but a whole layer of dead settlement
machinery is still bolted onto the aggregate. A reader can't tell which model is real.

**Live and healthy:**
- `totalContributed()` sums the `contributions` list — single source of truth for progress.
- `addContribution()` → atomic real EXPENSE transaction (`AddProjectContributionUseCase`).
- `complete()` fires synchronously at target; `expireIfDeadlinePassed()` is lazy-on-read. No cron.
- Owner actions `freeze` / `unfreeze` / `abandon` — legitimate, user-driven.

**Vestigial (dead weight still in the class/tables):**
- **`ProjectTracking`** (`moneySaved`, `monthlySaving`, `moneyOwed`, `recalibrate()`) — a whole
  1:1 entity + table + repo that fed the settlement engine. Progress no longer reads it
  (`netSavedAmount()` is now just an alias for `totalContributed()`), yet it's still persisted
  and mapped. Tracking numbers nobody reads.
- **`systemFreeze()` / `systemUnfreeze()`** — explicitly comment "bypass owner check because
  initiated by the settlement engine." Engine is disabled → **zero callers**. Also an auth-bypass
  smell.
- **`ProjectHistory`** (`moneySavedBefore/After`, `OVERSPENT/DISCIPLINED`) — only the settlement
  cron wrote these snapshots. Orphaned.
- **`SettlementEngine`**, **`PerformMonthlySettlementUseCase`** (no-op),
  **`MonthlySettlementScheduler`** (`@ConditionalOnProperty` off) — dormant.
- **`FROZEN`** status — still reachable via owner `freeze()`, but its original meaning
  (debt-triggered auto-freeze) is gone; now just means "owner paused it."

Net: the **behavior** is press-to-contribute, but the **data model still speaks the entire
savings-discipline vocabulary** (saved/owed/monthly/history) as if settlement were running.

### What purpose does `priority` serve? — Honestly, none right now

Traced every usage:
- Stored on `Project`, defaults `MEDIUM`, CRUD via `updatePriority()`.
- Used as a **list filter** in `JpaProjectRepository` (`:priority IS NULL OR p.priority = :priority`).
- Read once in `UserFinancialDataService` (~L156) — the **commented-out/deprecated** AI-prompt builder.

**Nothing consumes priority to make a decision.** In the old settlement world it plausibly had
teeth (auto-allocating saved money across projects → priority decides who's funded first).
Press-to-contribute killed that role: the *user* now manually chooses where money goes, so the
system never ranks projects on their behalf. Priority degraded from a decision input to a
**cosmetic label + filter**.

### Two paths for priority

**Option A — give it a real job (the interesting, thesis-consistent path).** Priority is exactly
a "preference, not fact" signal, like the 3 user traits. Wire it into the suggestion layer:
- `CONTRIBUTE_TO_PROJECT` (Phase 2): when surplus exists, **rank candidate projects by priority**
  — suggest funding the HIGH one first.
- Behind-pace nudges: a HIGH-priority laggard nudges harder than a LOW one.
- `ProjectBudgetAdvisorUseCase` could weight its saving plan by priority.

This makes priority *functional* — same principle as replacing the inert 9-field profile with 3
working traits. Something to write about.

**Option B — be honest and demote it.** If unused, keep it purely as a UI sort/filter label and
*say so*. Still valid (users like tagging importance) but acknowledge it's organizational, not
behavioral.

### Cleanup worth doing regardless (the settlement vestige)

1. **Decide `ProjectTracking`'s fate.** Biggest confusion source. Either (a) formally deprecate
   and stop persisting, or (b) **repurpose** — `monthlySaving` is actually useful now (it's what
   `ProjectBudgetAdvisorUseCase` computes as suggested pace). Keep `monthlySaving` as "advisor's
   recommended pace", drop `moneySaved`/`moneyOwed`. A clean reframe beats dead weight.
2. **Delete `systemFreeze()` / `systemUnfreeze()`** — zero callers + owner-check bypass (auth
   smell). Safe removal, shrinks the security surface.
3. **Reconsider `FROZEN`** — keep only if "owner pauses a goal" is a wanted feature; otherwise
   `ABANDONED` already covers stopping.
4. **`ProjectHistory`** — repurpose for contribution history/audit (useful for a progress chart)
   or deprecate. Today it records settlement events that never happen.

### Capstone recommendation

- Do **Option A** for priority — small wire-up, strengthens the narrative, makes the field honest.
- At minimum **delete `systemFreeze/Unfreeze`** and clearly mark `ProjectTracking` / `ProjectHistory`
  as deprecated-pending-removal.
- Full table teardown can wait for the "destructive migrations" already deferred in the phase
  plans — but the dead *methods* should go now (confusing + auth-bypass).

**Open question to resolve first:** is `ProjectTracking` still *written* on the contribute path
(actively-maintained dead data) or already inert? That decides whether deprecating it is a
one-line comment or needs unwiring.

---

## Part 3 — The Budget Model

### Current state

`domain/models/budget/Budget.java` — a per-month, per-category cap:
`(userId, category, amountLimit, spent, month, year)` with `UNIQUE(user_id, category, month, year)`.
Has real domain logic that the analyzer consumes: `AlertLevel` (SAFE/CAUTION/WARNING/EXCEEDED at
50/80/100%), `calculateProgressPercent`, `calculateRemaining`. That part is healthy and is exactly
what `SpendingInsightAnalyzer`'s budget-pace method reads.

### The serious bug — `spent` drifts (and it poisons the insight engine)

`spent` is a **denormalized cached running total**, maintained in **exactly one place** —
`CreateTransactionUseCase` (~L62):

```java
budget.updateSpent(currentSpent.add(amount.getValue()));
```

Verified: `updateSpent` has **no other callers**; nothing recomputes `spent`; and
`UpdateTransactionUseCase` / `DeleteTransactionUseCase` **never touch the budget**. Consequences
over a single normal month:
- Edit a 500k expense down to 50k → budget still shows 500k spent.
- Delete an expense → `spent` never decreases.
- Change a transaction's category/date → old budget stays inflated, new one never credited.

**Why this is not just a tracker bug:** the budget-pace insight reads `budget.getSpent()`
(`SpendingInsightAnalyzer` ~L131). A drifted `spent` produces false "you'll exceed your budget"
projections **and** a `RAISE_BUDGET` suggestion computed off a wrong number — the system
confidently asks the user to raise a budget for spending that didn't happen. This directly
undermines the "honest, deterministic, verifiable" claim the whole redesign rests on.

**Fix is architectural, not a patch:**
- **(A) Derive `spent` on read** — `SUM(transactions WHERE category, month, year)` — and stop
  storing it. Always correct; slightly more query cost (trivial at capstone scale). **Recommended**
  — it's the same philosophy already applied to `Project.totalContributed()` (derive from
  contributions, don't cache). One source of truth, can't drift.
- **(B) Maintain it transactionally in all three** create/update/delete paths. Faster reads, but
  back to keeping a cache honest — the exact trap settlement fell into.

### Design smells (less urgent, but telling)

1. **Two sources of truth for `spent`.** `spent` is a *field*, yet `calculateRemaining(spent)`,
   `calculateProgressPercent(spent)`, and `getAlertLevel(spent)` all take `spent` as a
   **parameter** — callers can pass a different number than the stored field. Pick one. (If option
   A, these parameterized methods become the *right* design: pass the computed sum, drop the field.)
2. **`@Setter` on everything + float money math.** Lombok `@Setter` exposes
   `setSpent`/`setAmountLimit` publicly, bypassing the validation in `updateSpent`/
   `updateAmountLimit`. And `calculateProgressPercent` does `spent.doubleValue() /
   amountLimit.doubleValue()` — float math on money, inconsistent with the `Money`/`BigDecimal`
   discipline in the project aggregate.
3. **`PROJECT_CONTRIBUTION` vs budgets.** Contributions are real EXPENSE transactions. If `spent`
   is computed from the ledger (option A), `PROJECT_CONTRIBUTION` **must be excluded** from category
   spend or saving money looks like overspending. Same exclusion list the analyzer needs — ties
   straight back to Part 1.

### The "not smart" gap (same theme as recurring transactions)

Budgets are **monthly and manual** — `(category, month, year)`, no rollover, no recurring budget.
So **every month the user must recreate all budgets from scratch**, or the analyzer has nothing to
pace against. This quietly **starves the insight engine**: no budget → no `BUDGET_PACE` insight →
no `RAISE_BUDGET` suggestion. Fixes that fit existing primitives:
- **Auto-roll budgets forward** (copy last month's, optionally seeded at the trailing-3-month
  median the analyzer already computes), or
- a **"draft my budgets" suggestion** (`CREATE_BULK_BUDGETS` cold-start action from the catalog)
  that proposes budgets from observed spending.

Either turns budget creation from a monthly chore into something the intelligence layer does *for*
the user — the product thesis in miniature.

### Honest priority order

1. **Fix `spent` drift** (derive-on-read, option A). Correctness bug that poisons the insight
   engine — non-negotiable for the thesis to hold.
2. **Resolve the two-sources-of-truth** method signatures (falls out of #1).
3. **Exclude `PROJECT_CONTRIBUTION`** from budget spend (ties to the Part 1 category cleanup).
4. **Budget rollover / draft-budgets suggestion** — the "make it smart" win; Phase-2-appropriate.
5. Cosmetic: drop public `@Setter`, use `BigDecimal` division.

---

## Cross-cutting theme

All three areas show the same pattern: a field/model that was **functional under settlement** has
become **decoration under press-to-contribute** (priority, `ProjectTracking`) — or was **never
functional**, only descriptive (`OTHER`, fuzzy categories) — or is **silently wrong** in a way
that corrupts the new intelligence layer (`spent` drift). The redesign's north star is "every
field is a real engine knob" (the 9-field-profile → 3-trait move). Applying that same test to
categories, projects, and budgets is the through-line: **make each one functional and correct, or
be honest that it's just a label.** And wherever a number feeds the analyzer (`spent`,
category totals, contributions), it must be **derived from the ledger, not cached** — the one
discipline that keeps the "deterministic and verifiable" thesis defensible.
