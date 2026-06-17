package com.example.smartmoney.application.dtos.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
public class TransactionAnalyticsResponse {

    private List<MonthlyStat> monthlyStats;

    private List<CategoryProportion> categoryProportions;

    private List<DailyStat> dailyStats;

    private long monthlyTotalTransactions;

    private java.math.BigDecimal monthlyTotalExpense;

    private java.math.BigDecimal monthlyTotalIncome;

    @Data
    @AllArgsConstructor
    @Builder
    public static class DailyStat {
        private int day;
        private double income;
        private double expense;
    }

    @Data
    @AllArgsConstructor
    public static class MonthlyStat {
        private String week;
        private double income;
        private double expense;
    }

    @Data
    @AllArgsConstructor
    public static class CategoryProportion {
        private String category;       // Food , Drink ,...
        private long count;
        private double percentage;
    }
}