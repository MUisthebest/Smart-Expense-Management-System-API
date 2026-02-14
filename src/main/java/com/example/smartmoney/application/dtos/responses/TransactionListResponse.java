package com.example.smartmoney.application.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    
    public static TransactionListResponse of(List<TransactionResponse> transactions) {
        return TransactionListResponse.builder()
                .transactions(transactions)
                .count(transactions.size())
                .build();
    }
    
    public static TransactionListResponse of(List<TransactionResponse> transactions, int page, int size, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / size);
        return TransactionListResponse.builder()
                .transactions(transactions)
                .count(transactions.size())
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .build();
    }
}
