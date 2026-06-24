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
public class UpsertBulkBudgetsUseCase {

    private final BudgetRepository budgetRepository;

    @Transactional
    public BulkBudgetsResponse execute(UUID userId, CreateBulkBudgetsRequest request) {
        if (userId == null) {
            throw new DomainException("User ID is required");
        }

        List<BudgetItemResponse> savedBudgets = new ArrayList<>();
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

                Budget budget;

                if (existingBudget.isPresent()) {
                    budget = existingBudget.get();

                    budget.updateAmountLimit(budgetItem.getAmountLimit());

                    log.info(
                            "Updating existing budget: userId={}, category={}, month={}, year={}",
                            userId,
                            budgetItem.getCategory(),
                            request.getMonth(),
                            request.getYear()
                    );
                } else {
                    budget = Budget.create(
                            userId,
                            budgetItem.getCategory(),
                            budgetItem.getAmountLimit(),
                            request.getMonth(),
                            request.getYear()
                    );

                    log.info(
                            "Creating new budget: userId={}, category={}, month={}, year={}",
                            userId,
                            budgetItem.getCategory(),
                            request.getMonth(),
                            request.getYear()
                    );
                }

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

                savedBudgets.add(response);

            } catch (Exception e) {
                log.warn("Failed to save budget for category: {}", budgetItem.getCategory(), e);

                failedItems.add(
                        BulkBudgetsResponse.BudgetCreationError.builder()
                                .category(budgetItem.getCategory().toString())
                                .error(e.getMessage())
                                .build()
                );
            }
        }

        return BulkBudgetsResponse.builder()
                .totalCreated(savedBudgets.size())
                .month(request.getMonth())
                .year(request.getYear())
                .budgets(savedBudgets)
                .failedItems(failedItems.isEmpty() ? null : failedItems)
                .build();
    }
}