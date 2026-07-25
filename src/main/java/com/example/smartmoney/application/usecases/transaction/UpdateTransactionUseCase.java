package com.example.smartmoney.application.usecases.transaction;

import com.example.smartmoney.application.dtos.requests.transactions.UpdateTransactionRequest;
import com.example.smartmoney.application.dtos.responses.TransactionResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class UpdateTransactionUseCase {
    private final TransactionRepository transactionRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    
    public TransactionResponse execute(UUID userId, UUID transactionId, UpdateTransactionRequest request) {
        if (userId == null) {
            throw new DomainException("User ID is required");
        }
        if (transactionId == null) {
            throw new DomainException("Transaction ID is required");
        }
        
        // Find existing transaction
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new DomainException("Transaction not found"));
        
        // Verify ownership
        if (!transaction.getUserId().equals(userId)) {
            throw new DomainException("Unauthorized: Transaction does not belong to user");
        }
        
        // Parse date if provided
        LocalDateTime transactionDate = null;
        if (request.getDate() != null && !request.getDate().isEmpty()) {
            try {
                transactionDate = LocalDateTime.parse(request.getDate(), DATE_FORMATTER);
            } catch (Exception e) {
                throw new DomainException("Invalid date format");
            }
        }
        
        // Create amount value object if provided
        Amount amount = null;
        if (request.getAmount() != null) {
            amount = new Amount(request.getAmount());
        }
        
        TransactionType type = request.getType();
        Category category = request.getCategory();

        // If transaction type is INCOME (or changing to INCOME) and category was not supplied in request,
        // fallback category to Category.OTHER if current category is an expense-only category
        TransactionType effectiveType = type != null ? type : transaction.getType();
        if (effectiveType == TransactionType.INCOME && category == null) {
            if (transaction.getCategory() == null || isExpenseCategory(transaction.getCategory())) {
                category = Category.OTHER;
            }
        }

        // Update transaction
        transaction.update(amount, type, category, request.getDescription(), transactionDate);
        
        // Save and return
        Transaction updatedTransaction = transactionRepository.save(transaction);
        return TransactionResponse.fromDomain(updatedTransaction);
    }

    private boolean isExpenseCategory(Category category) {
        return category == Category.FOOD
                || category == Category.TRANSPORTATION
                || category == Category.CLOTHING
                || category == Category.UTILITIES
                || category == Category.ENTERTAINMENT
                || category == Category.HEALTH
                || category == Category.EDUCATION
                || category == Category.SHOPPING
                || category == Category.PROJECT_CONTRIBUTION;
    }
}

