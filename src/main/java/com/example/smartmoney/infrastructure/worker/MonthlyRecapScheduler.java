package com.example.smartmoney.infrastructure.worker;

import com.example.smartmoney.application.usecases.insight.SendMonthlyRecapUseCase;
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
 * Wave 3 (item 6): month-rollover trigger for {@link SendMonthlyRecapUseCase} — the first purely
 * time-based signal in the adaptive engine (everything before it keyed off a transaction or budget
 * write). Runs shortly after a month closes and evaluates the just-completed month per user.
 *
 * <p>Registered only when {@code app.recap.enabled=true} — <b>disabled by default</b>, mirroring
 * {@link WeeklyInsightDigestScheduler}, until the fan-out is validated against a live DB. The cron
 * is property-overridable ({@code app.recap.cron}) so a demo profile can fire it every minute; the
 * default is 09:00 on the 1st. For deterministic testing/demos, call the use case (or the demo
 * trigger endpoint) with an explicit {@code asOf} instead of waiting for the clock.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.recap", name = "enabled", havingValue = "true", matchIfMissing = false)
public class MonthlyRecapScheduler {

    private final UserRepository userRepository;
    private final SendMonthlyRecapUseCase sendMonthlyRecapUseCase;

    /** Default cron: {@code 0 0 9 1 * *} → 09:00 on the 1st of every month (server local time). */
    @Scheduled(cron = "${app.recap.cron:0 0 9 1 * *}")
    public void runMonthlyRecap() {
        LocalDate today = LocalDate.now();
        List<User> users = userRepository.findAllForDigest();
        int sent = 0;
        for (User user : users) {
            try {
                if (sendMonthlyRecapUseCase.sendFor(user.getId(), today)) {
                    sent++;
                }
            } catch (Exception e) {
                // One user's failure must not abort the batch.
                log.warn("[Recap] failed for user {}: {}", user.getId(), e.getMessage());
            }
        }
        log.info("[Recap] monthly recap complete: {} sent / {} eligible", sent, users.size());
    }
}
