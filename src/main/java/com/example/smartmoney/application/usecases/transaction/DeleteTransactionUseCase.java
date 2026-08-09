package com.example.smartmoney.application.usecases.transaction;

import com.example.smartmoney.application.services.BudgetSyncService;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeleteTransactionUseCase {
    private final TransactionRepository transactionRepository;
    private final BudgetSyncService budgetSyncService;
    
    public void execute(UUID userId, UUID transactionId) {
        if (userId == null) {
            throw new DomainException("User ID is required");
        }
        if (transactionId == null) {
            throw new DomainException("Transaction ID is required");
        }
        
        // Find transaction to verify ownership before deletion
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new DomainException("Transaction not found"));
        
        // Verify ownership
        if (!transaction.getUserId().equals(userId)) {
            throw new DomainException("Unauthorized: Transaction does not belong to user");
        }

        TransactionType type = transaction.getType();
        Category category = transaction.getCategory();
        LocalDateTime date = transaction.getDate();
        
        // Delete transaction
        transactionRepository.deleteById(transactionId);

        if (type == TransactionType.EXPENSE && category != null && date != null) {
            budgetSyncService.syncBudgetSpent(userId, category, date.getMonthValue(), date.getYear());
        }
    }
}
