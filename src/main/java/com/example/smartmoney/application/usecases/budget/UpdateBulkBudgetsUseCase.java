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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpdateBulkBudgetsUseCase {

    private final BudgetRepository budgetRepository;

    @Transactional
    public BulkBudgetsResponse execute(UUID userId, CreateBulkBudgetsRequest request) {
        if (userId == null) {
            throw new DomainException("User ID is required");
        }

        List<BudgetItemResponse> updatedBudgets = new ArrayList<>();
        List<BulkBudgetsResponse.BudgetCreationError> failedItems = new ArrayList<>();
        Set<String> uniqueCategories = new HashSet<>();

        for (CreateBulkBudgetsRequest.BudgetItem budgetItem : request.getBudgets()) {
            try {
                String categoryKey = budgetItem.getCategory().toString();

                if (!uniqueCategories.add(categoryKey)) {
                    failedItems.add(
                            BulkBudgetsResponse.BudgetCreationError.builder()
                                    .category(categoryKey)
                                    .error("Duplicate category in request")
                                    .build()
                    );
                    continue;
                }

                var existingBudget = budgetRepository.findByUserIdCategoryMonthYear(
                        userId,
                        budgetItem.getCategory(),
                        request.getMonth(),
                        request.getYear()
                );

                if (existingBudget.isEmpty()) {
                    failedItems.add(
                            BulkBudgetsResponse.BudgetCreationError.builder()
                                    .category(categoryKey)
                                    .error(String.format(
                                            "Budget not found for category '%s' in month %d/%d",
                                            budgetItem.getCategory(),
                                            request.getMonth(),
                                            request.getYear()
                                    ))
                                    .build()
                    );
                    continue;
                }

                Budget budget = existingBudget.get();

                budget.updateAmountLimit(budgetItem.getAmountLimit());

                Budget savedBudget = budgetRepository.save(budget);

                BudgetItemResponse response = BudgetItemResponse.builder()
                        .budgetId(savedBudget.getId())
                        .userId(savedBudget.getUserId())
                        .category(savedBudget.getCategory())
                        .amountLimit(savedBudget.getAmountLimit())
                        .month(savedBudget.getMonth())
                        .year(savedBudget.getYear())
                        .spent(BigDecimal.ZERO)
                        .remaining(savedBudget.getAmountLimit())
                        .progressPercent(0.0)
                        .alertLevel(Budget.AlertLevel.SAFE)
                        .createdAt(savedBudget.getCreatedAt())
                        .updatedAt(savedBudget.getUpdatedAt())
                        .build();

                updatedBudgets.add(response);

                log.info(
                        "Budget updated: id={}, userId={}, category={}, month={}, year={}",
                        savedBudget.getId(),
                        userId,
                        budgetItem.getCategory(),
                        request.getMonth(),
                        request.getYear()
                );

            } catch (Exception e) {
                log.warn("Failed to update budget for category: {}", budgetItem.getCategory(), e);

                failedItems.add(
                        BulkBudgetsResponse.BudgetCreationError.builder()
                                .category(budgetItem.getCategory().toString())
                                .error(e.getMessage())
                                .build()
                );
            }
        }

        return BulkBudgetsResponse.builder()
                .totalCreated(updatedBudgets.size())
                .month(request.getMonth())
                .year(request.getYear())
                .budgets(updatedBudgets)
                .failedItems(failedItems.isEmpty() ? null : failedItems)
                .build();
    }
}