package com.example.smartmoney.application.usecases.budget;

import com.example.smartmoney.application.dtos.requests.budgets.CreateBudgetRequest;
import com.example.smartmoney.application.dtos.responses.BudgetItemResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreateBudgetUseCase {
    private final BudgetRepository budgetRepository;

    public BudgetItemResponse execute(UUID userId, CreateBudgetRequest request) {
        if (userId == null) {
            throw new DomainException("User ID is required");
        }

        // Check if budget already exists for this user + category + month + year
        var existingBudget = budgetRepository.findByUserIdCategoryMonthYear(
            userId,
            request.getCategory(),
            request.getMonth(),
            request.getYear()
        );

        if (existingBudget.isPresent()) {
            throw new DomainException(
                String.format(
                    "Budget already exists for category '%s' in month %d/%d",
                    request.getCategory(),
                    request.getMonth(),
                    request.getYear()
                )
            );
        }

        // Create new budget
        Budget budget = Budget.create(
            userId,
            request.getCategory(),
            request.getAmountLimit(),
            request.getMonth(),
            request.getYear()
        );

        Budget savedBudget = budgetRepository.save(budget);
        
        log.info("Budget created: id={}, userId={}, category={}, month={}, year={}",
                savedBudget.getId(), userId, request.getCategory(), request.getMonth(), request.getYear());

        return BudgetItemResponse.builder()
                .budgetId(savedBudget.getId())
                .userId(savedBudget.getUserId())
                .category(savedBudget.getCategory())
                .amountLimit(savedBudget.getAmountLimit())
                .month(savedBudget.getMonth())
                .year(savedBudget.getYear())
                .spent(java.math.BigDecimal.ZERO)
                .remaining(savedBudget.getAmountLimit())
                .progressPercent(0.0)
                .alertLevel(Budget.AlertLevel.SAFE)
                .createdAt(savedBudget.getCreatedAt())
                .updatedAt(savedBudget.getUpdatedAt())
                .build();
    }
}
