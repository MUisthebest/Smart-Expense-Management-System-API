package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.UserIncome;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Centralised enforcement of the 20% monthly-obligation rule.
 *
 * <p>Rule: the sum of monthly saving obligations across all ACTIVE projects
 * owned by a user must never exceed 20% of that user's usable income.
 *
 * <p>Provide the proposed monthly saving for the project being created/updated
 * and pass every other ACTIVE project owned by the same user so this policy
 * can sum their obligations. Throws immediately on breach.
 *
 * <h3>Call sites</h3>
 * <ul>
 *   <li>{@link CreateProjectUseCase} — new project validation</li>
 *   <li>{@link UpdateProjectUseCase} — post-update re-validation &amp; tracking recalibration</li>
 *   <li>{@link CreateProjectTrackingUseCase} — when initial monthly saving is computed</li>
 * </ul>
 */
public final class MonthlyObligationPolicy {

    private MonthlyObligationPolicy() {}

    /**
     * Asserts that the proposed monthly saving for a single project,
     * added to the obligations of all other ACTIVE projects owned by
     * the same user, stays within 20% of the user's usable income.
     *
     * @param income             the owner's {@link UserIncome} (must not be null)
     * @param otherActiveProjects all ACTIVE projects owned by this user
     *                            <em>excluding</em> the project being changed
     * @param proposedMonthly    the monthly saving that will be written for
     *                           the subject project after this operation
     */
    public static void assertWithinLimit(
            UserIncome income,
            List<Project> otherActiveProjects,
            BigDecimal proposedMonthly
    ) {
        if (income.getUsableIncome() == null || income.getUsableIncome() <= 0) {
            return; // cannot validate without income data — other guards cover this
        }

        BigDecimal usableIncome = BigDecimal.valueOf(income.getUsableIncome());
        BigDecimal limit        = usableIncome.multiply(new BigDecimal("0.20"));

        BigDecimal sumOther = otherActiveProjects.stream()
                .map(MonthlyObligationPolicy::derivedMonthly)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal total = sumOther.add(proposedMonthly.setScale(2, RoundingMode.HALF_UP));

        if (total.compareTo(limit) > 0) {
            throw new ProjectException(
                    "Total monthly targets of ACTIVE projects must be <= 20% of usable_income",
                    ProjectErrorCode.PROJECT_USABLE_INCOME_LIMIT_EXCEEDED
            );
        }
    }

    /**
     * Derives the effective monthly saving for a project as
     * {@code targetAmount / monthsToDeadline}, consistent with how
     * all use cases compute the obligation for an existing project.
     */
    public static BigDecimal derivedMonthly(Project p) {
        if (p.getTargetAmount() == null || p.getDeadline() == null) return BigDecimal.ZERO;
        int months = monthsToDeadline(LocalDate.now(), p.getDeadline());
        return p.getTargetAmount().amount()
                .divide(BigDecimal.valueOf(months), 2, RoundingMode.CEILING);
    }

    /**
     * Derives the effective monthly saving from raw values —
     * use when no {@link Project} object is available yet (e.g. create path).
     */
    public static BigDecimal derivedMonthly(BigDecimal targetAmount, LocalDate deadline) {
        if (targetAmount == null || deadline == null) return BigDecimal.ZERO;
        int months = monthsToDeadline(LocalDate.now(), deadline);
        return targetAmount.divide(BigDecimal.valueOf(months), 2, RoundingMode.CEILING);
    }

    /**
     * Filters {@code all} to ACTIVE projects owned by {@code ownerId},
     * optionally excluding one project by id.
     *
     * @param all              all projects for this user
     * @param ownerId          user whose projects to include
     * @param excludeProjectId project to skip (the one being changed), or {@code null}
     */
    public static List<Project> otherActiveOwned(
            List<Project> all,
            UserId ownerId,
            UUID excludeProjectId
    ) {
        return all.stream()
                .filter(p -> ownerId.equals(p.getOwnerId()))
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE)
                .filter(p -> excludeProjectId == null || !p.getId().value().equals(excludeProjectId))
                .toList();
    }

    // ── internal helpers ─────────────────────────────────────────────────────

    static int monthsToDeadline(LocalDate today, LocalDate deadline) {
        if (deadline == null || deadline.isBefore(today)) return 1;
        int m = (deadline.getYear() - today.getYear()) * 12
                + (deadline.getMonthValue() - today.getMonthValue()) + 1;
        return Math.max(1, m);
    }
}
