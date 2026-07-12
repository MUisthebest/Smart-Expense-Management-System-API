package com.example.smartmoney.application.usecases.insight;

import com.example.smartmoney.application.dtos.responses.insight.InsightResponse;
import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.domain.models.insight.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * P2-F: batches a user's actionable insights into a single digest notification, instead of the
 * per-event pushes the {@link SuggestionProducer} emits. Intended for low-interruption focus modes
 * (SAVE_MORE / TRACK_ONLY). Reuses {@link GenerateInsightsUseCase} for the data, so the digest and
 * the pull screen always agree. Pure per-user logic, separated from the schedule trigger so it is
 * unit-testable without Spring scheduling.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SendInsightDigestUseCase {

    private final GenerateInsightsUseCase generateInsightsUseCase;
    private final CreateNotificationUseCase createNotificationUseCase;

    /**
     * Send one digest for the user covering {@code asOf}'s period. Counts only {@code >= WARN}
     * insights (informational ones don't warrant an interruption). No actionable insights → no
     * notification. Returns whether a digest was actually sent.
     */
    public boolean sendFor(UUID userId, LocalDate asOf) {
        List<InsightResponse> insights = generateInsightsUseCase.execute(userId, asOf);
        long actionable = insights.stream()
                .filter(i -> i.getSeverity() != null && i.getSeverity().atLeast(Severity.WARN))
                .count();
        if (actionable == 0) {
            return false;
        }
        // Pipe-delimited i18n key carrying the count, consistent with the producer's content style.
        createNotificationUseCase.createNotification(
                userId, "notification.digest.weekly|" + actionable, "app://insights");
        log.debug("[Digest] sent weekly digest to user {} with {} actionable insight(s)", userId, actionable);
        return true;
    }
}
