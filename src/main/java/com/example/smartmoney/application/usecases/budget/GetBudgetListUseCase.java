package com.example.smartmoney.application.usecases.budget;

import com.example.smartmoney.application.dtos.responses.BudgetItemResponse;
import com.example.smartmoney.application.dtos.responses.BudgetListResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetBudgetListUseCase {
    private final BudgetRepository budgetRepository;

    public BudgetListResponse execute(UUID userId, Integer month, Integer year) {
        if (userId == null) {
            throw new DomainException("User ID is required");
        }
        if (month == null || month < 1 || month > 12) {
            throw new DomainException("Month must be between 1 and 12");
        }
        if (year == null || year < 1900) {
            throw new DomainException("Year must be valid (>= 1900)");
        }

        List<Budget> budgets = budgetRepository.findByUserIdMonthYear(userId, month, year);

        log.info("Retrieved {} budgets for userId={}, month={}, year={}", 
                budgets.size(), userId, month, year);

        List<BudgetItemResponse> items = budgets.stream()
            .map(budget -> {
                // TODO: Calculate spent amount from transactions for this budget's category and month/year
                BigDecimal spent = BigDecimal.ZERO;
                BigDecimal remaining = budget.calculateRemaining(spent);
                Double progressPercent = budget.calculateProgressPercent(spent);
                Budget.AlertLevel alertLevel = budget.getAlertLevel(spent);

                return BudgetItemResponse.builder()
                    .budgetId(budget.getId())
                    .userId(budget.getUserId())
                    .category(budget.getCategory())
                    .amountLimit(budget.getAmountLimit())
                    .month(budget.getMonth())
                    .year(budget.getYear())
                    .spent(spent)
                    .remaining(remaining)
                    .progressPercent(progressPercent)
                    .alertLevel(alertLevel)
                    .createdAt(budget.getCreatedAt())
                    .updatedAt(budget.getUpdatedAt())
                    .build();
            })
            .collect(Collectors.toList());

        return BudgetListResponse.builder()
                .month(month)
                .year(year)
                .items(items)
                .build();
    }
}
