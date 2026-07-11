package com.example.smartmoney.application.usecases.budget;

import com.example.smartmoney.application.dtos.requests.budgets.CreateBulkBudgetsRequest;
import com.example.smartmoney.application.dtos.responses.BudgetItemResponse;
import com.example.smartmoney.application.dtos.responses.BulkBudgetsResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
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
    private final TransactionRepository transactionRepository;

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

                    // Backfill spent from expenses already logged this month/category, mirroring
                    // CreateBudgetUseCase — otherwise a mid-month bulk plan starts at 0 and silently
                    // ignores existing spending. Existing budgets keep their live spent counter.
                    budget.updateSpent(spentThisMonth(userId, budgetItem.getCategory(), request));

                    log.info(
                            "Creating new budget: userId={}, category={}, month={}, year={}",
                            userId,
                            budgetItem.getCategory(),
                            request.getMonth(),
                            request.getYear()
                    );
                }

                Budget savedBudget = budgetRepository.save(budget);

                BigDecimal savedSpent = savedBudget.getSpent() != null
                        ? savedBudget.getSpent() : BigDecimal.ZERO;

                BudgetItemResponse response = BudgetItemResponse.builder()
                        .budgetId(savedBudget.getId())
                        .userId(savedBudget.getUserId())
                        .category(savedBudget.getCategory())
                        .amountLimit(savedBudget.getAmountLimit())
                        .month(savedBudget.getMonth())
                        .year(savedBudget.getYear())
                        .spent(savedSpent)
                        .remaining(savedBudget.calculateRemaining(savedSpent))
                        .progressPercent(savedBudget.calculateProgressPercent(savedSpent))
                        .alertLevel(savedBudget.getAlertLevel(savedSpent))
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

    /** Sum of EXPENSE amounts already logged in this category for the request's month. */
    private BigDecimal spentThisMonth(UUID userId, Category category, CreateBulkBudgetsRequest request) {
        YearMonth period = YearMonth.of(request.getYear(), request.getMonth());
        LocalDateTime monthStart = period.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = period.atEndOfMonth().atTime(23, 59, 59);

        return transactionRepository.findByUserIdWithFilters(
                        userId, null, null, monthStart, monthEnd, category, TransactionType.EXPENSE)
                .stream()
                .map(Transaction::getAmount)
                .filter(a -> a != null && a.getValue() != null)
                .map(a -> a.getValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}