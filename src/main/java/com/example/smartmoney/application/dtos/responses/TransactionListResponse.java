package com.example.smartmoney.application.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionListResponse {
    private List<TransactionResponse> transactions;
    private int count;
    private Integer page;
    private Integer size;
    private Long totalElements;
    private Integer totalPages;
    private BigDecimal totalIncome;
    private BigDecimal totalExpenses;
    private BigDecimal totalBalance;
    
    public static TransactionListResponse of(List<TransactionResponse> transactions) {
        return TransactionListResponse.builder()
                .transactions(transactions)
                .count(transactions.size())
                .build();
    }
    
    public static TransactionListResponse of(List<TransactionResponse> transactions, int page, int size, long totalElements, BigDecimal totalIncome, BigDecimal totalExpenses, BigDecimal totalBalance) {
        int totalPages = (int) Math.ceil((double) totalElements / size);
        return TransactionListResponse.builder()
                .transactions(transactions)
                .count(transactions.size())
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .totalIncome(totalIncome)
                .totalExpenses(totalExpenses)
                .totalBalance(totalBalance)
                .build();
    }
}

