package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.ProjectBudgetAdvisorRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectBudgetAdvisorResponse;
import com.example.smartmoney.application.ports.llm.LlmPort;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.user.UserIncome;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Advises the user on the monthly saving and number of months needed for a new project.
 *
 * <p><b>Rule enforced:</b>
 * {@code new_monthly_saving + existing_obligations <= 20% of usable_income}
 * where {@code existing_obligations = usable_income - safe_spending}.
 *
 * <p><b>Mode behaviour:</b>
 * <ul>
 *   <li>{@code URGENT}  — minimum months = ceil(target / maxAllowedNewMonthlySaving)</li>
 *   <li>{@code RELAXED} — minimum months = ceil(target / (maxAllowedNewMonthlySaving / 2)), capped at 60</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectBudgetAdvisorUseCase {

    private static final int MAX_MONTHS = 60;

    private final UserIncomeRepository userIncomeRepository;
    private final UserRepository userRepository;
    private final LlmPort llmPort; // kept for future services; not used here

    public ProjectBudgetAdvisorResponse execute(String actorId, ProjectBudgetAdvisorRequest req) {
        UUID userId = parseUserId(actorId);

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ProjectException("User not found", ProjectErrorCode.PROJECT_PERMISSION_DENIED));

        validateIncomeSetupCompleted(user);

        UserIncome income = userIncomeRepository.findByUserId(userId)
                .orElseThrow(() -> new ProjectException(
                        "User income record not found. Please set your income first.",
                        ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED));

        if (income.getUsableIncome() == null || income.getUsableIncome() <= 0) {
            throw new ProjectException(
                    "usable_income must be set and > 0",
                    ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED);
        }

        BigDecimal usableIncome = BigDecimal.valueOf(income.getUsableIncome());
        BigDecimal currentSafeSpending = income.getSafeSpending() != null
                ? BigDecimal.valueOf(income.getSafeSpending())
                : usableIncome;

        // Existing project obligations = usable_income - safe_spending
        BigDecimal existingObligations = usableIncome.subtract(currentSafeSpending);

        // Maximum total obligation allowed = 20% of usable income
        BigDecimal maxObligation = usableIncome.multiply(new BigDecimal("0.20"));

        // Max affordable saving for this new project under the 20% rule
        BigDecimal maxAllowedNewMonthlySaving = maxObligation.subtract(existingObligations);

        if (maxAllowedNewMonthlySaving.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ProjectException(
                    "You have reached your 20% usable income limit for project savings.",
                    ProjectErrorCode.PROJECT_USABLE_INCOME_LIMIT_EXCEEDED);
        }

        // --- Compute months & monthly saving ---
        int months;
        BigDecimal monthlySaving;

        if (req.getMode() == ProjectBudgetAdvisorRequest.Mode.URGENT) {
            // URGENT: minimum months — save as much as the 20% rule allows per month
            months = req.getTargetAmount()
                    .divide(maxAllowedNewMonthlySaving, 0, RoundingMode.CEILING)
                    .intValue();
            months = Math.max(1, months);
        } else {
            // RELAXED: use half of the max allowed saving per month, capped at 60 months
            BigDecimal relaxedMonthlySaving = maxAllowedNewMonthlySaving
                    .divide(new BigDecimal("2"), 2, RoundingMode.FLOOR);
            months = req.getTargetAmount()
                    .divide(relaxedMonthlySaving, 0, RoundingMode.CEILING)
                    .intValue();
            months = Math.min(Math.max(1, months), MAX_MONTHS);
        }

        monthlySaving = req.getTargetAmount()
                .divide(BigDecimal.valueOf(months), 2, RoundingMode.CEILING);

        // --- Enforce 20% rule ---
        // new_monthly_saving + existing_obligations <= 20% of usable_income
        BigDecimal totalObligation = monthlySaving.add(existingObligations);
        if (totalObligation.compareTo(maxObligation) > 0) {
            throw new ProjectException(
                    "Monthly saving of " + monthlySaving + " VND combined with existing obligations exceeds 20% of usable income. " +
                    "Consider a lower target amount.",
                    ProjectErrorCode.PROJECT_USABLE_INCOME_LIMIT_EXCEEDED);
        }

        return ProjectBudgetAdvisorResponse.builder()
                .monthlySaving(monthlySaving)
                .numberOfMonths(months)
                .build();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private UUID parseUserId(String actorId) {
        try {
            return UUID.fromString(actorId);
        } catch (Exception e) {
            throw new ProjectException("Invalid actor id", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
    }

    private void validateIncomeSetupCompleted(com.example.smartmoney.domain.models.user.User user) {
        if (!user.isIncomeSetupCompleted()) {
            throw new ProjectException(
                    "Please set up your income profile before using project advisor",
                    ProjectErrorCode.INCOME_PROFILE_REQUIRED);
        }
    }
}
