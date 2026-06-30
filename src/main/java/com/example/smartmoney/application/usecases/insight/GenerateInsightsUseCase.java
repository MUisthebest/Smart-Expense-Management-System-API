package com.example.smartmoney.application.usecases.insight;

import com.example.smartmoney.application.dtos.responses.insight.InsightResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.insight.Insight;
import com.example.smartmoney.domain.models.insight.InsightType;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.services.SpendingInsightAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Pull endpoint backing GET /api/v1/insights (C2). Loads trailing history + current
 * budgets, runs the pure {@link SpendingInsightAnalyzer}, then orders results by the
 * user's {@code focusMode} trait. Read-only: this fully satisfies NOTIFY users, who
 * never receive a pushed suggestion.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GenerateInsightsUseCase {

    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final SpendingInsightAnalyzer analyzer;
    private final InsightNarrator narrator;

    /** Generate insights for the user's current month (as of {@code LocalDate.now()}). */
    @Transactional(readOnly = true)
    public List<InsightResponse> execute(UUID userId) {
        return execute(userId, LocalDate.now());
    }

    /** Overload taking an explicit reference date — keeps the use case deterministic for tests. */
    @Transactional(readOnly = true)
    public List<InsightResponse> execute(UUID userId, LocalDate asOf) {
        if (userId == null) {
            throw new DomainException("User ID is required");
        }
        YearMonth period = YearMonth.from(asOf);

        // Trailing window: current month + the prior 6 months feed the z-score baseline.
        LocalDateTime windowStart = period.minusMonths(SpendingInsightAnalyzer.TRAILING_WINDOW)
                .atDay(1).atStartOfDay();
        LocalDateTime windowEnd = asOf.atTime(23, 59, 59);

        List<Transaction> history = transactionRepository.findByUserIdWithFilters(
                userId, null, null, windowStart, windowEnd, null, null);

        List<Budget> budgets = budgetRepository.findByUserIdMonthYear(
                userId, period.getMonthValue(), period.getYear());

        List<Insight> insights = analyzer.analyze(history, budgets, asOf);

        Focus_Mode focus = userRepository.findById(userId).map(User::getFocusMode).orElse(null);
        insights.sort(orderingFor(focus));

        return insights.stream().map(this::toResponse).toList();
    }

    /** Map to the FE DTO, attaching a best-effort LLM narrative (P3-C; null when narration is off). */
    private InsightResponse toResponse(Insight insight) {
        InsightResponse response = InsightResponse.fromDomain(insight);
        response.setNarrative(narrator.narrate(insight));
        return response;
    }

    /**
     * Phase-1 ordering only (no dropping). Most urgent first; the {@code focusMode} trait
     * breaks ties: REDUCE_SPENDING surfaces overspend/pace insights ahead of the rest.
     */
    private Comparator<Insight> orderingFor(Focus_Mode focus) {
        Comparator<Insight> bySeverity = Comparator.comparingInt((Insight i) -> i.getSeverity().ordinal()).reversed();
        Comparator<Insight> byFocus = Comparator.comparingInt(i -> focusRank(i.getType(), focus));
        return byFocus.thenComparing(bySeverity)
                .thenComparing(i -> i.getType().name())
                .thenComparing(i -> i.getCategory() == null ? "" : i.getCategory().name());
    }

    private int focusRank(InsightType type, Focus_Mode focus) {
        if (focus == Focus_Mode.REDUCE_SPENDING) {
            // Cut-waste users: spending-control insights first, savings/info last.
            return switch (type) {
                case OVERSPEND_ANOMALY -> 0;
                case BUDGET_PACE -> 1;
                case CATEGORY_TREND -> 2;
                case RECURRING -> 3;
                case SAVINGS_CAPACITY -> 4;
            };
        }
        if (focus == Focus_Mode.SAVE_MORE) {
            // Save-mode users: surface saving opportunities and committed costs first.
            return switch (type) {
                case SAVINGS_CAPACITY -> 0;
                case RECURRING -> 1;
                case CATEGORY_TREND -> 2;
                case OVERSPEND_ANOMALY -> 3;
                case BUDGET_PACE -> 4;
            };
        }
        return 0; // TRACK_ONLY / cold-start: no type prioritization, severity order only
    }
}
