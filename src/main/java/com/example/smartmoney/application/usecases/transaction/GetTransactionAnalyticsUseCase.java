package com.example.smartmoney.application.usecases.transaction;

import com.example.smartmoney.application.dtos.requests.transactions.AnalyticsRequest;
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

    public TransactionAnalyticsResponse execute(UUID userId, AnalyticsRequest request) {
        int year = (request != null && request.getYear() != null) ? request.getYear() : java.time.LocalDateTime.now().getYear();
        String type = (request != null && request.getType() != null) ? request.getType() : "MONTH";

        if ("YEAR".equalsIgnoreCase(type)) {
            // 1. Get transactions by type and month for the year
            List<Object[]> yearlyRaw = transactionRepository.getTransactionsByYear(userId, year);

            // 2. Proportion of categories by year
            List<Object[]> proportionRaw = transactionRepository.getTransactionProportionByCategoryForYear(userId, year);

            // 3. Get yearly summary totals
            List<Object[]> totalsList = transactionRepository.getYearlyTotals(userId, year);
            java.math.BigDecimal totalIncome = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalExpense = java.math.BigDecimal.ZERO;
            long totalTransactions = 0;

            if (totalsList != null && !totalsList.isEmpty()) {
                Object[] totalsRaw = totalsList.get(0);
                if (totalsRaw != null && totalsRaw.length > 0) {
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
                    .monthlyStats(mapToYearlyStats(yearlyRaw))
                    .categoryProportions(mapToProportions(proportionRaw))
                    .monthlyTotalIncome(totalIncome)
                    .monthlyTotalExpense(totalExpense)
                    .monthlyTotalTransactions(totalTransactions)
                    .build();
        } else {
            // "MONTH" or "DAY" view
            int month = (request != null && request.getMonth() != null) ? request.getMonth() : java.time.LocalDateTime.now().getMonthValue();

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

            // 4. Daily totals for calendar indicators
            List<Object[]> dailyRaw = transactionRepository.getDailyTotals(userId, year, month);

            return TransactionAnalyticsResponse.builder()
                    .monthlyStats(mapToMonthlyStats(monthlyRaw))
                    .categoryProportions(mapToProportions(proportionRaw))
                    .dailyStats(mapToDailyStats(dailyRaw))
                    .monthlyTotalIncome(totalIncome)
                    .monthlyTotalExpense(totalExpense)
                    .monthlyTotalTransactions(totalTransactions)
                    .build();
        }
    }

    private List<TransactionAnalyticsResponse.MonthlyStat> mapToMonthlyStats(List<Object[]> raw) {
        Map<String, TransactionAnalyticsResponse.MonthlyStat> statsMap = new LinkedHashMap<>();

        for (Object[] row : raw) {
            String type = row[0].toString();
            String week = row[1].toString();
            double amount = ((Number) row[2]).doubleValue();

            TransactionAnalyticsResponse.MonthlyStat stat =
                    statsMap.computeIfAbsent(
                            week,
                            w -> new TransactionAnalyticsResponse.MonthlyStat(
                                    w,
                                    0.0,
                                    0.0
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

    private List<TransactionAnalyticsResponse.MonthlyStat> mapToYearlyStats(List<Object[]> raw) {
        Map<String, TransactionAnalyticsResponse.MonthlyStat> statsMap = new LinkedHashMap<>();

        String[] monthNames = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        for (String mName : monthNames) {
            statsMap.put(mName, new TransactionAnalyticsResponse.MonthlyStat(mName, 0.0, 0.0));
        }

        for (Object[] row : raw) {
            String type = row[0].toString();
            int monthVal = ((Number) row[1]).intValue();
            double amount = ((Number) row[2]).doubleValue();

            if (monthVal >= 1 && monthVal <= 12) {
                String mName = monthNames[monthVal - 1];
                TransactionAnalyticsResponse.MonthlyStat stat = statsMap.get(mName);
                if (stat != null) {
                    if ("INCOME".equalsIgnoreCase(type)) {
                        stat.setIncome(amount);
                    } else if ("EXPENSE".equalsIgnoreCase(type)) {
                        stat.setExpense(amount);
                    }
                }
            }
        }

        return new ArrayList<>(statsMap.values());
    }

    private List<TransactionAnalyticsResponse.DailyStat> mapToDailyStats(List<Object[]> raw) {
        List<TransactionAnalyticsResponse.DailyStat> dailyStats = new ArrayList<>();
        if (raw == null) return dailyStats;

        for (Object[] row : raw) {
            int day = ((Number) row[0]).intValue();
            double income = ((Number) row[1]).doubleValue();
            double expense = ((Number) row[2]).doubleValue();
            dailyStats.add(new TransactionAnalyticsResponse.DailyStat(day, income, expense));
        }
        return dailyStats;
    }

    private List<TransactionAnalyticsResponse.CategoryProportion> mapToProportions(List<Object[]> raw) {
        double totalExpenseAmount = raw.stream()
                .mapToDouble(r -> r[2] != null ? ((Number) r[2]).doubleValue() : 0.0)
                .sum();

        return raw.stream().map(row -> {
            String category = row[0].toString();
            long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            double amount = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
            double percent = totalExpenseAmount == 0 ? 0 : amount * 100 / totalExpenseAmount;
            return new TransactionAnalyticsResponse.CategoryProportion(category, count, percent);
        }).collect(Collectors.toList());
    }
}