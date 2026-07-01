package com.example.smartmoney.application.usecases.budget;

import com.example.smartmoney.application.dtos.requests.budgets.CreateBudgetRequest;
import com.example.smartmoney.application.dtos.responses.BudgetItemResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CreateBudgetUseCase {
    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;

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

        // Backfill spent from expenses already logged in this budget's month/category. A budget is
        // month-scoped, so a mid-month budget must account for what was already spent — otherwise it
        // starts at 0 and silently ignores existing spending (e.g. an overspend that prompted it).
        // New expenses created after this keep the counter live via CreateTransactionUseCase.
        BigDecimal spent = spentThisMonth(userId, request);
        budget.updateSpent(spent);

        Budget savedBudget = budgetRepository.save(budget);

        log.info("Budget created: id={}, userId={}, category={}, month={}, year={}, backfilledSpent={}",
                savedBudget.getId(), userId, request.getCategory(), request.getMonth(), request.getYear(), spent);

        BigDecimal savedSpent = savedBudget.getSpent();
        return BudgetItemResponse.builder()
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
    }

    /** Sum of EXPENSE amounts already logged in this category for the budget's month. */
    private BigDecimal spentThisMonth(UUID userId, CreateBudgetRequest request) {
        YearMonth period = YearMonth.of(request.getYear(), request.getMonth());
        LocalDateTime monthStart = period.atDay(1).atStartOfDay();
        LocalDateTime monthEnd = period.atEndOfMonth().atTime(23, 59, 59);

        return transactionRepository.findByUserIdWithFilters(
                        userId, null, null, monthStart, monthEnd, request.getCategory(), TransactionType.EXPENSE)
                .stream()
                .map(Transaction::getAmount)
                .filter(a -> a != null && a.getValue() != null)
                .map(a -> a.getValue())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
