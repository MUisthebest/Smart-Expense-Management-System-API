package com.example.smartmoney.infrastructure.worker;

import com.example.smartmoney.application.usecases.insight.SuggestionProducer;
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
 * Wave 4 (item 8): the weekly project-health pass. A saving project falling behind its deadline
 * has no transaction to react to — the problem is precisely the contributions that <em>didn't</em>
 * happen — so, like the monthly recap, it needs a time-based trigger. Weekly (not monthly) so a
 * stalled project is caught within days, while the per-project monthly dedup key plus the topic
 * cooldown keep the ask itself at most monthly.
 *
 * <p>Zero new logic here: it fans out to
 * {@link SuggestionProducer#maybeProduceProjectHealth}, so the trait gate, severity escalation,
 * dedup, dismissal suppression, cooldown, and daily cap all apply unchanged. Disabled by default
 * ({@code app.project-health.enabled}); cron overridable via {@code app.project-health.cron},
 * default 09:00 every Monday.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.project-health", name = "enabled", havingValue = "true", matchIfMissing = false)
public class ProjectHealthScheduler {

    private final UserRepository userRepository;
    private final SuggestionProducer suggestionProducer;

    /** Default cron: {@code 0 0 9 * * MON} → 09:00 every Monday (server local time). */
    @Scheduled(cron = "${app.project-health.cron:0 0 9 * * MON}")
    public void runProjectHealthCheck() {
        LocalDate today = LocalDate.now();
        List<User> users = userRepository.findAllForDigest();
        for (User user : users) {
            try {
                // Best-effort by contract (the producer swallows its own failures); the belt-and-
                // suspenders catch keeps one user's surprise from aborting the batch anyway.
                suggestionProducer.maybeProduceProjectHealth(user.getId(), today);
            } catch (Exception e) {
                log.warn("[ProjectHealth] failed for user {}: {}", user.getId(), e.getMessage());
            }
        }
        log.info("[ProjectHealth] weekly project-health pass complete: {} users evaluated", users.size());
    }
}
