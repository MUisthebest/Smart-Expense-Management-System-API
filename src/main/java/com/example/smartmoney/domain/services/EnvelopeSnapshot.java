package com.example.smartmoney.domain.services;

import java.math.BigDecimal;

/**
 * A single, consistent snapshot of every envelope-related number for a user at a point in time.
 *
 * <p>This record exists because the same word — "safe spending" — has meant three different
 * things across the codebase (see  javadoc). Every service that needs
 * to reason about how much a user can spend or save should get their numbers from an
 * {@code EnvelopeSnapshot} produced by , so there is exactly one
 * definition of each quantity in the whole system.
 *
 * <p>Every field is non-null and non-negative (never {@code null}, never below zero). Fields
 * are ordered from "raw inputs" to "derived quantities" — reading the record top to bottom
 * follows the same order as the actual computation.
 *
 * @param income                 monthly net income the user declared — the top of the funnel
 * @param savingCapacity         income × trait rate ({@code 10 / 20 / 35 %}); the target amount
 *                               the user's saving pace says they should be putting away each
 *                               month, absent any project commitments
 * @param totalCommitment        sum of {@code Project.calculateMonthlyCommitment()} across all
 *                               ACTIVE projects — money the user has already promised to save
 *                               into a goal each month
 * @param safeSpending           {@code max(0, income − totalCommitment)} — legacy name kept for
 *                               continuity; equal to what {@code User.calculateSafeSpending()}
 *                               returns. This is NOT the same as {@code envelope}
 * @param targetSavings          {@code max(0, savingCapacity − totalCommitment)} — the extra
 *                               amount the trait wants to save on top of what projects already
 *                               absorb; zero when projects already fully consume the capacity
 * @param envelope               {@code max(0, safeSpending − targetSavings)} — the true amount
 *                               a user can budget for day-to-day categories in this month.
 *                               This is the number to divide across categories, not
 *                               {@code safeSpending}
 * @param actualSpentThisMonth   sum of transactions in EXPENSE categories charged this month
 *                               so far — EXCLUDES {@code PROJECT_CONTRIBUTION} (which is book-
 *                               keeping, not real spending). Zero if the caller does not
 *                               provide a month-to-date figure
 * @param realRemaining          {@code max(0, envelope − actualSpentThisMonth)} — how much
 *                               spending headroom actually still exists right now. This is
 *                               the correct number for "can I spend X more this month" questions,
 *                               replacing the old {@code totalBudget − totalSpent} shortcut in
 *                               {@code SpendingSimulationService}
 * @param overCommitted          {@code true} iff {@code totalCommitment > income}, meaning the
 *                               user has promised more to projects than they earn — every
 *                               downstream service should degrade gracefully when this fires
 *                               instead of producing nonsense negative envelopes
 */
public record EnvelopeSnapshot(
        BigDecimal income,
        BigDecimal savingCapacity,
        BigDecimal totalCommitment,
        BigDecimal safeSpending,
        BigDecimal targetSavings,
        BigDecimal envelope,
        BigDecimal actualSpentThisMonth,
        BigDecimal realRemaining,
        boolean overCommitted
) {
    /**
     * Compact constructor — normalizes {@code null} inputs to {@link BigDecimal#ZERO} rather
     * than propagating them through the arithmetic. Callers should NOT rely on this to smuggle
     * unset fields through; it is a defensive last-line-of-defence, not an API for the caller.
     */
    public EnvelopeSnapshot {
        income               = income               != null ? income               : BigDecimal.ZERO;
        savingCapacity       = savingCapacity       != null ? savingCapacity       : BigDecimal.ZERO;
        totalCommitment      = totalCommitment      != null ? totalCommitment      : BigDecimal.ZERO;
        safeSpending         = safeSpending         != null ? safeSpending         : BigDecimal.ZERO;
        targetSavings        = targetSavings        != null ? targetSavings        : BigDecimal.ZERO;
        envelope             = envelope             != null ? envelope             : BigDecimal.ZERO;
        actualSpentThisMonth = actualSpentThisMonth != null ? actualSpentThisMonth : BigDecimal.ZERO;
        realRemaining        = realRemaining        != null ? realRemaining        : BigDecimal.ZERO;
    }
}