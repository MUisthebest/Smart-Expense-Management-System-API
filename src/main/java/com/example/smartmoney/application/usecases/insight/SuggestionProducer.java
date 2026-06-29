package com.example.smartmoney.application.usecases.insight;

import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.application.usecases.suggestion.SuggestionPayload;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.insight.Insight;
import com.example.smartmoney.domain.models.insight.InsightType;
import com.example.smartmoney.domain.models.insight.Severity;
import com.example.smartmoney.domain.models.suggestion.Suggestion;
import com.example.smartmoney.domain.models.suggestion.SuggestionType;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.SuggestionRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.services.SpendingInsightAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Proactive half of workstream C: on an expense create it runs the analyzer for the
 * affected category and — gated by the user's {@code interventionLevel} trait — may persist
 * a RAISE_BUDGET {@link Suggestion} and push a notification deep-linking to it.
 *
 * <p>Best-effort: every failure here is swallowed so a producer hiccup never affects the
 * user's transaction. Fatigue is controlled by (a) NOTIFY users getting zero pushes,
 * (b) the severity gate, and (c) one open ask per (category, period) via dedup.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SuggestionProducer {

    private final SpendingInsightAnalyzer analyzer;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final SuggestionRepository suggestionRepository;
    private final CreateNotificationUseCase createNotificationUseCase;
    private final ObjectMapper objectMapper;

    /**
     * Evaluate the affected category after an expense create and maybe raise a suggestion.
     * Never throws.
     */
    public void maybeProduce(UUID userId, Category affectedCategory, LocalDate asOf) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            Severity gate = pushGateFor(user == null ? null : user.getInterventionLevel());
            if (gate == null) {
                return; // NOTIFY (or unset) → pull-only user, no push
            }

            YearMonth period = YearMonth.from(asOf);
            LocalDateTime windowStart = period.minusMonths(SpendingInsightAnalyzer.TRAILING_WINDOW)
                    .atDay(1).atStartOfDay();
            LocalDateTime windowEnd = asOf.atTime(23, 59, 59);

            List<Transaction> history = transactionRepository.findByUserIdWithFilters(
                    userId, null, null, windowStart, windowEnd, null, null);
            List<Budget> budgets = budgetRepository.findByUserIdMonthYear(
                    userId, period.getMonthValue(), period.getYear());

            List<Insight> insights = analyzer.analyze(history, budgets, asOf);

            // Only a budget-tied insight maps to RAISE_BUDGET; keep the worst one that clears the gate.
            Optional<Insight> qualifying = insights.stream()
                    .filter(i -> i.getType() == InsightType.BUDGET_PACE)
                    .filter(i -> i.getCategory() == affectedCategory)
                    .filter(i -> i.getSeverity().atLeast(gate))
                    .max(Comparator.comparingInt(i -> i.getSeverity().ordinal()));
            if (qualifying.isEmpty()) {
                return;
            }
            Insight insight = qualifying.get();

            Budget budget = budgets.stream()
                    .filter(b -> b.getCategory() == affectedCategory)
                    .findFirst().orElse(null);
            if (budget == null || budget.getId() == null) {
                return; // no budget to raise
            }

            String dedupKey = "RAISE_BUDGET:" + affectedCategory.name() + ":" + period;
            // Pre-check: one open ask per topic. The partial-unique index is the hard backstop.
            if (suggestionRepository.findOpenByUserIdAndDedupKey(userId, dedupKey).isPresent()) {
                log.debug("[Suggestion] skip — open ask already exists for {}", dedupKey);
                return;
            }

            BigDecimal resolvedValue = resolveNewLimit(history, insight, affectedCategory, period);
            String payloadJson = objectMapper.writeValueAsString(buildPayload(insight, affectedCategory, budget, resolvedValue));

            Suggestion suggestion = Suggestion.create(userId, SuggestionType.RAISE_BUDGET,
                    payloadJson, dedupKey, null);

            Suggestion saved;
            try {
                saved = suggestionRepository.save(suggestion);
            } catch (Exception insertEx) {
                // A concurrent insert won the dedup race — treat as already asked.
                log.debug("[Suggestion] dedup insert conflict for {}: {}", dedupKey, insertEx.getMessage());
                return;
            }

            createNotificationUseCase.createNotification(
                    userId,
                    notificationContent(affectedCategory),
                    "app://suggestions/" + saved.getId());

            log.info("[Suggestion] raised RAISE_BUDGET id={} for user={} category={} resolvedValue={}",
                    saved.getId(), userId, affectedCategory, resolvedValue);

        } catch (Exception e) {
            // Never let suggestion production break the transaction-create flow.
            log.warn("[Suggestion] producer failed for user {} / {}: {}", userId, affectedCategory, e.getMessage());
        }
    }

    /** Maps the intervention trait to a push severity gate (null = no push). */
    private Severity pushGateFor(Intervention_Level level) {
        if (level == null) {
            return null;
        }
        return switch (level) {
            case NOTIFY -> null;          // pull-only
            case GENTLE -> Severity.WARN; // WARN/CRITICAL become pushed suggestions
            case HARD -> Severity.CRITICAL;
        };
    }

    /** Resolved new limit = max(robust trailing median, projected end-of-month). */
    private BigDecimal resolveNewLimit(List<Transaction> history, Insight insight,
                                       Category category, YearMonth period) {
        BigDecimal median = analyzer.trailing3MonthMedian(history, category, period);
        BigDecimal projected = toBigDecimal(insight.getMetrics().get("projectedEndOfMonth"));
        return median.max(projected);
    }

    private SuggestionPayload buildPayload(Insight insight, Category category, Budget budget, BigDecimal resolvedValue) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", insight.getType().name());
        snapshot.put("severity", insight.getSeverity().name());
        snapshot.put("code", insight.getCode());
        snapshot.put("period", insight.getPeriod() != null ? insight.getPeriod().toString() : null);
        snapshot.put("metrics", insight.getMetrics());

        SuggestionPayload.ProposedAction action = SuggestionPayload.ProposedAction.builder()
                .category(category)
                .budgetId(budget.getId())
                .resolvedValue(resolvedValue)
                .build();

        return SuggestionPayload.builder()
                .insightSnapshot(snapshot)
                .proposedAction(action)
                .build();
    }

    private String notificationContent(Category category) {
        // Pipe-delimited i18n key, consistent with CreateTransactionUseCase content style.
        return "notification.suggestion.raise_budget|" + category.name();
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(value.toString());
    }
}
