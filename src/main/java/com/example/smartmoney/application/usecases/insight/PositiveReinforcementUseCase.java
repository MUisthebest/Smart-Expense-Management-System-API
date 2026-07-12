package com.example.smartmoney.application.usecases.insight;

import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.insight.Insight;
import com.example.smartmoney.domain.models.insight.InsightType;
import com.example.smartmoney.domain.models.insight.Severity;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.suggestion.SuggestionTopicStat;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.SuggestionTopicStatRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wave 4 (item 9): positive reinforcement — the engine's first messages that celebrate instead of
 * warn. Two events qualify:
 * <ul>
 *   <li><b>PROJECT_MILESTONE</b> — a contribution pushed a saving project across a progress
 *       milestone ({@link #MILESTONES}); evaluated synchronously on contribution.</li>
 *   <li><b>GOOD_MONTH</b> — a closed month where every budget finished under its limit; evaluated
 *       at month rollover, riding the recap (and only when the recap found nothing negative — a
 *       congratulation next to an overspend warning would read as sarcasm).</li>
 * </ul>
 *
 * <p>Notification-only (no card, no accept action, never in {@code GET /insights}), and INFO by
 * nature, so the gate is <em>presence of push</em> (GENTLE and HARD both celebrate; NOTIFY stays
 * pull-only) — the severity gate is about warning fatigue, and these aren't warnings (resolved
 * user decision, 2026-07-03). Doesn't fit the {@code Suggestion} dedup model (there is nothing
 * PENDING to collapse onto), so "already congratulated?" uses a {@link SuggestionTopicStat}-style
 * marker per (project, milestone) / per month instead. Every entry point is best-effort and never
 * throws — a missed cheer must not break a contribution or a recap.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PositiveReinforcementUseCase {

    /** Progress thresholds (percent) worth celebrating; a single jump celebrates the highest crossed. */
    static final int[] MILESTONES = {25, 50, 75, 100};

    private final UserRepository userRepository;
    private final BudgetRepository budgetRepository;
    private final SuggestionTopicStatRepository topicStatRepository;
    private final CreateNotificationUseCase createNotificationUseCase;
    private final InsightNarrator narrator;
    private final Clock clock;

    /**
     * Celebrate the highest milestone a contribution just crossed, at most once per (project,
     * milestone). Call with the progress percent before and after the contribution.
     */
    public void maybeCelebrateMilestone(UUID userId, Project project,
                                        BigDecimal beforePct, BigDecimal afterPct) {
        try {
            if (project == null || beforePct == null || afterPct == null || !userGetsPushes(userId)) {
                return;
            }
            int crossed = highestCrossed(beforePct, afterPct);
            if (crossed < 0) {
                return;
            }
            String markerKey = "MILESTONE:" + project.getId().value() + ":" + crossed;
            if (topicStatRepository.find(userId, markerKey).isPresent()) {
                return; // this milestone was already celebrated (e.g. progress bounced around it)
            }

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("projectName", project.getName());
            metrics.put("milestone", crossed);
            metrics.put("progressPercent", afterPct);
            metrics.put("contributed", project.totalContributed().amount());
            metrics.put("target", project.getTargetAmount().amount());

            Insight insight = Insight.builder()
                    .type(InsightType.PROJECT_MILESTONE)
                    .severity(Severity.INFO)
                    .period(YearMonth.now(clock))
                    .metrics(metrics)
                    .code("insight.project_milestone")
                    .build();

            createNotificationUseCase.createNotification(
                    userId,
                    pushText(insight, "notification.insight.project_milestone|" + project.getName()),
                    "app://projects/" + project.getId().value());
            stampMarker(userId, markerKey);
            log.info("[Positive] milestone {}% celebrated for user={} project={}",
                    crossed, userId, project.getId().value());
        } catch (Exception e) {
            // Celebration is a garnish: never let it fail the contribution that earned it.
            log.warn("[Positive] milestone check failed for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Celebrate a closed month in which every budget finished under its limit, at most once per
     * month. The caller (the recap) only invokes this when the month produced no negative finding.
     * Returns whether a ping was pushed.
     */
    public boolean maybeCelebrateGoodMonth(UUID userId, YearMonth closed) {
        try {
            if (closed == null || !userGetsPushes(userId)) {
                return false;
            }
            String markerKey = "GOOD_MONTH:" + closed;
            if (topicStatRepository.find(userId, markerKey).isPresent()) {
                return false; // this month was already celebrated (e.g. a demo re-run)
            }

            List<Budget> budgets = budgetRepository.findByUserIdMonthYear(
                    userId, closed.getMonthValue(), closed.getYear());
            if (budgets.isEmpty()) {
                return false; // nothing was budgeted, so nothing was "stayed under"
            }
            BigDecimal totalLimit = BigDecimal.ZERO;
            BigDecimal totalSpent = BigDecimal.ZERO;
            for (Budget budget : budgets) {
                BigDecimal spent = budget.getSpent() == null ? BigDecimal.ZERO : budget.getSpent();
                if (budget.getAmountLimit() == null || spent.compareTo(budget.getAmountLimit()) > 0) {
                    return false; // at least one budget blew its limit — not a good month
                }
                totalLimit = totalLimit.add(budget.getAmountLimit());
                totalSpent = totalSpent.add(spent);
            }

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("budgetCount", budgets.size());
            metrics.put("totalLimit", totalLimit);
            metrics.put("totalSpent", totalSpent);

            Insight insight = Insight.builder()
                    .type(InsightType.GOOD_MONTH)
                    .severity(Severity.INFO)
                    .period(closed)
                    .metrics(metrics)
                    .code("insight.good_month")
                    .build();

            createNotificationUseCase.createNotification(
                    userId,
                    pushText(insight, "notification.insight.good_month"),
                    "app://budgets");
            stampMarker(userId, markerKey);
            log.info("[Positive] good month {} celebrated for user={} ({} budgets under limit)",
                    closed, userId, budgets.size());
            return true;
        } catch (Exception e) {
            log.warn("[Positive] good-month check failed for user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    /** The highest milestone in {@link #MILESTONES} that {@code after} reached but {@code before} hadn't, or -1. */
    private static int highestCrossed(BigDecimal before, BigDecimal after) {
        for (int i = MILESTONES.length - 1; i >= 0; i--) {
            BigDecimal threshold = BigDecimal.valueOf(MILESTONES[i]);
            if (after.compareTo(threshold) >= 0 && before.compareTo(threshold) < 0) {
                return MILESTONES[i];
            }
        }
        return -1;
    }

    /** Presence-of-push gate: INFO positives go to every non-NOTIFY user (not severity-gated). */
    private boolean userGetsPushes(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        Intervention_Level level = user == null ? null : user.getInterventionLevel();
        return level != null && level != Intervention_Level.NOTIFY;
    }

    /** "Already celebrated?" marker — a topic-stat row whose presence is the whole answer. */
    private void stampMarker(UUID userId, String markerKey) {
        SuggestionTopicStat marker = SuggestionTopicStat.start(userId, markerKey);
        marker.recordPush(clock.instant());
        topicStatRepository.save(marker);
    }

    /** Narrated push body when narration is on, else the raw i18n key (the FE localizes it). */
    private String pushText(Insight insight, String fallbackCode) {
        String narrative = narrator.narrate(insight);
        return narrative != null ? narrative : fallbackCode;
    }
}
