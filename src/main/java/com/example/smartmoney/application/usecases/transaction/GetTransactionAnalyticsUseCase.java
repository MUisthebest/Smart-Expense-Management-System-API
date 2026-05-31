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

    public TransactionAnalyticsResponse execute(int year, int month) {
        // 1. Get transactions by type and week of months
        List<Object[]> monthlyRaw = transactionRepository.getTransactionsByMonth(year, month);

        // 2. Proportion of categories by month
        List<Object[]> proportionRaw = transactionRepository.getTransactionProportionByCategory(year, month);

        return TransactionAnalyticsResponse.builder()
                .monthlyStats(mapToMonthlyStats(monthlyRaw))
                .categoryProportions(mapToProportions(proportionRaw))
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