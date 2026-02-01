package com.example.smartmoney.application.usecases.transaction;

import com.example.smartmoney.application.dtos.requests.transactions.UpdateTransactionRequest;
import com.example.smartmoney.application.dtos.responses.TransactionResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class UpdateTransactionUseCase {
    private final TransactionRepository transactionRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    
    public TransactionResponse execute(Long userId, Long transactionId, UpdateTransactionRequest request) {
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
        
        // Update transaction
        transaction.update(amount, request.getCategory(), request.getDescription(), transactionDate);
        
        // Save and return
        Transaction updatedTransaction = transactionRepository.save(transaction);
        return TransactionResponse.fromDomain(updatedTransaction);
    }
}
