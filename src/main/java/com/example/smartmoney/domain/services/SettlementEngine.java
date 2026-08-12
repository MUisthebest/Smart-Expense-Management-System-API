package com.example.smartmoney.domain.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure domain service for monthly settlement calculations.
 * No Spring annotations, no side effects — only math.
 */
public class SettlementEngine {

    private SettlementEngine() {}

    /** Debt at or above {@code monthlySaving ×} this multiplier freezes an ACTIVE project. */
    public static final int FREEZE_DEBT_MULTIPLIER = 3;

    /** A project that stays FROZEN for this many settlements expires. */
    public static final int MAX_FROZEN_MONTHS = 3;

    /**
     * What the user is actually expected to save this month: their projects' combined monthly
     * saving, capped by what their saving pace says they can afford. Nobody is penalised for
     * failing to save beyond their means.
     */
    public static BigDecimal effectiveGoals(BigDecimal totalSavingGoals, BigDecimal savingCapacity) {
        if (totalSavingGoals == null) return BigDecimal.ZERO;
        if (savingCapacity == null) return totalSavingGoals;
        return totalSavingGoals.min(savingCapacity);
    }

    /** Debt level at which an ACTIVE project is frozen. */
    public static BigDecimal freezeThreshold(BigDecimal monthlySaving) {
        if (monthlySaving == null) return BigDecimal.ZERO;
        return monthlySaving.multiply(BigDecimal.valueOf(FREEZE_DEBT_MULTIPLIER));
    }

    /**
     * Penalty = max(0, actualExpenses - safeSpending).
     * Returns ZERO when spending is within budget.
     */
    public static BigDecimal calculatePenalty(BigDecimal actualExpenses, BigDecimal safeSpending) {
        BigDecimal diff = actualExpenses.subtract(safeSpending);
        return diff.compareTo(BigDecimal.ZERO) > 0
                ? diff.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2);
    }

    /**
     * Recalculates how many months are left toward the project target.
     *
     * <pre>
     * calculated_months_left = (targetAmount - moneySaved) / monthlySaving
     * diff = floor(oldMonthLeft - calculated_months_left)
     * if (diff <= 0) newMonthLeft = oldMonthLeft - 1
     * else           newMonthLeft = oldMonthLeft - diff
     * newMonthLeft = max(0, newMonthLeft)
     * </pre>
     *
     * @param targetAmount  project target amount
     * @param moneySaved    amount saved so far (after this month's saving is added)
     * @param monthlySaving fixed monthly saving amount
     * @param oldMonthLeft  month_left value before this settlement
     * @return updated month_left (never negative)
     */
    public static int recalculateMonthLeft(
            BigDecimal targetAmount,
            BigDecimal moneySaved,
            BigDecimal monthlySaving,
            int oldMonthLeft
    ) {
        if (monthlySaving.compareTo(BigDecimal.ZERO) == 0) {
            // Avoid division by zero; simply decrement by 1
            return Math.max(0, oldMonthLeft - 1);
        }

        BigDecimal remaining = targetAmount.subtract(moneySaved);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            // Already reached or exceeded the target
            return 0;
        }

        BigDecimal calculatedMonthsLeft = remaining.divide(monthlySaving, 10, RoundingMode.HALF_UP);

        // diff = floor(oldMonthLeft - calculatedMonthsLeft)
        BigDecimal diffDecimal = BigDecimal.valueOf(oldMonthLeft).subtract(calculatedMonthsLeft);
        int diff = diffDecimal.setScale(0, RoundingMode.FLOOR).intValue();

        int newMonthLeft;
        if (diff <= 0) {
            newMonthLeft = oldMonthLeft - 1;
        } else {
            newMonthLeft = oldMonthLeft - diff;
        }

        return Math.max(0, newMonthLeft);
    }
}
