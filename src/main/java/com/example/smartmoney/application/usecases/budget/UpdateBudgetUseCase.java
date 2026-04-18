package com.example.smartmoney.application.usecases.budget;

import com.example.smartmoney.application.dtos.requests.budgets.UpdateBudgetRequest;
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
public class UpdateBudgetUseCase {
    private final BudgetRepository budgetRepository;

    public BudgetItemResponse execute(UUID budgetId, UUID userId, UpdateBudgetRequest request) {
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

        // Update amount limit if provided
        if (request.getAmountLimit() != null) {
            budget.updateAmountLimit(request.getAmountLimit());
        }
        
        // Update spent amount if provided - can exceed amount_limit
        if (request.getSpent() != null) {
            budget.updateSpent(request.getSpent());
        } else {
            // If spent is not provided, keep the current spent value
            if (budget.getSpent() == null) {
                budget.setSpent(BigDecimal.ZERO);
            }
        }
        
        Budget updatedBudget = budgetRepository.save(budget);

        log.info("Budget updated: id={}, userId={}, newAmountLimit={}, newSpent={}", 
                budgetId, userId, updatedBudget.getAmountLimit(), updatedBudget.getSpent());

        // Calculate remaining amount and other metrics
        BigDecimal spent = updatedBudget.getSpent() != null ? updatedBudget.getSpent() : BigDecimal.ZERO;
        BigDecimal remaining = updatedBudget.calculateRemaining(spent);
        Double progressPercent = updatedBudget.calculateProgressPercent(spent);
        Budget.AlertLevel alertLevel = updatedBudget.getAlertLevel(spent);

        return BudgetItemResponse.builder()
                .budgetId(updatedBudget.getId())
                .userId(updatedBudget.getUserId())
                .category(updatedBudget.getCategory())
                .amountLimit(updatedBudget.getAmountLimit())
                .month(updatedBudget.getMonth())
                .year(updatedBudget.getYear())
                .spent(spent)
                .remaining(remaining)
                .progressPercent(progressPercent)
                .alertLevel(alertLevel)
                .createdAt(updatedBudget.getCreatedAt())
                .updatedAt(updatedBudget.getUpdatedAt())
                .build();
    }
}
