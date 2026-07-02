package com.example.smartmoney.application.usecases.insight;

import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.application.usecases.suggestion.SuggestionPayload;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.insight.Insight;
import com.example.smartmoney.domain.models.insight.InsightType;
import com.example.smartmoney.domain.models.insight.Severity;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.suggestion.Suggestion;
import com.example.smartmoney.domain.models.suggestion.SuggestionTopicStat;
import com.example.smartmoney.domain.models.suggestion.SuggestionType;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.SuggestionRepository;
import com.example.smartmoney.domain.repositories.SuggestionTopicStatRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.services.SpendingInsightAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 * Proactive half of workstream C, extended for Phase 2. On a transaction create it runs the
 * analyzer and — gated by the user's {@code interventionLevel} trait — may persist a
 * {@link Suggestion} and push a deep-linked notification:
 * <ul>
 *   <li>EXPENSE → {@code RAISE_BUDGET} (BUDGET_PACE overshoot, category has a budget) or
 *       {@code CREATE_BUDGET} (OVERSPEND_ANOMALY, category has no budget) — P2-B.</li>
 *   <li>INCOME → {@code CONTRIBUTE_TO_PROJECT} (SAVINGS_CAPACITY headroom toward an active
 *       saving project, amount scaled by {@code savingPace}) — P2-B/D/E.</li>
 * </ul>
 *
 * <p>Best-effort: every failure here is swallowed so a producer hiccup never affects the user's
 * transaction. Fatigue is controlled in layers: (a) NOTIFY users get zero pushes, (b) the severity
 * gate, (c) one open ask per topic via dedup, (d) dismissal suppression (P2-A), (e) a per-topic
 * cooldown, and (f) a per-user daily push cap (P3-A). Controls (d) and (e) fully skip the ask; the
 * daily cap (f) instead <em>demotes</em> the push — the suggestion card is still created (actionable
 * in-app) but a single daily roll-up ping replaces the individual push, so overflow is never silently
 * lost. Accepted suggestions don't count toward the cap (engagement isn't fatigue). A CRITICAL
 * escalation bypasses (d)–(f) entirely, so a genuine emergency is never silenced.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SuggestionProducer {

    /** Dismissals of a topic at/above this count suppress it unless severity escalates to CRITICAL. */
    static final int SUPPRESS_AFTER_DISMISSALS = 2;
    /** Headroom over the median actual when proposing a reduced budget limit (REDUCE_BUDGET). */
    private static final BigDecimal REDUCE_HEADROOM = new BigDecimal("1.15");

    private final SpendingInsightAnalyzer analyzer;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final SuggestionRepository suggestionRepository;
    private final SuggestionTopicStatRepository topicStatRepository;
    private final CreateNotificationUseCase createNotificationUseCase;
    private final ObjectMapper objectMapper;
    private final SuggestionFatigueProperties fatigue;
    private final Clock clock;
    private final InsightNarrator narrator;

    /**
     * Evaluate the affected category after an expense create and maybe raise a budget-related
     * suggestion (RAISE_BUDGET if the category has a budget, else CREATE_BUDGET). Never throws.
     */
    public void maybeProduce(UUID userId, Category affectedCategory, LocalDate asOf) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            Severity gate = pushGateFor(user == null ? null : user.getInterventionLevel());
            if (gate == null) {
                return; // NOTIFY (or unset) → pull-only user, no push
            }

            YearMonth period = YearMonth.from(asOf);
            List<Transaction> history = loadHistory(userId, asOf, period);
            List<Budget> budgets = budgetRepository.findByUserIdMonthYear(
                    userId, period.getMonthValue(), period.getYear());
            List<Insight> insights = analyzer.analyze(history, budgets, asOf);

            Budget budget = budgets.stream()
                    .filter(b -> b.getCategory() == affectedCategory)
                    .findFirst().orElse(null);

            Candidate candidate;
            if (budget != null && budget.getId() != null) {
                // Overshoot wins; on a healthy budget a persistent underspend can suggest trimming it.
                candidate = raiseBudgetCandidate(insights, affectedCategory, budget, gate, history, period, user.getIncome());
                if (candidate == null) {
                    candidate = reduceBudgetCandidate(insights, affectedCategory, budget, period);
                }
            } else {
                // Anomaly (already overspending) wins; else a rising trend can suggest a first limit.
                candidate = createBudgetCandidate(insights, affectedCategory, gate, history, period, user.getIncome());
                if (candidate == null) {
                    candidate = setCategoryLimitCandidate(insights, affectedCategory, gate, history, period, user.getIncome());
                }
            }

            persistAndPush(userId, candidate);
        } catch (Exception e) {
            log.warn("[Suggestion] expense producer failed for user {} / {}: {}",
                    userId, affectedCategory, e.getMessage());
        }
    }

    /**
     * Evaluate savings headroom after an income create and maybe suggest contributing to an
     * active saving project (P2-B/D/E). Never throws. SAVINGS_CAPACITY is an INFO insight, so the
     * gate here is presence-of-push (GENTLE/HARD), not severity.
     */
    public void maybeProduceSavingsContribution(UUID userId, LocalDate asOf) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || pushGateFor(user.getInterventionLevel()) == null) {
                return; // pull-only / unset → no push
            }

            YearMonth period = YearMonth.from(asOf);
            List<Transaction> history = loadHistory(userId, asOf, period);
            List<Budget> budgets = budgetRepository.findByUserIdMonthYear(
                    userId, period.getMonthValue(), period.getYear());
            List<Insight> insights = analyzer.analyze(history, budgets, asOf);

            Insight capacity = insights.stream()
                    .filter(i -> i.getType() == InsightType.SAVINGS_CAPACITY)
                    .findFirst().orElse(null);
            if (capacity == null) {
                return;
            }
            BigDecimal disposable = toBigDecimal(capacity.getMetrics().get("disposable"));
            if (disposable.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            Project target = pickContributionTarget(userId);
            if (target == null) {
                return; // no active saving project with room to receive
            }

            // P2-E: scale the share of disposable by savingPace, then cap at what the project needs.
            BigDecimal amount = scaleByPace(disposable, user.getSavingPace())
                    .min(target.remaining().amount())
                    .setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            persistAndPush(userId, contributeCandidate(capacity, target, amount, period));
        } catch (Exception e) {
            log.warn("[Suggestion] savings producer failed for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Wave 1: on an expense create, synchronously check the just-saved transaction for two
     * per-transaction anomalies — a {@code LARGE_TRANSACTION} outlier and a {@code DUPLICATE_CHARGE} —
     * and push an informational notification for each (no suggestion, no accept action). Gated by
     * push presence (any non-NOTIFY user), best-effort, never throws. Immediacy is the point, so it
     * runs on the create path rather than a schedule.
     */
    public void maybeNotifyTransactionAnomalies(UUID userId, Transaction saved) {
        try {
            if (saved == null || saved.getType() != TransactionType.EXPENSE || saved.getDate() == null) {
                return;
            }
            User user = userRepository.findById(userId).orElse(null);
            if (user == null || pushGateFor(user.getInterventionLevel()) == null) {
                return; // pull-only / unset → no push
            }

            LocalDate asOf = saved.getDate().toLocalDate();
            List<Transaction> history = loadHistory(userId, asOf, YearMonth.from(asOf));

            analyzer.detectDuplicateCharge(history, saved).ifPresent(insight ->
                    notifyInsight(userId, insight, "notification.insight.duplicate_charge|" + insight.getCategory().name()));
            analyzer.detectLargeTransaction(history, saved).ifPresent(insight ->
                    notifyInsight(userId, insight, "notification.insight.large_transaction|" + insight.getCategory().name()));
        } catch (Exception e) {
            log.warn("[Suggestion] transaction-anomaly notify failed for user {}: {}", userId, e.getMessage());
        }
    }

    /** Push a notification-only insight (no suggestion card), narrated when available. */
    private void notifyInsight(UUID userId, Insight insight, String fallbackCode) {
        createNotificationUseCase.createNotification(userId, pushText(insight, fallbackCode), "app://transactions");
    }

    // ---- candidate builders --------------------------------------------------------------

    /** RAISE_BUDGET: BUDGET_PACE overshoot on a category that already has a budget. */
    private Candidate raiseBudgetCandidate(List<Insight> insights, Category category, Budget budget,
                                           Severity gate, List<Transaction> history, YearMonth period,
                                           BigDecimal income) {
        Insight insight = bestQualifying(insights, InsightType.BUDGET_PACE, category, gate);
        if (insight == null) {
            return null;
        }
        BigDecimal resolvedValue = capByIncome(
                analyzer.trailing3MonthMedian(history, category, period)
                        .max(toBigDecimal(insight.getMetrics().get("projectedEndOfMonth"))),
                income);
        // After capping, a "raise" that isn't above the current limit is meaningless — don't ask.
        if (resolvedValue.compareTo(budget.getAmountLimit()) <= 0) {
            return null;
        }

        SuggestionPayload.ProposedAction action = SuggestionPayload.ProposedAction.builder()
                .category(category)
                .budgetId(budget.getId())
                .resolvedValue(resolvedValue)
                .build();

        return Candidate.builder()
                .type(SuggestionType.RAISE_BUDGET)
                .severity(insight.getSeverity())
                .dedupKey("RAISE_BUDGET:" + category.name() + ":" + period)
                .payloadJson(payloadJson(insight, action))
                .notificationContent(pushText(insight, "notification.suggestion.raise_budget|" + category.name()))
                .build();
    }

    /** CREATE_BUDGET: OVERSPEND_ANOMALY on a category with no budget to anchor spending. */
    private Candidate createBudgetCandidate(List<Insight> insights, Category category,
                                            Severity gate, List<Transaction> history, YearMonth period,
                                            BigDecimal income) {
        Insight insight = bestQualifying(insights, InsightType.OVERSPEND_ANOMALY, category, gate);
        if (insight == null) {
            return null;
        }
        // Anchor on the historical norm, but never below what's already spent this month: CreateBudget
        // backfills spent from the month's existing expenses, so a limit under the overspend that
        // triggered this would be born already-EXCEEDED. The current-month total is carried on the
        // anomaly insight as "actual" (SpendingInsightAnalyzer#detectOverspendAnomalies).
        BigDecimal actualThisMonth = toBigDecimal(insight.getMetrics().get("actual"));
        BigDecimal resolvedValue = capByIncome(
                analyzer.trailing3MonthMedian(history, category, period), income)
                .max(actualThisMonth);
        if (resolvedValue.compareTo(BigDecimal.ZERO) <= 0) {
            return null; // no robust baseline to propose a limit from
        }

        SuggestionPayload.ProposedAction action = SuggestionPayload.ProposedAction.builder()
                .category(category)
                .month(period.getMonthValue())
                .year(period.getYear())
                .resolvedValue(resolvedValue)
                .build();

        return Candidate.builder()
                .type(SuggestionType.CREATE_BUDGET)
                .severity(insight.getSeverity())
                .dedupKey("CREATE_BUDGET:" + category.name() + ":" + period)
                .payloadJson(payloadJson(insight, action))
                .notificationContent(pushText(insight, "notification.suggestion.create_budget|" + category.name()))
                .build();
    }

    /** SET_CATEGORY_LIMIT: CATEGORY_TREND on a budgetless category — set a first anchoring limit. */
    private Candidate setCategoryLimitCandidate(List<Insight> insights, Category category, Severity gate,
                                                List<Transaction> history, YearMonth period, BigDecimal income) {
        Insight insight = bestQualifying(insights, InsightType.CATEGORY_TREND, category, gate);
        if (insight == null) {
            return null;
        }
        // Anchor the first limit on the trailing norm (robust to a one-off), capped by income.
        BigDecimal resolvedValue = capByIncome(analyzer.trailing3MonthMedian(history, category, period), income);
        if (resolvedValue.compareTo(BigDecimal.ZERO) <= 0) {
            return null; // no robust baseline to propose a limit from
        }

        SuggestionPayload.ProposedAction action = SuggestionPayload.ProposedAction.builder()
                .category(category)
                .month(period.getMonthValue())
                .year(period.getYear())
                .resolvedValue(resolvedValue)
                .build();

        return Candidate.builder()
                .type(SuggestionType.SET_CATEGORY_LIMIT)
                .severity(insight.getSeverity())
                .dedupKey("SET_CATEGORY_LIMIT:" + category.name() + ":" + period)
                .payloadJson(payloadJson(insight, action))
                .notificationContent(pushText(insight, "notification.suggestion.set_category_limit|" + category.name()))
                .build();
    }

    /**
     * REDUCE_BUDGET: BUDGET_UNDERSPEND on a budgeted category. INFO by nature, so it isn't gated by
     * severity (the caller already confirmed the user gets pushes) — it mirrors the savings nudge.
     */
    private Candidate reduceBudgetCandidate(List<Insight> insights, Category category, Budget budget, YearMonth period) {
        Insight insight = insights.stream()
                .filter(i -> i.getType() == InsightType.BUDGET_UNDERSPEND)
                .filter(i -> i.getCategory() == category)
                .findFirst().orElse(null);
        if (insight == null) {
            return null;
        }
        // Trim to the median actual plus a little headroom, so the tighter limit still breathes.
        BigDecimal resolvedValue = toBigDecimal(insight.getMetrics().get("medianActual"))
                .multiply(REDUCE_HEADROOM).setScale(2, RoundingMode.HALF_UP);
        // A "reduce" that isn't below the current limit is meaningless — don't ask.
        if (resolvedValue.compareTo(BigDecimal.ZERO) <= 0 || resolvedValue.compareTo(budget.getAmountLimit()) >= 0) {
            return null;
        }

        SuggestionPayload.ProposedAction action = SuggestionPayload.ProposedAction.builder()
                .category(category)
                .budgetId(budget.getId())
                .resolvedValue(resolvedValue)
                .build();

        return Candidate.builder()
                .type(SuggestionType.REDUCE_BUDGET)
                .severity(insight.getSeverity())
                .dedupKey("REDUCE_BUDGET:" + category.name() + ":" + period)
                .payloadJson(payloadJson(insight, action))
                .notificationContent(pushText(insight, "notification.suggestion.reduce_budget|" + category.name()))
                .build();
    }

    /** CONTRIBUTE_TO_PROJECT: SAVINGS_CAPACITY headroom toward a specific active project. */
    private Candidate contributeCandidate(Insight insight, Project target, BigDecimal amount, YearMonth period) {
        SuggestionPayload.ProposedAction action = SuggestionPayload.ProposedAction.builder()
                .projectId(target.getId().value())
                .resolvedValue(amount)
                .build();

        return Candidate.builder()
                .type(SuggestionType.CONTRIBUTE_TO_PROJECT)
                .severity(insight.getSeverity())
                .dedupKey("CONTRIBUTE_TO_PROJECT:" + target.getId().value() + ":" + period)
                .payloadJson(payloadJson(insight, action))
                .notificationContent(pushText(insight, "notification.suggestion.contribute_to_project|" + target.getName()))
                .build();
    }

    // ---- shared persist + push -----------------------------------------------------------

    private void persistAndPush(UUID userId, Candidate candidate) throws Exception {
        if (candidate == null) {
            return;
        }
        // Pre-check: one open ask per topic. The partial-unique index is the hard backstop.
        if (suggestionRepository.findOpenByUserIdAndDedupKey(userId, candidate.dedupKey).isPresent()) {
            log.debug("[Suggestion] skip — open ask already exists for {}", candidate.dedupKey);
            return;
        }
        // Fatigue ceiling. A CRITICAL escalation bypasses all of it. For non-CRITICAL asks:
        //  - dismissal suppression and the per-topic cooldown fully skip the ask;
        //  - the daily cap only DEMOTES the push — the card is still created (so it stays actionable
        //    in the in-app list) but a single daily roll-up ping replaces the per-suggestion push,
        //    so nothing is silently lost. See docs/redesign-phase2-3-plan.md §P3-A.
        boolean overflow = false;           // over the daily cap → create the card, no individual push
        boolean firstOverflowToday = false; // only the first crossing sends the one roll-up ping
        if (candidate.severity != Severity.CRITICAL) {
            Instant now = clock.instant();
            SuggestionTopicStat stat = topicStatRepository.find(userId, candidate.dedupKey).orElse(null);
            if (isSuppressed(stat)) {
                log.debug("[Suggestion] suppressed (dismissal fatigue) for {}", candidate.dedupKey);
                return;
            }
            if (isInCooldown(stat, now)) {
                log.debug("[Suggestion] in cooldown ({}h) for {}", fatigue.getCooldownHours(), candidate.dedupKey);
                return;
            }
            int cap = fatigue.getDailyCap();
            if (cap > 0) {
                int used = suggestionRepository.countTowardDailyCap(userId, startOfDay(now));
                if (used >= cap) {
                    overflow = true;
                    firstOverflowToday = (used == cap); // exactly at the boundary = first over today
                }
            }
        }

        Suggestion suggestion = Suggestion.create(
                userId, candidate.type, candidate.payloadJson, candidate.dedupKey, null);

        Suggestion saved;
        try {
            saved = suggestionRepository.save(suggestion);
        } catch (Exception insertEx) {
            // A concurrent insert won the dedup race — treat as already asked.
            log.debug("[Suggestion] dedup insert conflict for {}: {}", candidate.dedupKey, insertEx.getMessage());
            return;
        }

        if (overflow) {
            // Daily-cap overflow: the card is in the in-app list; don't ping per-suggestion. Send one
            // roll-up notification per day (the digest pattern) so the user knows there's more to review.
            if (firstOverflowToday) {
                createNotificationUseCase.createNotification(
                        userId, "notification.digest.pending_suggestions", "app://suggestions");
            }
            log.info("[Suggestion] {} id={} created without push (daily cap {} reached — rolled into digest)",
                    candidate.type, saved.getId(), fatigue.getDailyCap());
            return;
        }

        createNotificationUseCase.createNotification(
                userId, candidate.notificationContent, "app://suggestions/" + saved.getId());
        recordPush(userId, candidate.dedupKey); // P3-A: stamp the topic so the cooldown can space repeats

        log.info("[Suggestion] raised {} id={} for user={} dedupKey={}",
                candidate.type, saved.getId(), userId, candidate.dedupKey);
    }

    /** True when the topic has been dismissed enough times to back off (CRITICAL handled by caller). */
    private boolean isSuppressed(SuggestionTopicStat stat) {
        return stat != null && stat.getDismissCount() >= SUPPRESS_AFTER_DISMISSALS;
    }

    /** P3-A: still inside the per-topic cooldown window since the last push? */
    private boolean isInCooldown(SuggestionTopicStat stat, Instant now) {
        if (stat == null || stat.getLastPushedAt() == null || fatigue.getCooldownHours() <= 0) {
            return false;
        }
        Instant readyAt = stat.getLastPushedAt().plus(Duration.ofHours(fatigue.getCooldownHours()));
        return now.isBefore(readyAt);
    }

    /** Start-of-day instant in the producer's clock zone — the window for the P3-A daily cap. */
    private Instant startOfDay(Instant now) {
        return LocalDate.ofInstant(now, clock.getZone()).atStartOfDay(clock.getZone()).toInstant();
    }

    /** Best-effort: stamp the topic's last-pushed time so the P3-A cooldown can space out repeats. */
    private void recordPush(UUID userId, String dedupKey) {
        try {
            SuggestionTopicStat stat = topicStatRepository.find(userId, dedupKey)
                    .orElseGet(() -> SuggestionTopicStat.start(userId, dedupKey));
            stat.recordPush(clock.instant());
            topicStatRepository.save(stat);
        } catch (Exception e) {
            log.warn("[Suggestion] could not stamp last-pushed for topic {}: {}", dedupKey, e.getMessage());
        }
    }

    // ---- helpers -------------------------------------------------------------------------

    private List<Transaction> loadHistory(UUID userId, LocalDate asOf, YearMonth period) {
        LocalDateTime windowStart = period.minusMonths(SpendingInsightAnalyzer.TRAILING_WINDOW)
                .atDay(1).atStartOfDay();
        LocalDateTime windowEnd = asOf.atTime(23, 59, 59);
        return transactionRepository.findByUserIdWithFilters(
                userId, null, null, windowStart, windowEnd, null, null);
    }

    /** The worst insight of a type+category that clears the push gate, if any. */
    private Insight bestQualifying(List<Insight> insights, InsightType type, Category category, Severity gate) {
        return insights.stream()
                .filter(i -> i.getType() == type)
                .filter(i -> i.getCategory() == category)
                .filter(i -> i.getSeverity().atLeast(gate))
                .max(Comparator.comparingInt(i -> i.getSeverity().ordinal()))
                .orElse(null);
    }

    /** Active, non-group saving project with the nearest deadline that still has room to receive. */
    private Project pickContributionTarget(UUID userId) {
        return projectRepository.findAllByUser(new UserId(userId)).stream()
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE)
                .filter(p -> !p.isSubPersonal())
                .filter(p -> p.remaining().amount().compareTo(BigDecimal.ZERO) > 0)
                .min(Comparator.comparing(Project::getDeadline,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
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

    /**
     * Sanity ceiling for a proposed budget limit: never suggest a monthly limit larger than the
     * user's stated monthly income — you can't spend money you don't have. When income is unset
     * (cold-start), the value passes through unbounded (the trailing-median anchor still applies).
     */
    private BigDecimal capByIncome(BigDecimal value, BigDecimal income) {
        if (income != null && income.compareTo(BigDecimal.ZERO) > 0) {
            return value.min(income);
        }
        return value;
    }

    /** P2-E: share of disposable to suggest saving, by pace. Cold-start (null) → BALANCED. */
    private BigDecimal scaleByPace(BigDecimal disposable, Saving_Pace pace) {
        BigDecimal fraction = switch (pace == null ? Saving_Pace.BALANCED : pace) {
            case RELAXED -> new BigDecimal("0.10");
            case BALANCED -> new BigDecimal("0.20");
            case AGGRESIVE -> new BigDecimal("0.30");
        };
        return disposable.multiply(fraction);
    }

    /**
     * P3-C: human-readable push body from the insight, best-effort. Falls back to the raw i18n
     * {@code code} when narration is off/unavailable — the FE localizes that key as before.
     */
    private String pushText(Insight insight, String fallbackCode) {
        String narrative = narrator.narrate(insight);
        return narrative != null ? narrative : fallbackCode;
    }

    private String payloadJson(Insight insight, SuggestionPayload.ProposedAction action) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("type", insight.getType().name());
        snapshot.put("severity", insight.getSeverity().name());
        snapshot.put("code", insight.getCode());
        snapshot.put("period", insight.getPeriod() != null ? insight.getPeriod().toString() : null);
        snapshot.put("metrics", insight.getMetrics());

        SuggestionPayload payload = SuggestionPayload.builder()
                .insightSnapshot(snapshot)
                .proposedAction(action)
                .build();
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize suggestion payload", e);
        }
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

    /** Internal carrier for a fully-resolved suggestion ready to persist + push. */
    @Builder
    private static class Candidate {
        private final SuggestionType type;
        private final Severity severity;
        private final String dedupKey;
        private final String payloadJson;
        private final String notificationContent;
    }
}
