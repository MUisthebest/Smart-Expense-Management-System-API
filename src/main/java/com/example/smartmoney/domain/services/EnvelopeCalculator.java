package com.example.smartmoney.domain.services;

import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.user.User;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Single source of truth for the "how much can this user spend / save" question.
 *
 * <h2>Why this class exists</h2>
 *
 * Three different services in the codebase used to answer overlapping questions with different
 * arithmetic and even different meanings for the same field name:
 *
 * <ul>
 *   <li>{@code BudgetAllocationCalculator} / {@code ComputeBudgetAllocationUseCase} —
 *       {@code safeSpending = income − commitment}, {@code envelope = safeSpending − targetSavings}.
 *       This is the "full pipeline" that reflects both project commitments and the trait saving
 *       target correctly.
 *   <li>{@code ProjectHypothesisService} — {@code available_budget = income − commitment}. Same
 *       formula as {@code User.calculateSafeSpending}, but only the first half — never subtracts
 *       {@code targetSavings}. This is why "shorten a project" what-ifs come out too pessimistic
 *       for AGGRESSIVE users, and "abandon a project" what-ifs come out too optimistic.
 *   <li>{@code SpendingSimulationService} — {@code "safe_spending" = totalBudget − totalSpent −
 *       extra}. Same name, completely different concept: it is budget headroom, not envelope.
 *       Fails hard when the user has not set budgets for every category.
 * </ul>
 *
 * A comment in {@code ProjectUpdateSuggestionService.checkMonthEndLeftover} (lines 194–198)
 * already flags the specific inconsistency this class fixes: "safeSpending is the PLANNED
 * envelope … it does NOT subtract actual spending already recorded this month. Treating the
 * whole planned figure as 'unused leftover' is wrong once real expenses have eaten into it."
 * That workaround lived in one caller; this service moves the reconciliation up to a single,
 * reusable place so every caller gets it right.
 *
 * <h2>How to use it</h2>
 *
 * Callers should stop computing {@code income − commitment} or {@code totalBudget − totalSpent}
 * inline and instead call one of the {@code compute} overloads and read the field they want off
 * the returned {@link EnvelopeSnapshot}:
 *
 * <ul>
 *   <li>Planning how much to budget for the month → {@link EnvelopeSnapshot#envelope()}
 *   <li>Answering "can I spend X more right now?" → {@link EnvelopeSnapshot#realRemaining()}
 *   <li>What-if on a project change — the caller has ALREADY summed the hypothetical
 *       commitment (as {@code ProjectHypothesisService} does) → use
 *       {@link #computeFromCommitment(User, BigDecimal, BigDecimal)}. Otherwise pass the
 *       hypothetical project list to {@link #compute(User, List)}.
 * </ul>
 *
 * <h2>Design constraints</h2>
 *
 * <ul>
 *   <li>Marked {@code @Service} for Spring injection into other application-layer services,
 *       but the arithmetic itself is pure — no repositories, no I/O, no state. The caller
 *       loads {@code User} and the project list; this class does math on them. Mirrors
 *       {@link BudgetAllocationCalculator} and {@link SpendingInsightAnalyzer}'s role in the
 *       domain-services package.
 *   <li>Every field on the snapshot is non-negative. {@link BigDecimal#max(BigDecimal)} with
 *       {@link BigDecimal#ZERO} is applied at the right places so downstream code can trust
 *       the numbers without re-validating.
 *   <li>Defensive on inputs: a null user throws (that is a caller bug), but null / missing
 *       optional fields degrade to {@link BigDecimal#ZERO}. The result carries an
 *       {@link EnvelopeSnapshot#overCommitted()} flag so callers can render a warning without
 *       repeating the check.
 * </ul>
 */
@Service
public class EnvelopeCalculator {

    /**
     * Compute an {@link EnvelopeSnapshot} for the user, given the projects currently in play.
     * Use this overload when the caller only needs the planned figures (budget allocation, what-if
     * project changes); when a real-time "how much can I still spend today" answer is needed,
     * use {@link #compute(User, List, BigDecimal)} and pass month-to-date spending too.
     *
     * @param user            the user; must not be null and must have completed financial setup
     * @param activeProjects  active projects to count toward the commitment; empty list is fine.
     *                        Non-active statuses (COMPLETED/ABANDONED/FROZEN/EXPIRED) should not
     *                        be passed — the caller is responsible for filtering, matching how
     *                        {@code ComputeBudgetAllocationUseCase} already loads them
     * @return a snapshot with every derived quantity filled in
     * @throws DomainException if the user has not completed financial setup — the same contract
     *                         {@link User#calculateSafeSpending(BigDecimal)} already enforces
     */
    public EnvelopeSnapshot compute(User user, List<Project> activeProjects) {
        return compute(user, activeProjects, BigDecimal.ZERO);
    }

    /**
     * Same as {@link #compute(User, List)}, but also reconciles the planned envelope against
     * money the user has already actually spent this month, so callers can answer
     * "how much do I still have left right now?" without re-doing the subtraction themselves.
     *
     * @param user                  the user; must not be null and must have completed setup
     * @param activeProjects        active projects to count toward the commitment
     * @param actualSpentThisMonth  sum of EXPENSE transactions charged this month so far,
     *                              EXCLUDING {@code PROJECT_CONTRIBUTION} (which is book-
     *                              keeping, not real spending). Null or negative degrades
     *                              to zero
     */
    public EnvelopeSnapshot compute(User user, List<Project> activeProjects,
                                    BigDecimal actualSpentThisMonth) {
        return computeFromCommitment(user, sumCommitments(activeProjects), actualSpentThisMonth);
    }

    /**
     * Same computation as {@link #compute(User, List, BigDecimal)}, but takes the already-summed
     * total commitment. Use this from callers that build a hypothetical scenario by adjusting a
     * commitment scalar directly (e.g. {@code ProjectHypothesisService} previewing a shorten by
     * dividing the target by a new month-count) rather than by re-materialising a project list.
     *
     * @param user             the user; must not be null and must have completed setup
     * @param totalCommitment  pre-summed {@code Σ Project.calculateMonthlyCommitment()} for the
     *                         scenario. Null degrades to zero
     * @param actualSpentThisMonth  optional month-to-date real spending (null → zero)
     */
    public EnvelopeSnapshot computeFromCommitment(User user, BigDecimal totalCommitment,
                                                  BigDecimal actualSpentThisMonth) {
        if (user == null) {
            throw new DomainException("User is required");
        }
        if (!user.hasCompletedFinancialSetup()) {
            throw new DomainException(
                    "Please complete financial setup before calculating envelope");
        }

        BigDecimal income = user.getIncome() != null ? user.getIncome() : BigDecimal.ZERO;
        BigDecimal savingCapacity = user.getSavingCapacity();
        BigDecimal commitment = totalCommitment != null
                ? totalCommitment.max(BigDecimal.ZERO)
                : BigDecimal.ZERO;

        // Mirrors User.calculateSafeSpending — kept in-line rather than delegated so we skip the
        // domain-exception rethrow path (we already validated setup + income above).
        BigDecimal safeSpending = income.subtract(commitment).max(BigDecimal.ZERO);

        // Projects are press-to-contribute savings, so they count toward the savings ambition:
        // only the shortfall between the trait target and what projects already reserve is held
        // back as targetSavings. This is the SAME formula ComputeBudgetAllocationUseCase applies
        // in Stage 0 — lifted here so what-if callers get it too.
        BigDecimal targetSavings = savingCapacity.subtract(commitment).max(BigDecimal.ZERO);
        BigDecimal envelope = safeSpending.subtract(targetSavings).max(BigDecimal.ZERO);

        BigDecimal spent = actualSpentThisMonth != null
                ? actualSpentThisMonth.max(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        BigDecimal realRemaining = envelope.subtract(spent).max(BigDecimal.ZERO);

        boolean overCommitted = commitment.compareTo(income) > 0;

        return new EnvelopeSnapshot(
                income,
                savingCapacity,
                commitment,
                safeSpending,
                targetSavings,
                envelope,
                spent,
                realRemaining,
                overCommitted);
    }

    /**
     * Sum {@code Project.calculateMonthlyCommitment()} across the given projects.
     * Null-safe on the list itself and on individual entries; commitments already sum to the
     * caller's chosen precision (2 decimals) via {@link Project#calculateMonthlyCommitment()}.
     */
    private BigDecimal sumCommitments(List<Project> projects) {
        if (projects == null || projects.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return projects.stream()
                .filter(p -> p != null)
                .map(Project::calculateMonthlyCommitment)
                .filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}