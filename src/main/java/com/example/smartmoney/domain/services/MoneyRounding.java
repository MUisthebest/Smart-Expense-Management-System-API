package com.example.smartmoney.domain.services;

import com.example.smartmoney.domain.exceptions.DomainException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Snaps proposed money amounts to a clean step so arithmetic never surfaces figures like
 * 5,425,000.37 or 2,923,076.92 on a card the user has to say yes to. A limit is a decision, and a
 * decision reads better in round numbers than in the exact output of a proportional split.
 *
 * <p>Pure and Spring-free like {@link SpendingInsightAnalyzer}; {@code DomainServiceConfig} wires
 * the configured step. The step is currency-dependent — {@value #DEFAULT_STEP_VALUE} suits VND,
 * where it is a rounding error, but a USD deployment would want a far smaller one — so it is a
 * property rather than a constant. Tests that predate rounding construct this with a step of 1,
 * which makes every operation an identity on whole amounts.
 */
public class MoneyRounding {

    static final String DEFAULT_STEP_VALUE = "10000";

    /** The step used unless one is configured — VND-scale, matching {@link BudgetAllocationCalculator}. */
    public static final BigDecimal DEFAULT_STEP = new BigDecimal(DEFAULT_STEP_VALUE);

    private final BigDecimal step;

    public MoneyRounding() {
        this(DEFAULT_STEP);
    }

    public MoneyRounding(BigDecimal step) {
        if (step == null || step.compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("Money rounding step must be greater than 0");
        }
        this.step = step;
    }

    public BigDecimal step() {
        return step;
    }

    /** Largest multiple of the step at or below {@code amount}; negatives collapse to zero. */
    public BigDecimal snapDown(BigDecimal amount) {
        return money(stepsFloor(amount));
    }

    /** Smallest multiple of the step at or above {@code amount}; negatives collapse to zero. */
    public BigDecimal snapUp(BigDecimal amount) {
        return money(stepsCeil(amount));
    }

    /**
     * How many whole steps fit inside {@code amount} — the capacity form, used when an amount is
     * something to be spent or given away and overshooting it is not allowed.
     */
    public long stepsFloor(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0L;
        }
        return amount.divide(step, 0, RoundingMode.FLOOR).longValueExact();
    }

    /**
     * How many whole steps it takes to cover {@code amount} — the requirement form, used when an
     * amount must be reached and falling short defeats the purpose.
     */
    public long stepsCeil(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return 0L;
        }
        return amount.divide(step, 0, RoundingMode.CEILING).longValueExact();
    }

    /** The money value of a whole number of steps. */
    public BigDecimal money(long steps) {
        return step.multiply(BigDecimal.valueOf(steps));
    }
}
