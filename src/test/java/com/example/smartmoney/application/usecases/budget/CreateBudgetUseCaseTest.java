package com.example.smartmoney.application.usecases.budget;

import com.example.smartmoney.application.dtos.requests.budgets.CreateBudgetRequest;
import com.example.smartmoney.application.dtos.responses.BudgetItemResponse;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
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
@DisplayName("CreateBudgetUseCase — mid-month spent backfill")
class CreateBudgetUseCaseTest {

    @Mock private BudgetRepository budgetRepository;
    @Mock private TransactionRepository transactionRepository;

    private final UUID userId = UUID.randomUUID();

    private CreateBudgetUseCase useCase() {
        return new CreateBudgetUseCase(budgetRepository, transactionRepository);
    }

    private CreateBudgetRequest request(String limit) {
        CreateBudgetRequest r = new CreateBudgetRequest();
        r.setCategory(Category.ENTERTAINMENT);
        r.setAmountLimit(new BigDecimal(limit));
        r.setMonth(5);
        r.setYear(2026);
        return r;
    }

    private Transaction expense(String amount) {
        return Transaction.create(userId, new Amount(new BigDecimal(amount)), TransactionType.EXPENSE,
                Category.ENTERTAINMENT, "test", LocalDateTime.of(2026, 5, 15, 12, 0));
    }

    @Test
    @DisplayName("backfills spent from expenses already logged this month (incl. the spike that prompted it)")
    void backfillsExistingMonthSpend() {
        when(budgetRepository.findByUserIdCategoryMonthYear(userId, Category.ENTERTAINMENT, 5, 2026))
                .thenReturn(Optional.empty());
        when(transactionRepository.findByUserIdWithFilters(
                eq(userId), isNull(), isNull(), any(), any(), eq(Category.ENTERTAINMENT), eq(TransactionType.EXPENSE)))
                .thenReturn(List.of(expense("5000000"), expense("1100000"))); // spike + a prior charge
        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> inv.getArgument(0));

        BudgetItemResponse response = useCase().execute(userId, request("3000000"));

        // spent = 6,100,000 (not 0) → remaining negative, budget already exceeded
        assertEquals(0, new BigDecimal("6100000").compareTo(response.getSpent()));
        assertEquals(0, new BigDecimal("-3100000").compareTo(response.getRemaining()));
        assertEquals(Budget.AlertLevel.EXCEEDED, response.getAlertLevel());
    }

    @Test
    @DisplayName("no prior spend this month → spent starts at zero (SAFE)")
    void noPriorSpendStartsAtZero() {
        when(budgetRepository.findByUserIdCategoryMonthYear(userId, Category.ENTERTAINMENT, 5, 2026))
                .thenReturn(Optional.empty());
        when(transactionRepository.findByUserIdWithFilters(
                eq(userId), isNull(), isNull(), any(), any(), eq(Category.ENTERTAINMENT), eq(TransactionType.EXPENSE)))
                .thenReturn(List.of());
        when(budgetRepository.save(any(Budget.class))).thenAnswer(inv -> inv.getArgument(0));

        BudgetItemResponse response = useCase().execute(userId, request("3000000"));

        assertEquals(0, BigDecimal.ZERO.compareTo(response.getSpent()));
        assertEquals(0, new BigDecimal("3000000").compareTo(response.getRemaining()));
        assertEquals(Budget.AlertLevel.SAFE, response.getAlertLevel());
    }
}
