package com.example.smartmoney.application.usecases.transaction;

import com.example.smartmoney.application.dtos.requests.CreateTransactionRequest;
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
public class CreateTransactionUseCase {
    private final TransactionRepository transactionRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    
    public TransactionResponse execute(Long userId, CreateTransactionRequest request) {
        if (userId == null) {
            throw new DomainException("User ID is required");
        }
        
        // Parse date
        LocalDateTime transactionDate;
        try {
            transactionDate = LocalDateTime.parse(request.getDate(), DATE_FORMATTER);
        } catch (Exception e) {
            throw new DomainException("Invalid date format");
        }
        
        // Create amount value object
        Amount amount = new Amount(request.getAmount());
        
        // Create transaction domain object
        Transaction transaction = Transaction.create(
                userId,
                amount,
                request.getType(),
                request.getCategory(),
                request.getDescription(),
                transactionDate
        );
        
        // Save and return
        Transaction savedTransaction = transactionRepository.save(transaction);
        return TransactionResponse.fromDomain(savedTransaction);
    }
}
