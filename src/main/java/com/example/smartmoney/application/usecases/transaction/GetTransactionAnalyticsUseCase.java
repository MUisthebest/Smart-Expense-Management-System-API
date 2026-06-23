package com.example.smartmoney.application.usecases.transaction;

import com.example.smartmoney.application.dtos.responses.TransactionAnalyticsResponse;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetTransactionAnalyticsUseCase {

    private final TransactionRepository transactionRepository;

    public TransactionAnalyticsResponse execute(UUID userId, int year, int month) {
        // 1. Get transactions by type and week of months
        List<Object[]> monthlyRaw = transactionRepository.getTransactionsByMonth(userId, year, month);

        // 2. Proportion of categories by month
        List<Object[]> proportionRaw = transactionRepository.getTransactionProportionByCategory(userId, year, month);

        // 3. Get monthly summary totals
        List<Object[]> totalsList = transactionRepository.getMonthlyTotals(userId, year, month);
        java.math.BigDecimal totalIncome = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalExpense = java.math.BigDecimal.ZERO;
        long totalTransactions = 0;

        if (totalsList != null && !totalsList.isEmpty()) {
            Object[] totalsRaw = totalsList.get(0);
            if (totalsRaw != null && totalsRaw.length > 0) {
                // Postgres query returns: [0] total_income, [1] total_expense, [2] total_transactions
                if (totalsRaw[0] != null) {
                    totalIncome = (java.math.BigDecimal) totalsRaw[0];
                }
                if (totalsRaw[1] != null) {
                    totalExpense = (java.math.BigDecimal) totalsRaw[1];
                }
                if (totalsRaw[2] != null) {
                    totalTransactions = ((Number) totalsRaw[2]).longValue();
                }
            }
        }

        return TransactionAnalyticsResponse.builder()
                .monthlyStats(mapToMonthlyStats(monthlyRaw))
                .categoryProportions(mapToProportions(proportionRaw))
                .monthlyTotalIncome(totalIncome)
                .monthlyTotalExpense(totalExpense)
                .monthlyTotalTransactions(totalTransactions)
                .build();
    }

    private List<TransactionAnalyticsResponse.MonthlyStat> mapToMonthlyStats(List<Object[]> raw) {

        Map<String, TransactionAnalyticsResponse.MonthlyStat> statsMap = new LinkedHashMap<>();

        for (Object[] row : raw) {

            String type = row[0].toString();
            String week = row[1].toString();
            int amount = ((Number) row[2]).intValue();

            TransactionAnalyticsResponse.MonthlyStat stat =
                    statsMap.computeIfAbsent(
                            week,
                            w -> new TransactionAnalyticsResponse.MonthlyStat(
                                    w,
                                    0,
                                    0
                            )
                    );

            if ("INCOME".equalsIgnoreCase(type)) {
                stat.setIncome(amount);
            } else if ("EXPENSE".equalsIgnoreCase(type)) {
                stat.setExpense(amount);
            }
        }

        return new ArrayList<>(statsMap.values());
    }
    private List<TransactionAnalyticsResponse.CategoryProportion> mapToProportions(List<Object[]> raw) {
        long total = raw.stream().mapToLong(r -> ((Number) r[1]).longValue()).sum();

        return raw.stream().map(row -> {
            String category = row[0].toString();
            long count = ((Number) row[1]).longValue();
            double percent = total == 0 ? 0 : (double) count * 100 / total;
            return new TransactionAnalyticsResponse.CategoryProportion(category, count, percent);
        }).collect(Collectors.toList());
    }
}