package com.example.smartmoney.application.usecases.transaction;

import com.example.smartmoney.application.dtos.responses.TransactionListResponse;
import com.example.smartmoney.application.dtos.responses.TransactionResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetUserTransactionsUseCase {
    private final TransactionRepository transactionRepository;
    
    public TransactionListResponse execute(UUID userId) {
        if (userId == null) {
            throw new DomainException("User ID is required");
        }
        
        // Find all transactions for user
        List<Transaction> transactions = transactionRepository.findByUserId(userId);
        
        // Convert to response DTOs
        List<TransactionResponse> transactionResponses = transactions.stream()
                .map(TransactionResponse::fromDomain)
                .collect(Collectors.toList());
        
        return TransactionListResponse.of(transactionResponses);
    }
}
