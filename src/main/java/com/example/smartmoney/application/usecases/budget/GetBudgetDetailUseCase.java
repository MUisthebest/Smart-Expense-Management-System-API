package com.example.smartmoney.application.usecases.budget;

import com.example.smartmoney.application.dtos.responses.BudgetItemResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetBudgetDetailUseCase {
    private final BudgetRepository budgetRepository;

    public BudgetItemResponse execute(UUID budgetId, UUID userId) {
        if (budgetId == null) {
            throw new DomainException("Budget ID is required");
        }
        if (userId == null) {
            throw new DomainException("User ID is required");
        }

        Budget budget = budgetRepository.findById(budgetId)
            .orElseThrow(() -> new DomainException("Budget not found with id: " + budgetId));

        // Verify the budget belongs to the user
        if (!budget.getUserId().equals(userId)) {
            throw new DomainException("Budget does not belong to the authenticated user");
        }

        log.info("Retrieved budget: id={}, userId={}", budgetId, userId);

        // TODO: Calculate spent amount from transactions for this budget's category and month/year
        BigDecimal spent = BigDecimal.ZERO;
        BigDecimal remaining = budget.calculateRemaining(spent);
        Double progressPercent = budget.calculateProgressPercent(spent);
        Budget.AlertLevel alertLevel = budget.getAlertLevel(spent);

        return BudgetItemResponse.builder()
                .budgetId(budget.getId())
                .userId(budget.getUserId())
                .category(budget.getCategory())
                .amountLimit(budget.getAmountLimit())
                .month(budget.getMonth())
                .year(budget.getYear())
                .spent(spent)
                .remaining(remaining)
                .progressPercent(progressPercent)
                .alertLevel(alertLevel)
                .createdAt(budget.getCreatedAt())
                .updatedAt(budget.getUpdatedAt())
                .build();
    }
}
