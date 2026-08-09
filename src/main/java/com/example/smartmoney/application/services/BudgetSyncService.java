package com.example.smartmoney.application.services;

import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BudgetSyncService {
    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Recalculates and updates the budget's spent amount for a specific user, category, month, and year
     * based on all EXPENSE transactions in that period.
     */
    public void syncBudgetSpent(UUID userId, Category category, int month, int year) {
        if (userId == null || category == null) {
            return;
        }

        budgetRepository.findByUserIdCategoryMonthYear(userId, category, month, year)
                .ifPresent(budget -> {
                    YearMonth period = YearMonth.of(year, month);
                    LocalDateTime monthStart = period.atDay(1).atStartOfDay();
                    LocalDateTime monthEnd = period.atEndOfMonth().atTime(23, 59, 59);

                    BigDecimal newSpent = transactionRepository.findByUserIdWithFilters(
                                    userId, null, null, monthStart, monthEnd, category, TransactionType.EXPENSE)
                            .stream()
                            .map(Transaction::getAmount)
                            .filter(a -> a != null && a.getValue() != null)
                            .map(Amount::getValue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    budget.updateSpent(newSpent);
                    budgetRepository.save(budget);
                });
    }
}
