package com.example.smartmoney.application.usecases.transaction;

import com.example.smartmoney.application.services.BudgetSyncService;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteTransactionUseCaseTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BudgetSyncService budgetSyncService;

    private DeleteTransactionUseCase deleteTransactionUseCase;

    private final UUID userId = UUID.randomUUID();
    private final UUID txId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        deleteTransactionUseCase = new DeleteTransactionUseCase(transactionRepository, budgetSyncService);
    }

    @Test
    @DisplayName("Deleting an EXPENSE transaction triggers budget sync")
    void deleteExpenseTransaction_syncsBudget() {
        LocalDateTime date = LocalDateTime.of(2026, 8, 5, 10, 0);
        Transaction tx = Transaction.create(userId, new Amount(new BigDecimal("100000")), TransactionType.EXPENSE, Category.FOOD, "Lunch", date);

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        deleteTransactionUseCase.execute(userId, txId);

        verify(transactionRepository).deleteById(txId);
        verify(budgetSyncService).syncBudgetSpent(userId, Category.FOOD, 8, 2026);
    }

    @Test
    @DisplayName("Deleting an INCOME transaction does not trigger budget sync")
    void deleteIncomeTransaction_doesNotSyncBudget() {
        LocalDateTime date = LocalDateTime.of(2026, 8, 5, 10, 0);
        Transaction tx = Transaction.create(userId, new Amount(new BigDecimal("5000000")), TransactionType.INCOME, Category.SALARY, "Paycheck", date);

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        deleteTransactionUseCase.execute(userId, txId);

        verify(transactionRepository).deleteById(txId);
        verify(budgetSyncService, never()).syncBudgetSpent(any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Deleting transaction owned by another user throws DomainException")
    void deleteTransaction_unauthorizedUser_throwsException() {
        UUID otherUser = UUID.randomUUID();
        Transaction tx = Transaction.create(otherUser, new Amount(new BigDecimal("100000")), TransactionType.EXPENSE, Category.FOOD, "Lunch", LocalDateTime.now());

        when(transactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        assertThrows(DomainException.class, () -> deleteTransactionUseCase.execute(userId, txId));
        verify(transactionRepository, never()).deleteById(any());
        verify(budgetSyncService, never()).syncBudgetSpent(any(), any(), anyInt(), anyInt());
    }
}
