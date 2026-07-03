package com.example.smartmoney.application.usecases.insight;

import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.domain.models.insight.Insight;
import com.example.smartmoney.domain.models.insight.Severity;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.services.SpendingInsightAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Wave 3 (item 6): the month-rollover recap. Evaluates the month that just <b>closed</b> (the one
 * before {@code asOf}) for the two facts only a completed month can establish — an income drop and
 * negative cashflow — and pushes one notification per finding. These catch what the reactive,
 * transaction-triggered producer structurally cannot: the trigger for an income drop is a
 * transaction that <em>didn't happen</em>.
 *
 * <p>Notification-only, like the Wave 1 transaction-anomaly pings: never a suggestion card, never
 * in {@code GET /insights} (mid-month both facts would be misleading). Severity-gated by the
 * {@code interventionLevel} trait exactly like pushed suggestions (NOTIFY → nothing, GENTLE →
 * WARN+, HARD → CRITICAL only). Pure per-user logic taking an explicit {@code asOf}, separated
 * from the cron trigger ({@code MonthlyRecapScheduler}) so it is unit-testable and demo-runnable
 * without Spring scheduling.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SendMonthlyRecapUseCase {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final SpendingInsightAnalyzer analyzer;
    private final CreateNotificationUseCase createNotificationUseCase;
    private final InsightNarrator narrator;

    /**
     * Run the recap of the month before {@code asOf} for one user. Returns whether anything was
     * pushed (false for gated users or an unremarkable month).
     */
    public boolean sendFor(UUID userId, LocalDate asOf) {
        User user = userRepository.findById(userId).orElse(null);
        Severity gate = pushGateFor(user == null ? null : user.getInterventionLevel());
        if (gate == null) {
            return false; // NOTIFY (or unset) → pull-only user, no recap ping
        }

        YearMonth closed = YearMonth.from(asOf).minusMonths(1);
        // The closed month plus the trailing window before it feed the income-drop baseline.
        LocalDateTime windowStart = closed.minusMonths(SpendingInsightAnalyzer.TRAILING_WINDOW)
                .atDay(1).atStartOfDay();
        LocalDateTime windowEnd = closed.atEndOfMonth().atTime(23, 59, 59);
        List<Transaction> history = transactionRepository.findByUserIdWithFilters(
                userId, null, null, windowStart, windowEnd, null, null);

        int sent = 0;
        sent += pushIfQualifying(userId, gate,
                analyzer.detectIncomeDrop(history, closed).orElse(null),
                "notification.insight.income_drop");
        sent += pushIfQualifying(userId, gate,
                analyzer.detectCashflowNegative(history, closed).orElse(null),
                "notification.insight.cashflow_negative");

        if (sent > 0) {
            log.info("[Recap] monthly recap for user {}: {} finding(s) on {}", userId, sent, closed);
        }
        return sent > 0;
    }

    /** Push one recap ping when the insight exists and clears the user's severity gate. */
    private int pushIfQualifying(UUID userId, Severity gate, Insight insight, String fallbackCode) {
        if (insight == null || !insight.getSeverity().atLeast(gate)) {
            return 0;
        }
        String narrative = narrator.narrate(insight);
        createNotificationUseCase.createNotification(
                userId, narrative != null ? narrative : fallbackCode, "app://transactions");
        return 1;
    }

    /** Same trait → push-gate mapping as the {@code SuggestionProducer} (null = no push). */
    private Severity pushGateFor(Intervention_Level level) {
        if (level == null) {
            return null;
        }
        return switch (level) {
            case NOTIFY -> null;
            case GENTLE -> Severity.WARN;
            case HARD -> Severity.CRITICAL;
        };
    }
}
