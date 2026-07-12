package com.example.smartmoney.infrastructure.worker;

import com.example.smartmoney.application.usecases.insight.SendInsightDigestUseCase;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * P2-F: weekly insight digest for low-interruption users (focus SAVE_MORE / TRACK_ONLY). Batches
 * the period's actionable insights into one push, rather than the per-event suggestions the
 * {@link SendInsightDigestUseCase} replaces for these users.
 *
 * <p>Registered only when {@code app.digest.enabled=true} — <b>disabled by default</b>, mirroring
 * {@link MonthlySettlementScheduler}, until validated against a live DB (the {@code findForDigest}
 * native-enum query and the per-user fan-out are unproven end-to-end). {@code @EnableScheduling}
 * is already declared on {@code SmartmoneyApplication}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.digest", name = "enabled", havingValue = "true", matchIfMissing = false)
public class WeeklyInsightDigestScheduler {

    private final UserRepository userRepository;
    private final SendInsightDigestUseCase sendInsightDigestUseCase;

    /** Cron: {@code 0 0 9 * * MON} → 09:00 every Monday (server local time). */
    @Scheduled(cron = "0 0 9 * * MON")
    public void runWeeklyDigest() {
        LocalDate today = LocalDate.now();
        List<User> users = userRepository.findAllForDigest();
        int sent = 0;
        for (User user : users) {
            try {
                if (sendInsightDigestUseCase.sendFor(user.getId(), today)) {
                    sent++;
                }
            } catch (Exception e) {
                // One user's failure must not abort the batch.
                log.warn("[Digest] failed for user {}: {}", user.getId(), e.getMessage());
            }
        }
        log.info("[Digest] weekly insight digest complete: {} sent / {} eligible", sent, users.size());
    }
}
