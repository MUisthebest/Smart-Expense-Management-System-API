package com.example.smartmoney.domain.services;

import com.example.smartmoney.domain.exceptions.InsufficientSafeSpendingException;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.UserIncome;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SafeSpendingService {

    private final UserIncomeRepository userIncomeRepository;
    private final ProjectRepository projectRepository;
    private final ProjectTrackingRepository projectTrackingRepository;

    /**
     * Recalculates and saves the user's safe spending.
     * Formula:
     *   safe_spending = usable_income - SUM(monthly_saving of active projects) - SUM(money_owed of active projects)
     *   Floored at 0.
     */
    @Transactional
    public void recalculateAndSaveSafeSpending(UserId ownerId) {
        UserIncome income = userIncomeRepository.findByUserId(ownerId.value()).orElse(null);
        if (income == null || income.getUsableIncome() == null) {
            return;
        }

        BigDecimal sumObligations = sumObligationsForUser(ownerId);

        int newSafeSpending = income.getUsableIncome() - sumObligations.setScale(0, RoundingMode.HALF_UP).intValue();
        if (newSafeSpending < 0) {
            newSafeSpending = 0;
        }

        income.setSafeSpending(newSafeSpending);
        userIncomeRepository.save(income);
    }

    /**
     * @return the user's persisted safe spending, or {@link BigDecimal#ZERO}
     * when the user has no income profile yet.
     */
    public BigDecimal currentSafeSpending(UserId ownerId) {
        UserIncome income = userIncomeRepository.findById(ownerId.value()).orElse(null);
        if (income == null || income.getSafeSpending() == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(income.getSafeSpending());
    }

    /**
     * Pure-arithmetic preview: what the user's safe spending would be if a
     * new monthly obligation of {@code additionalMonthly} were added on top
     * of their current active commitments. Does not persist anything.
     */
    public BigDecimal projectedSafeSpendingIfAdded(UserId ownerId, BigDecimal additionalMonthly) {
        if (additionalMonthly == null) additionalMonthly = BigDecimal.ZERO;

        UserIncome income = userIncomeRepository.findById(ownerId.value()).orElse(null);
        if (income == null || income.getUsableIncome() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal sumObligations = sumObligationsForUser(ownerId).add(additionalMonthly);
        BigDecimal usable = BigDecimal.valueOf(income.getUsableIncome());
        BigDecimal result = usable.subtract(sumObligations);
        return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result;
    }

    /**
     * Gating check used by every code path that increases a user's monthly
     * obligations (create personal project, raise monthly saving, accept a
     * group invite). Throws {@link InsufficientSafeSpendingException} with
     * the requested and currently-available figures when the addition would
     * push safe spending below zero.
     *
     * <p>Boundary: equal to current safe spending is allowed (i.e. floor is 0).
     */
    public void requireCanAfford(UserId ownerId, BigDecimal additionalMonthly) {
        if (additionalMonthly == null
                || additionalMonthly.compareTo(BigDecimal.ZERO) <= 0) return;

        BigDecimal current = currentSafeSpending(ownerId);
        if (additionalMonthly.compareTo(current) > 0) {
            throw new InsufficientSafeSpendingException(additionalMonthly, current);
        }
    }

    private BigDecimal sumObligationsForUser(UserId ownerId) {
        List<Project> activeProjects = projectRepository.findAllByUser(ownerId)
                .stream()
                .filter(p -> ownerId.equals(p.getOwnerId()))
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE)
                .toList();

        BigDecimal sumObligations = BigDecimal.ZERO;

        for (Project p : activeProjects) {
            ProjectTracking tracking = projectTrackingRepository.findByProjectId(p.getId()).orElse(null);

            BigDecimal monthlySaving = tracking != null && tracking.getMonthlySaving() != null
                    ? tracking.getMonthlySaving() : derivedMonthly(p);

            BigDecimal moneyOwed = tracking != null && tracking.getMoneyOwed() != null
                    ? tracking.getMoneyOwed() : BigDecimal.ZERO;

            sumObligations = sumObligations.add(monthlySaving).add(moneyOwed);
        }
        return sumObligations;
    }

    private BigDecimal derivedMonthly(Project p) {
        if (p.getTargetAmount() == null || p.getDeadline() == null) return BigDecimal.ZERO;
        int months = monthsToDeadline(LocalDate.now(), p.getDeadline());
        return p.getTargetAmount().amount()
                .divide(BigDecimal.valueOf(months), 2, RoundingMode.CEILING);
    }

    private int monthsToDeadline(LocalDate today, LocalDate deadline) {
        if (deadline == null || deadline.isBefore(today)) return 1;
        int m = (deadline.getYear() - today.getYear()) * 12
                + (deadline.getMonthValue() - today.getMonthValue()) + 1;
        return Math.max(1, m);
    }
}
