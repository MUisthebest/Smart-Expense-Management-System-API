package com.example.smartmoney.application.dtos.responses;

import lombok.Builder;
import lombok.Value;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Value
@Builder
public class WeeklyTransactionResponse {
    WeekData currentWeek;
    WeekData previousWeek;
    
    @Value
    @Builder
    public static class WeekData {
        LocalDate startDate;
        LocalDate endDate;
        BigDecimal totalIncome;
        BigDecimal totalExpense;
        Map<String, BigDecimal> expenseByCategory;
    }
}