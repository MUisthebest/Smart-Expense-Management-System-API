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
    
    public static TransactionListResponse of(List<TransactionResponse> transactions) {
        return TransactionListResponse.builder()
                .transactions(transactions)
                .count(transactions.size())
                .build();
    }
}
