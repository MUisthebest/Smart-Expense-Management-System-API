package com.example.smartmoney.application.dtos.responses;

import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    private Long id;
    private Long userId;
    private BigDecimal amount;
    private TransactionType type;
    private Category category;
    private String description;
    private String date;
    private String createdAt;
    private String updatedAt;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    
    public static TransactionResponse fromDomain(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .userId(transaction.getUserId())
                .amount(transaction.getAmount().getValue())
                .type(transaction.getType())
                .category(transaction.getCategory())
                .description(transaction.getDescription())
                .date(transaction.getDate() != null ? transaction.getDate().format(DATE_FORMATTER) : null)
                .createdAt(transaction.getCreatedAt() != null ? transaction.getCreatedAt().format(DATE_FORMATTER) : null)
                .updatedAt(transaction.getUpdatedAt() != null ? transaction.getUpdatedAt().format(DATE_FORMATTER) : null)
                .build();
    }
}
