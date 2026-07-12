package com.example.smartmoney.application.usecases.budget;

import com.example.smartmoney.application.dtos.requests.budgets.CreateBulkBudgetsRequest;
import com.example.smartmoney.application.dtos.responses.BulkBudgetsResponse;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpsertBulkBudgetsUseCase — mid-month spent backfill")
class UpsertBulkBudgetsUseCaseTest {

    @Mock private BudgetRepository budgetRepository;
    @Mock private TransactionRepository transactionRepository;

    private UpsertBulkBudgetsUseCase useCase;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new UpsertBulkBudgetsUseCase(budgetRepository, transactionRepository);
    }

    private CreateBulkBudgetsRequest request(Category category, String limit) {
        CreateBulkBudgetsRequest.BudgetItem item = new CreateBulkBudgetsRequest.BudgetItem();
        item.setCategory(category);
        item.setAmountLimit(new BigDecimal(limit));
        CreateBulkBudgetsRequest request = new CreateBulkBudgetsRequest();
        request.setBudgets(List.of(item));
        request.setMonth(6);
        request.setYear(2026);
        return request;
    }

    @Test
    @DisplayName("a newly created budget backfills spent from this month's expenses")
    void newBudgetBackfillsSpent() {
        when(budgetRepository.findByUserIdCategoryMonthYear(any(), any(), anyInt(), anyInt()))
                .thenReturn(Optional.empty()); // no existing budget → create path
        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> inv.getArgument(0));

        Transaction spent = Transaction.create(userId, new Amount(new BigDecimal("300000")),
                TransactionType.EXPENSE, Category.FOOD, "lunch", LocalDateTime.of(2026, 6, 5, 12, 0));
        when(transactionRepository.findByUserIdWithFilters(
                eq(userId), any(), any(), any(), any(), eq(Category.FOOD), eq(TransactionType.EXPENSE)))
                .thenReturn(List.of(spent));

        BulkBudgetsResponse response = useCase.execute(userId, request(Category.FOOD, "1000000"));

        assertEquals(1, response.getBudgets().size());
        var item = response.getBudgets().get(0);
        assertEquals(0, new BigDecimal("300000").compareTo(item.getSpent()));         // backfilled
        assertEquals(0, new BigDecimal("700000").compareTo(item.getRemaining()));      // 1M − 300k
        assertEquals(Budget.AlertLevel.SAFE, item.getAlertLevel());                    // 30% used (< 50%)
    }
}
