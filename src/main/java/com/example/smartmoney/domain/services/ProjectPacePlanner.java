package com.example.smartmoney.domain.services;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Turns "how much do you want, and how hard do you want to push?" into a concrete saving plan.
 *
 * <p>This is the calculation behind the URGENT / RELAXED choice the project-creation screen shows:
 * the user never types a deadline there, they pick a pace and the system answers with the number
 * of months and the monthly amount. {@code ProjectBudgetAdvisorUseCase} serves that screen and the
 * chatbot's create flow now serves the same choice, so the math lives here rather than in either
 * caller — a chat that quoted a different month count than the screen for the same target would be
 * describing a project the backend is not going to build.
 *
 * <p>Both modes draw on the same pool: whatever monthly saving capacity is left after the user's
 * existing project commitments. URGENT spends all of it (fewest months); RELAXED spends half,
 * leaving room to breathe — and falls back to the full amount when half would round to nothing.
 */
public final class ProjectPacePlanner {

    private ProjectPacePlanner() {}

    /** Deadline ceiling shared with {@code CreateProjectUseCase}: no plan may run past this. */
    public static final int MAX_MONTHS = 60;

    /** How hard the user wants to push toward the target. */
    public enum Mode {
        /** Finish as fast as the remaining saving capacity allows. */
        URGENT,
        /** Use half the remaining capacity, so the goal leaves room for everything else. */
        RELAXED
    }

    /**
     * @param months        how many months the plan runs, always within [1, {@value #MAX_MONTHS}]
     * @param monthlySaving what that works out to per month — {@code target / months}, rounded up
     *                      so the plan never falls a few dong short of the target
     */
    public record Plan(int months, BigDecimal monthlySaving) {}

    /**
     * @param target       the project's target amount
     * @param capacityLeft monthly saving capacity not already committed to other projects; must be
     *                     positive — a caller with nothing left cannot offer a plan at all and
     *                     should say so instead
     */
    public static Plan plan(BigDecimal target, BigDecimal capacityLeft, Mode mode) {
        if (target == null || target.signum() <= 0) {
            throw new IllegalArgumentException("target must be > 0");
        }
        if (capacityLeft == null || capacityLeft.signum() <= 0) {
            throw new IllegalArgumentException("capacityLeft must be > 0");
        }

        BigDecimal pace = mode == Mode.URGENT
                ? capacityLeft
                : capacityLeft.divide(BigDecimal.valueOf(2), 2, RoundingMode.FLOOR);
        if (pace.signum() <= 0) {
            pace = capacityLeft;
        }

        // The cap applies to BOTH modes: capping only the relaxed plan let a squeezed budget push
        // the urgent plan past 60 months and out past the relaxed one.
        int months = target.divide(pace, 0, RoundingMode.CEILING).intValue();
        months = Math.min(Math.max(1, months), MAX_MONTHS);

        return new Plan(months, target.divide(BigDecimal.valueOf(months), 2, RoundingMode.CEILING));
    }

    /**
     * The monthly commitment a project of this size and horizon carries — the figure both the
     * create use case and the advisor charge against saving capacity.
     */
    public static BigDecimal monthlyCommitment(BigDecimal target, int months) {
        if (target == null || months <= 0) return BigDecimal.ZERO;
        return target.divide(BigDecimal.valueOf(months), 2, RoundingMode.CEILING);
    }
}
