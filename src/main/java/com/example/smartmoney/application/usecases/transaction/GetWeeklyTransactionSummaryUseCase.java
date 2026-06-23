package com.example.smartmoney.application.usecases.transaction;
import com.example.smartmoney.application.dtos.responses.WeeklyTransactionResponse;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GetWeeklyTransactionSummaryUseCase {

    private final TransactionRepository transactionRepository;

    public WeeklyTransactionResponse execute(UUID userId) {

        LocalDate today = LocalDate.now();

        LocalDate currentWeekStart =
                today.with(DayOfWeek.MONDAY);

        LocalDate currentWeekEnd =
                currentWeekStart.plusDays(6);

        LocalDate previousWeekStart =
                currentWeekStart.minusWeeks(1);

        LocalDate previousWeekEnd =
                currentWeekEnd.minusWeeks(1);

        WeeklyTransactionResponse.WeekData currentWeek =
                buildWeek(
                        userId,
                        currentWeekStart,
                        currentWeekEnd
                );

        WeeklyTransactionResponse.WeekData previousWeek =
                buildWeek(
                        userId,
                        previousWeekStart,
                        previousWeekEnd
                );

        return WeeklyTransactionResponse.builder()
                .currentWeek(currentWeek)
                .previousWeek(previousWeek)
                .build();
    }

    private WeeklyTransactionResponse.WeekData buildWeek(
            UUID userId,
            LocalDate start,
            LocalDate end
    ) {

        BigDecimal income =
                transactionRepository.getTotalIncomeBetween(
                        userId,
                        start,
                        end
                );

        BigDecimal expense =
                transactionRepository.getTotalExpenseBetween(
                        userId,
                        start,
                        end
                );

        Map<String, BigDecimal> categoryMap =
                transactionRepository
                        .getExpenseByCategoryBetween(
                                userId,
                                start,
                                end
                        )
                        .stream()
                        .collect(Collectors.toMap(
                                r -> (String) r[0],
                                r -> (BigDecimal) r[1]
                        ));

        return WeeklyTransactionResponse.WeekData.builder()
                .startDate(start)
                .endDate(end)
                .totalIncome(income)
                .totalExpense(expense)
                .expenseByCategory(categoryMap)
                .build();
    }
}