package com.example.smartmoney.application.services;

import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BudgetSyncServiceTest {

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private TransactionRepository transactionRepository;

    private BudgetSyncService budgetSyncService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        budgetSyncService = new BudgetSyncService(budgetRepository, transactionRepository);
    }

    @Test
    @DisplayName("syncBudgetSpent correctly sums expenses and updates budget spent amount")
    void syncBudgetSpent_updatesBudgetSpent() {
        Category category = Category.FOOD;
        int month = 8;
        int year = 2026;

        Budget budget = Budget.create(userId, category, new BigDecimal("5000000"), month, year);
        budget.updateSpent(new BigDecimal("1000000")); // current stale value

        when(budgetRepository.findByUserIdCategoryMonthYear(userId, category, month, year))
                .thenReturn(Optional.of(budget));

        Transaction t1 = Transaction.create(userId, new Amount(new BigDecimal("300000")), TransactionType.EXPENSE, category, "Lunch", LocalDateTime.of(2026, 8, 1, 12, 0));
        Transaction t2 = Transaction.create(userId, new Amount(new BigDecimal("200000")), TransactionType.EXPENSE, category, "Dinner", LocalDateTime.of(2026, 8, 2, 19, 0));

        when(transactionRepository.findByUserIdWithFilters(
                eq(userId), eq(null), eq(null), any(), any(), eq(category), eq(TransactionType.EXPENSE)))
                .thenReturn(List.of(t1, t2));

        budgetSyncService.syncBudgetSpent(userId, category, month, year);

        assertEquals(new BigDecimal("500000"), budget.getSpent());
        verify(budgetRepository).save(budget);
    }

    @Test
    @DisplayName("syncBudgetSpent sets spent to ZERO when no expense transactions exist")
    void syncBudgetSpent_setsSpentToZeroWhenNoTransactions() {
        Category category = Category.FOOD;
        int month = 8;
        int year = 2026;

        Budget budget = Budget.create(userId, category, new BigDecimal("5000000"), month, year);
        budget.updateSpent(new BigDecimal("1000000"));

        when(budgetRepository.findByUserIdCategoryMonthYear(userId, category, month, year))
                .thenReturn(Optional.of(budget));

        when(transactionRepository.findByUserIdWithFilters(
                eq(userId), eq(null), eq(null), any(), any(), eq(category), eq(TransactionType.EXPENSE)))
                .thenReturn(List.of());

        budgetSyncService.syncBudgetSpent(userId, category, month, year);

        assertEquals(BigDecimal.ZERO, budget.getSpent());
        verify(budgetRepository).save(budget);
    }

    @Test
    @DisplayName("syncBudgetSpent does nothing if budget does not exist")
    void syncBudgetSpent_doesNothingIfNoBudget() {
        Category category = Category.FOOD;
        int month = 8;
        int year = 2026;

        when(budgetRepository.findByUserIdCategoryMonthYear(userId, category, month, year))
                .thenReturn(Optional.empty());

        budgetSyncService.syncBudgetSpent(userId, category, month, year);

        verify(transactionRepository, never()).findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any());
        verify(budgetRepository, never()).save(any());
    }
}
