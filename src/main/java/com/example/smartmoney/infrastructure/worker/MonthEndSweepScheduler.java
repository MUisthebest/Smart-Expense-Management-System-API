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
 * Wave 3 (item 7): the late-month savings sweep. Decouples {@code CONTRIBUTE_TO_PROJECT} from
 * income arrival: today the savings nudge only fires when the user logs an INCOME transaction —
 * the worst moment to ask (the money hasn't paid the month's bills yet) and never at all for users
 * who don't log income. Around day 25 the picture inverts: remaining headroom is genuinely spare
 * and about to evaporate at month reset, so the same ask has far better odds.
 *
 * <p>Deliberately zero new logic: it fans out to the existing
 * {@link SuggestionProducer#maybeProduceSavingsContribution}, so the trait gate, capacity math,
 * pace scaling, dedup, cooldown, and daily cap all apply unchanged (a savings ask already answered
 * this month is naturally suppressed). Disabled by default ({@code app.sweep.enabled}); cron
 * overridable via {@code app.sweep.cron}, default 09:00 on the 25th.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.sweep", name = "enabled", havingValue = "true", matchIfMissing = false)
public class MonthEndSweepScheduler {

    private final UserRepository userRepository;
    private final SuggestionProducer suggestionProducer;

    /** Default cron: {@code 0 0 9 25 * *} → 09:00 on the 25th of every month (server local time). */
    @Scheduled(cron = "${app.sweep.cron:0 0 9 25 * *}")
    public void runMonthEndSweep() {
        LocalDate today = LocalDate.now();
        List<User> users = userRepository.findAllForDigest();
        for (User user : users) {
            try {
                // Best-effort by contract (the producer swallows its own failures); the belt-and-
                // suspenders catch keeps one user's surprise from aborting the batch anyway.
                suggestionProducer.maybeProduceSavingsContribution(user.getId(), today);
            } catch (Exception e) {
                log.warn("[Sweep] failed for user {}: {}", user.getId(), e.getMessage());
            }
        }
        log.info("[Sweep] month-end savings sweep complete: {} users evaluated", users.size());
    }
}
