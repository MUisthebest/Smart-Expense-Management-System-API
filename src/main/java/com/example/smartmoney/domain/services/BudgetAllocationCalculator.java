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

        List<CategoryAllocation> allocations = roundAndReconcile(floored, basis, in.recurring(), targetSum);

        // Whatever the envelope didn't need becomes savings. Never negative (floors can push the
        // total over the envelope in a heavy mid-month; that overage isn't "savings").
        BigDecimal surplus = envelope.subtract(targetSum).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        return new BudgetAllocationPlan(
                envelope.setScale(2, RoundingMode.HALF_UP), surplus, coldStart, false, allocations);
    }

    /**
     * Round each allocation to money scale and push the penny drift into the largest allocation so
     * the rounded amounts sum back to {@code targetSum} exactly (deterministic, unlike the AI path).
     */
    private List<CategoryAllocation> roundAndReconcile(Map<Category, BigDecimal> floored,
                                                       Map<Category, AllocationBasis> basis,
                                                       Map<Category, BigDecimal> recurring,
                                                       BigDecimal targetSum) {
        Map<Category, BigDecimal> rounded = new EnumMap<>(Category.class);
        BigDecimal sumRounded = BigDecimal.ZERO;
        Category largest = null;
        for (Category c : EXPENSE_CATEGORIES) {
            BigDecimal r = floored.get(c).setScale(2, RoundingMode.HALF_UP);
            rounded.put(c, r);
            sumRounded = sumRounded.add(r);
            if (largest == null || r.compareTo(rounded.get(largest)) > 0) {
                largest = c;
            }
        }
        BigDecimal drift = targetSum.setScale(2, RoundingMode.HALF_UP).subtract(sumRounded);
        if (largest != null && drift.signum() != 0) {
            rounded.put(largest, rounded.get(largest).add(drift));
        }

        List<CategoryAllocation> allocations = new ArrayList<>();
        for (Category c : EXPENSE_CATEGORIES) {
            allocations.add(new CategoryAllocation(
                    c,
                    rounded.get(c).setScale(2, RoundingMode.HALF_UP),
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
     * @param median            trailing 3-month median expense per category
     * @param recurring         detected recurring (fixed) cost per category
     * @param currentMonthSpent amount already spent this month per category (the mid-month floor)
     * @param focusMode         user trait — tilts the cold-start template
     * @param interventionLevel user trait — sets the needs buffer
     * @param monthsOfHistory   distinct prior months with spend; 0 forces the cold-start path
     */
    public record BudgetAllocationInputs(
            BigDecimal envelope,
            Map<Category, BigDecimal> median,
            Map<Category, BigDecimal> recurring,
            Map<Category, BigDecimal> currentMonthSpent,
            Focus_Mode focusMode,
            Intervention_Level interventionLevel,
            int monthsOfHistory) {
    }

    /** The computed plan. {@code allocations} covers all 9 expense categories (amounts may be zero). */
    public record BudgetAllocationPlan(
            BigDecimal envelope,
            BigDecimal surplusToSavings,
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
