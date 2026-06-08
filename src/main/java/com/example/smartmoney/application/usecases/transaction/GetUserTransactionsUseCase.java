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
        
        // Set default pagination values
        int page = (filter != null && filter.getPage() != null) ? filter.getPage() : 0;
        int size = (filter != null && filter.getSize() != null) ? filter.getSize() : 10;
        
        // Set default sort values
        String sortBy = (filter != null && filter.getSortBy() != null) ? filter.getSortBy() : "date";
        String sortOrder = (filter != null && filter.getSortOrder() != null) ? filter.getSortOrder() : "DESC";
        
        // Validate sort parameters
        validateSortParameters(sortBy, sortOrder);
        
        List<Transaction> transactions;
        long totalElements = 0;
        
        // Check if any filters are provided
        if (filter != null && hasAnyFilters(filter)) {
            // Parse dates if provided
            LocalDateTime startDate = parseDate(filter.getStartDate());
            LocalDateTime endDate = parseDate(filter.getEndDate());
            
            // Get total count for pagination
            totalElements = transactionRepository.countByUserIdWithFilters(
                userId,
                filter.getMinAmount(),
                filter.getMaxAmount(),
                startDate,
                endDate,
                filter.getCategory(),
                filter.getType()
            );
            
            // Find transactions with filters and pagination
            transactions = transactionRepository.findByUserIdWithFilters(
                userId,
                filter.getMinAmount(),
                filter.getMaxAmount(),
                startDate,
                endDate,
                filter.getCategory(),
                filter.getType(),
                sortBy,
                sortOrder,
                page,
                size
            );
        } else {
            // Find all transactions for user without filters, with sorting and pagination
            List<Transaction> allTransactions = transactionRepository.findByUserId(userId);
            totalElements = allTransactions.size();
            
            transactions = transactionRepository.findByUserIdSorted(userId, sortBy, sortOrder, page, size);
        }
        
        // Convert to response DTOs
        List<TransactionResponse> transactionResponses = transactions.stream()
                .map(TransactionResponse::fromDomain)
                .collect(Collectors.toList());
        
        return TransactionListResponse.of(transactionResponses, page, size, totalElements);
    }
    
    private void validateSortParameters(String sortBy, String sortOrder) {
        // Valid sort fields
        String[] validSortFields = {"date", "amount", "category", "type", "description"};
        boolean validSortBy = false;
        for (String field : validSortFields) {
            if (field.equalsIgnoreCase(sortBy)) {
                validSortBy = true;
                break;
            }
        }
        
        if (!validSortBy) {
            throw new DomainException("Invalid sort field: " + sortBy + ". Valid fields are: date, amount, category, type, description");
        }
        
        // Valid sort orders
        if (!sortOrder.equalsIgnoreCase("ASC") && !sortOrder.equalsIgnoreCase("DESC")) {
            throw new DomainException("Invalid sort order: " + sortOrder + ". Valid orders are: ASC, DESC");
        }
    }
    
    private List<Transaction> sortTransactions(List<Transaction> transactions, String sortBy, String sortOrder) {
        boolean ascending = sortOrder.equalsIgnoreCase("ASC");
        
        return transactions.stream()
                .sorted((t1, t2) -> {
                    int comparison = 0;
                    
                    switch (sortBy.toLowerCase()) {
                        case "amount":
                            comparison = t1.getAmount().getValue().compareTo(t2.getAmount().getValue());
                            break;
                        case "category":
                            comparison = t1.getCategory().toString().compareTo(t2.getCategory().toString());
                            break;
                        case "type":
                            comparison = t1.getType().toString().compareTo(t2.getType().toString());
                            break;
                        case "description":
                            comparison = (t1.getDescription() != null ? t1.getDescription() : "")
                                    .compareTo(t2.getDescription() != null ? t2.getDescription() : "");
                            break;
                        case "date":
                        default:
                            comparison = t1.getDate().compareTo(t2.getDate());
                            break;
                    }
                    
                    return ascending ? comparison : -comparison;
                })
                .collect(Collectors.toList());
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
    
    private boolean hasAnyFilters(TransactionFilterRequest filter) {
        return filter.getMinAmount() != null ||
               filter.getMaxAmount() != null ||
               filter.getStartDate() != null ||
               filter.getEndDate() != null ||
               filter.getCategory() != null ||
               filter.getType() != null;
    }
}
