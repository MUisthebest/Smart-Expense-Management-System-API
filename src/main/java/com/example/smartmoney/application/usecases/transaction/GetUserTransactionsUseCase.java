package com.example.smartmoney.application.usecases.transaction;

import com.example.smartmoney.application.dtos.requests.transactions.TransactionFilterRequest;
import com.example.smartmoney.application.dtos.responses.TransactionListResponse;
import com.example.smartmoney.application.dtos.responses.TransactionResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetUserTransactionsUseCase {
    private final TransactionRepository transactionRepository;
    
    public TransactionListResponse execute(UUID userId, TransactionFilterRequest filter) {
        if (userId == null) {
            throw new DomainException("User ID is required");
        }
        
        List<Transaction> transactions;
        
        // Check if any filters are provided
        if (filter != null && hasAnyFilter(filter)) {
            // Parse dates if provided
            LocalDateTime startDate = parseDate(filter.getStartDate());
            LocalDateTime endDate = parseDate(filter.getEndDate());
            
            // Find transactions with filters
            transactions = transactionRepository.findByUserIdWithFilters(
                userId,
                filter.getMinAmount(),
                filter.getMaxAmount(),
                startDate,
                endDate,
                filter.getCategory(),
                filter.getType()
            );
        } else {
            // Find all transactions for user without filters
            transactions = transactionRepository.findByUserId(userId);
        }
        
        // Convert to response DTOs
        List<TransactionResponse> transactionResponses = transactions.stream()
                .map(TransactionResponse::fromDomain)
                .collect(Collectors.toList());
        
        return TransactionListResponse.of(transactionResponses);
    }
    
    private boolean hasAnyFilter(TransactionFilterRequest filter) {
        return filter.getMinAmount() != null ||
               filter.getMaxAmount() != null ||
               filter.getStartDate() != null ||
               filter.getEndDate() != null ||
               filter.getCategory() != null ||
               filter.getType() != null;
    }
    
    private LocalDateTime parseDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            return LocalDateTime.parse(dateStr, formatter);
        } catch (Exception e) {
            throw new DomainException("Invalid date format. Expected format: dd/MM/yyyy HH:mm");
        }
    }
}
