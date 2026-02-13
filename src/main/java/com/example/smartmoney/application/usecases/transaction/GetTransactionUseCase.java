package com.example.smartmoney.application.usecases.transaction;

import com.example.smartmoney.application.dtos.responses.TransactionResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetTransactionUseCase {
    private final TransactionRepository transactionRepository;
    
    public TransactionResponse execute(UUID userId, Long transactionId) {
        if (userId == null) {
            throw new DomainException("User ID is required");
        }
        if (transactionId == null) {
            throw new DomainException("Transaction ID is required");
        }
        
        // Find transaction
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new DomainException("Transaction not found"));
        
        // Verify ownership
        if (!transaction.getUserId().equals(userId)) {
            throw new DomainException("Unauthorized: Transaction does not belong to user");
        }
        
        return TransactionResponse.fromDomain(transaction);
    }
}
