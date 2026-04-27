package com.example.smartmoney.application.dtos.responses.userincome;

import com.example.smartmoney.domain.models.user.UserIncome;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class UserIncomeResponse {
    private String userId;
    private Integer netIncome;
    private Integer usableIncome;
    private Integer safeSpending;
    private String currency;
    private String calculationNote;
    private Boolean autoInvestSurplus;
    private Instant lastCalculatedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public static UserIncomeResponse fromDomain(UserIncome income) {
        return UserIncomeResponse.builder()
                .userId(income.getId().toString())
                .netIncome(income.getNetIncome())
                .usableIncome(income.getUsableIncome())
                .safeSpending(income.getSafeSpending())
                .currency(income.getCurrency())
                .calculationNote(income.getCalculationNote())
                .autoInvestSurplus(income.getAutoInvestSurplus())
                .lastCalculatedAt(income.getLastCalculatedAt())
                .createdAt(income.getCreatedAt())
                .updatedAt(income.getUpdatedAt())
                .build();
    }
}
