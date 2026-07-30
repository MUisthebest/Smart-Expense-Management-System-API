package com.example.smartmoney.domain.services;

import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure domain service that turns a spendable envelope + spending history into a per-category
 * budget plan — the deterministic, LLM-free replacement for the {@code BUDGET_ALLOCATION_PLAN}
 * AI round-trip. No Spring, no I/O: the caller ({@code ComputeBudgetAllocationUseCase}) loads the
 * data and passes primitives in, which keeps every rule here unit-testable in isolation, exactly
 * like {@link SpendingInsightAnalyzer}.
 *
 * <p>Design and reasoning: docs/deterministic-budget-allocation-plan.md §3.
 * The envelope math (Stage 0) lives in the use case because it needs the User/Project models; this
 * class implements Stage 1–3 (weights, distribute, guard) on the resulting envelope.
 */
public class BudgetAllocationCalculator {

    /** The only categories a budget is allocated to — income/system categories are excluded. */
    public static final List<Category> EXPENSE_CATEGORIES = List.of(
            Category.FOOD, Category.TRANSPORTATION, Category.CLOTHING, Category.UTILITIES,
            Category.ENTERTAINMENT, Category.HEALTH, Category.EDUCATION, Category.SHOPPING, Category.OTHER);

    /** Cold-start proportions (sum to 1.0) used when there is no usable history to weight from. */
    private static final Map<Category, Double> COLD_START_TEMPLATE;
    static {
        Map<Category, Double> template = new EnumMap<>(Category.class);
        template.put(Category.FOOD, 0.30);
        template.put(Category.TRANSPORTATION, 0.12);
        template.put(Category.UTILITIES, 0.12);
        template.put(Category.SHOPPING, 0.10);
        template.put(Category.ENTERTAINMENT, 0.08);
        template.put(Category.HEALTH, 0.08);
        template.put(Category.EDUCATION, 0.08);
        template.put(Category.CLOTHING, 0.07);
        template.put(Category.OTHER, 0.05);
        COLD_START_TEMPLATE = Collections.unmodifiableMap(template);
    }

    /** Discretionary "wants" trimmed for save-oriented users during cold start. */
    private static final Set<Category> WANTS =
            Set.of(Category.ENTERTAINMENT, Category.SHOPPING, Category.CLOTHING);
    /** Multiplier applied to WANTS weights for SAVE_MORE / REDUCE_SPENDING users at cold start. */
    private static final double WANTS_TILT = 0.70;

    /**
     * Every proposed limit is snapped to a multiple of this step (VND) so proportional scaling
     * never surfaces amounts like 2,923,076.92; the shaved remainder rolls into surplusToSavings.
     */
    private static final BigDecimal ROUNDING_STEP = new BigDecimal("10000");

    /**
     * Build a per-category plan for the given inputs. Never returns {@code null}; an envelope of
     * zero or less yields an all-zero plan flagged {@code overCommitted}.
     */
    public BudgetAllocationPlan allocate(BudgetAllocationInputs in) {
        BigDecimal envelope = in.envelope() == null ? BigDecimal.ZERO : in.envelope();

        // Stage 0 result: nothing spendable after commitments + target savings.
        if (envelope.signum() <= 0) {
            return overCommittedPlan(in);
        }

        // Cold start when there is no month of history OR the history carries no spend signal at all.
        boolean coldStart = in.monthsOfHistory() <= 0 || totalMedian(in).signum() == 0;

        Map<Category, BigDecimal> raw = coldStart
                ? allocateColdStart(envelope, in)
                : allocateFromHistory(envelope, in);

        return finalizePlan(envelope, raw, in, coldStart);
    }

    // ---- Stage 2: established users — needs-based with surplus → savings (Philosophy B) ----------

    private Map<Category, BigDecimal> allocateFromHistory(BigDecimal envelope, BudgetAllocationInputs in) {
        double buffer = bufferFor(in.interventionLevel());
        BigDecimal bufferMultiplier = BigDecimal.valueOf(1.0 + buffer);

        Map<Category, BigDecimal> target = new EnumMap<>(Category.class);
        BigDecimal sumTarget = BigDecimal.ZERO;
        for (Category c : EXPENSE_CATEGORIES) {
            BigDecimal recurring = amt(in.recurring(), c);
            BigDecimal variable = amt(in.median(), c).subtract(recurring).max(BigDecimal.ZERO);
            BigDecimal t = recurring.add(variable.multiply(bufferMultiplier));
            target.put(c, t);
            sumTarget = sumTarget.add(t);
        }

        // Enough room: fund needs (+buffer); the leftover becomes savings, not a bigger budget.
        if (sumTarget.compareTo(envelope) <= 0) {
            return target;
        }

        // Over the envelope: the user wants to spend more than they can — scale everything down to fit.
        Map<Category, BigDecimal> scaled = new EnumMap<>(Category.class);
        for (Category c : EXPENSE_CATEGORIES) {
            scaled.put(c, target.get(c).multiply(envelope).divide(sumTarget, 4, RoundingMode.HALF_UP));
        }
        return scaled;
    }

    // ---- Stage 2 (cold start): trait-tilted template that fills the envelope (Philosophy A) ------

    private Map<Category, BigDecimal> allocateColdStart(BigDecimal envelope, BudgetAllocationInputs in) {
        boolean saveOriented = in.focusMode() == Focus_Mode.SAVE_MORE
                || in.focusMode() == Focus_Mode.REDUCE_SPENDING;

        Map<Category, Double> weights = new EnumMap<>(Category.class);
        double sumWeights = 0.0;
        for (Category c : EXPENSE_CATEGORIES) {
            double w = COLD_START_TEMPLATE.getOrDefault(c, 0.0);
            if (saveOriented && WANTS.contains(c)) {
                w *= WANTS_TILT;
            }
            weights.put(c, w);
            sumWeights += w;
        }

        Map<Category, BigDecimal> alloc = new EnumMap<>(Category.class);
        for (Category c : EXPENSE_CATEGORIES) {
            double share = sumWeights > 0 ? weights.get(c) / sumWeights : 0.0;
            alloc.put(c, envelope.multiply(BigDecimal.valueOf(share)));
        }
        return alloc;
    }

    // ---- Stage 3: floors, rounding reconciliation, surplus ---------------------------------------

    private BudgetAllocationPlan finalizePlan(BigDecimal envelope, Map<Category, BigDecimal> raw,
                                              BudgetAllocationInputs in, boolean coldStart) {
        AllocationBasis baseBasis = coldStart ? AllocationBasis.TEMPLATE : AllocationBasis.HISTORY;

        Map<Category, BigDecimal> floored = new EnumMap<>(Category.class);
        Map<Category, AllocationBasis> basis = new EnumMap<>(Category.class);
        BigDecimal targetSum = BigDecimal.ZERO;
        for (Category c : EXPENSE_CATEGORIES) {
            BigDecimal computed = raw.getOrDefault(c, BigDecimal.ZERO);
            BigDecimal spent = amt(in.currentMonthSpent(), c);
            // Never propose a limit below what is already spent this month — a budget must not be
            // born already-exceeded once CreateBudgetUseCase backfills the month's spend.
            BigDecimal finalAmount = computed.max(spent);
            basis.put(c, finalAmount.compareTo(computed) > 0 ? AllocationBasis.FLOORED : baseBasis);
            floored.put(c, finalAmount);
            targetSum = targetSum.add(finalAmount);
        }

        // Floors only ever expand, so they can push the total past the envelope. Claw the overage
        // back from the other categories' unspent headroom (proportionally to that headroom, never
        // below their own spent floor) so one overspent category shrinks the rest instead of
        // silently overflowing the plan. TRACK_ONLY users opt out of mid-month squeezing the same
        // way they opt out of settlement penalties: their savings ambition absorbs the overage
        // first, and other categories only shrink for whatever savings can't cover.
        BigDecimal overage = targetSum.subtract(envelope);
        if (overage.signum() > 0) {
            BigDecimal shrinkGoal = overage;
            if (in.focusMode() == Focus_Mode.TRACK_ONLY) {
                BigDecimal savings = in.targetSavings() == null
                        ? BigDecimal.ZERO : in.targetSavings().max(BigDecimal.ZERO);
                shrinkGoal = overage.subtract(savings).max(BigDecimal.ZERO);
            }
            Map<Category, BigDecimal> slack = new EnumMap<>(Category.class);
            BigDecimal totalSlack = BigDecimal.ZERO;
            for (Category c : EXPENSE_CATEGORIES) {
                BigDecimal s = floored.get(c).subtract(amt(in.currentMonthSpent(), c)).max(BigDecimal.ZERO);
                slack.put(c, s);
                totalSlack = totalSlack.add(s);
            }
            if (shrinkGoal.signum() > 0 && totalSlack.signum() > 0) {
                BigDecimal shrink = shrinkGoal.min(totalSlack);
                for (Category c : EXPENSE_CATEGORIES) {
                    if (slack.get(c).signum() <= 0) {
                        continue;
                    }
                    BigDecimal cut = slack.get(c).multiply(shrink)
                            .divide(totalSlack, 4, RoundingMode.HALF_UP);
                    floored.put(c, floored.get(c).subtract(cut));
                    targetSum = targetSum.subtract(cut);
                }
            }
        }

        // Snap every limit to the money step so proportional scaling never surfaces odd amounts.
        // Snapping rounds down (the shaved part rolls into surplus), except when that would dip
        // below the category's own spent floor — then it rounds the spent amount up instead.
        targetSum = BigDecimal.ZERO;
        for (Category c : EXPENSE_CATEGORIES) {
            BigDecimal snapped = snapToStep(floored.get(c), amt(in.currentMonthSpent(), c));
            floored.put(c, snapped);
            targetSum = targetSum.add(snapped);
        }

        // What even the squeeze couldn't absorb (every category is at its spent floor) has to be
        // funded from the month's savings ambition — reported so the caller can reduce targetSavings.
        BigDecimal savingsShortfall = targetSum.subtract(envelope).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        List<CategoryAllocation> allocations = toAllocations(floored, basis, in.recurring());

        // Whatever the envelope didn't need becomes savings. Never negative.
        BigDecimal surplus = envelope.subtract(targetSum).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        return new BudgetAllocationPlan(
                envelope.setScale(2, RoundingMode.HALF_UP), surplus, savingsShortfall, coldStart, false, allocations);
    }

    /** Snap down to the step; never below the spent floor (then the spent amount is snapped up). */
    private static BigDecimal snapToStep(BigDecimal amount, BigDecimal spent) {
        BigDecimal down = amount.divide(ROUNDING_STEP, 0, RoundingMode.FLOOR).multiply(ROUNDING_STEP);
        if (down.compareTo(spent) >= 0) {
            return down;
        }
        return spent.divide(ROUNDING_STEP, 0, RoundingMode.CEILING).multiply(ROUNDING_STEP);
    }

    /**
     * Build the allocation list at money scale. Amounts are already step-multiples from
     * {@link #snapToStep}, so no drift reconciliation is needed — the shaved remainder is
     * accounted for in {@code surplusToSavings} instead of being pushed into a category.
     */
    private List<CategoryAllocation> toAllocations(Map<Category, BigDecimal> floored,
                                                   Map<Category, AllocationBasis> basis,
                                                   Map<Category, BigDecimal> recurring) {
        List<CategoryAllocation> allocations = new ArrayList<>();
        for (Category c : EXPENSE_CATEGORIES) {
            allocations.add(new CategoryAllocation(
                    c,
                    floored.get(c).setScale(2, RoundingMode.HALF_UP),
                    amt(recurring, c).setScale(2, RoundingMode.HALF_UP),
                    basis.get(c)));
        }
        return allocations;
    }

    private BudgetAllocationPlan overCommittedPlan(BudgetAllocationInputs in) {
        List<CategoryAllocation> allocations = new ArrayList<>();
        for (Category c : EXPENSE_CATEGORIES) {
            allocations.add(new CategoryAllocation(
                    c,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    amt(in.recurring(), c).setScale(2, RoundingMode.HALF_UP),
                    AllocationBasis.HISTORY));
        }
        return new BudgetAllocationPlan(
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                false, true, allocations);
    }

    // ---- helpers ---------------------------------------------------------------------------------

    /** Tighter variance buffer for a more interventionist user; default GENTLE when unset. */
    private static double bufferFor(Intervention_Level level) {
        if (level == null) {
            return 0.10;
        }
        return switch (level) {
            case HARD -> 0.05;
            case GENTLE -> 0.10;
            case NOTIFY -> 0.20;
        };
    }

    private static BigDecimal totalMedian(BudgetAllocationInputs in) {
        BigDecimal total = BigDecimal.ZERO;
        for (Category c : EXPENSE_CATEGORIES) {
            total = total.add(amt(in.median(), c));
        }
        return total;
    }

    private static BigDecimal amt(Map<Category, BigDecimal> map, Category c) {
        if (map == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal v = map.get(c);
        return v == null ? BigDecimal.ZERO : v;
    }

    // ---- I/O records -----------------------------------------------------------------------------

    /**
     * Everything the calculator needs, as primitives. Maps may omit categories (treated as zero).
     *
     * @param envelope          spendable amount after commitments + target savings (Stage 0)
     * @param targetSavings     the month's savings ambition held out of the envelope — how much a
     *                          TRACK_ONLY user's floors may overflow before other categories shrink
     * @param median            trailing 3-month median expense per category
     * @param recurring         detected recurring (fixed) cost per category
     * @param currentMonthSpent amount already spent this month per category (the mid-month floor)
     * @param focusMode         user trait — tilts the cold-start template and the overage order
     * @param interventionLevel user trait — sets the needs buffer
     * @param monthsOfHistory   distinct prior months with spend; 0 forces the cold-start path
     */
    public record BudgetAllocationInputs(
            BigDecimal envelope,
            BigDecimal targetSavings,
            Map<Category, BigDecimal> median,
            Map<Category, BigDecimal> recurring,
            Map<Category, BigDecimal> currentMonthSpent,
            Focus_Mode focusMode,
            Intervention_Level interventionLevel,
            int monthsOfHistory) {
    }

    /**
     * The computed plan. {@code allocations} covers all 9 expense categories (amounts may be zero).
     * {@code savingsShortfall} is the part of the mid-month spent floors that the other categories'
     * headroom could not absorb — the caller funds it out of the month's savings target.
     */
    public record BudgetAllocationPlan(
            BigDecimal envelope,
            BigDecimal surplusToSavings,
            BigDecimal savingsShortfall,
            boolean coldStart,
            boolean overCommitted,
            List<CategoryAllocation> allocations) {
    }

    /** One category's proposed limit, the fixed portion within it, and how it was derived. */
    public record CategoryAllocation(
            Category category,
            BigDecimal amount,
            BigDecimal recurring,
            AllocationBasis basis) {
    }

    /** How a category's amount was derived: from history, a cold-start template, or a mid-month floor. */
    public enum AllocationBasis {
        HISTORY,
        TEMPLATE,
        FLOORED
    }
}
