package com.example.smartmoney.application.usecases.budget;

import com.example.smartmoney.application.dtos.requests.budgets.CreateBulkBudgetsRequest;
import com.example.smartmoney.application.dtos.responses.BudgetItemResponse;
import com.example.smartmoney.application.dtos.responses.BulkBudgetsResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreateBulkBudgetsUseCase {
    private final BudgetRepository budgetRepository;

    @Transactional
    public BulkBudgetsResponse execute(UUID userId, CreateBulkBudgetsRequest request) {
        if (userId == null) {
            throw new DomainException("User ID is required");
        }

        List<BudgetItemResponse> createdBudgets = new ArrayList<>();
        List<BulkBudgetsResponse.BudgetCreationError> failedItems = new ArrayList<>();

        for (CreateBulkBudgetsRequest.BudgetItem budgetItem : request.getBudgets()) {
            try {
                // Check if budget already exists for this user + category + month + year
                var existingBudget = budgetRepository.findByUserIdCategoryMonthYear(
                    userId,
                    budgetItem.getCategory(),
                    request.getMonth(),
                    request.getYear()
                );

                if (existingBudget.isPresent()) {
                    failedItems.add(
                        BulkBudgetsResponse.BudgetCreationError.builder()
                            .category(budgetItem.getCategory().toString())
                            .error(String.format(
                                "Budget already exists for category '%s' in month %d/%d",
                                budgetItem.getCategory(),
                                request.getMonth(),
                                request.getYear()
                            ))
                            .build()
                    );
                    continue;
                }

                // Create new budget
                Budget budget = Budget.create(
                    userId,
                    budgetItem.getCategory(),
                    budgetItem.getAmountLimit(),
                    request.getMonth(),
                    request.getYear()
                );

                Budget savedBudget = budgetRepository.save(budget);

                BudgetItemResponse response = BudgetItemResponse.builder()
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

                createdBudgets.add(response);
                log.info("Budget created: id={}, userId={}, category={}, month={}, year={}",
                    savedBudget.getId(), userId, budgetItem.getCategory(), request.getMonth(), request.getYear());

            } catch (Exception e) {
                log.warn("Failed to create budget for category: {}", budgetItem.getCategory(), e);
                failedItems.add(
                    BulkBudgetsResponse.BudgetCreationError.builder()
                        .category(budgetItem.getCategory().toString())
                        .error(e.getMessage())
                        .build()
                );
            }
        }

        return BulkBudgetsResponse.builder()
            .totalCreated(createdBudgets.size())
            .month(request.getMonth())
            .year(request.getYear())
            .budgets(createdBudgets)
            .failedItems(failedItems.isEmpty() ? null : failedItems)
            .build();
    }
}
