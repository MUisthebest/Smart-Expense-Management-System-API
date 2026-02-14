package com.example.smartmoney.application.dtos.requests.transactions;

import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionFilterRequest {
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private String startDate; // Format: dd/MM/yyyy HH:mm
    private String endDate;   // Format: dd/MM/yyyy HH:mm
    private Category category;
    private TransactionType type;
    private Integer page;     // Page number (0-indexed)
    private Integer size;     // Page size (default: 10)
}
