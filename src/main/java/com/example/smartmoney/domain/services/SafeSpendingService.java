package com.example.smartmoney.domain.services;

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
        UserIncome income = userIncomeRepository.findById(ownerId.value()).orElse(null);
        if (income == null || income.getUsableIncome() == null) {
            return;
        }

        List<Project> activeProjects = projectRepository.findAllByUser(ownerId)
                .stream()
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

        int newSafeSpending = income.getUsableIncome() - sumObligations.setScale(0, RoundingMode.HALF_UP).intValue();
        if (newSafeSpending < 0) {
            newSafeSpending = 0;
        }

        income.setSafeSpending(newSafeSpending);
        userIncomeRepository.save(income);
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
