package com.example.smartmoney.application.usecases.transaction;

import com.example.smartmoney.application.dtos.requests.transactions.CreateTransactionRequest;
import com.example.smartmoney.application.dtos.responses.TransactionResponse;
import com.example.smartmoney.application.usecases.insight.SuggestionProducer;
import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateTransactionUseCase {
    private final TransactionRepository transactionRepository;
    private final CreateNotificationUseCase createNotificationUseCase;
    private final BudgetRepository budgetRepository;
    private final SuggestionProducer suggestionProducer;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public TransactionResponse execute(UUID userId, CreateTransactionRequest request) {
        if (userId == null) {
            throw new DomainException("User ID is required");
        }

        LocalDateTime transactionDate;
        try {
            transactionDate = LocalDateTime.parse(request.getDate(), DATE_FORMATTER);
        } catch (Exception e) {
            throw new DomainException("Invalid date format");
        }

        Amount amount = new Amount(request.getAmount());

        Transaction transaction = Transaction.create(
                userId,
                amount,
                request.getType(),
                request.getCategory(),
                request.getDescription(),
                transactionDate);

        Transaction savedTransaction = transactionRepository.save(transaction);

        if (request.getType() == TransactionType.EXPENSE) {
            budgetRepository.findByUserIdCategoryMonthYear(
                    userId,
                    request.getCategory(),
                    transactionDate.getMonthValue(),
                    transactionDate.getYear()
            ).ifPresent(budget -> {
                BigDecimal currentSpent = budget.getSpent() != null ? budget.getSpent() : BigDecimal.ZERO;
                budget.updateSpent(currentSpent.add(amount.getValue()));
                budgetRepository.save(budget);
            });

            // Proactive insight → suggestion (workstream C). Best-effort: gated by the user's
            // interventionLevel trait and never allowed to break the transaction-create flow.
            suggestionProducer.maybeProduce(userId, request.getCategory(), transactionDate.toLocalDate());
        }

        String content = buildNotificationContent(request);
        createNotificationUseCase.createNotification(userId, content);

        return TransactionResponse.fromDomain(savedTransaction);
    }

    private String buildNotificationContent(CreateTransactionRequest request) {
        String typeText = request.getType() == TransactionType.EXPENSE
                ? "notification.expense"
                : "notification.income";
        return String.format(
                "notification.notification_done|%s|%s|%s",
                typeText,
                request.getAmount(),
                request.getCategory());
    }
}
