package com.example.smartmoney.infrastructure.worker;

import com.example.smartmoney.application.usecases.settlement.PerformMonthlySettlementUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Infrastructure trigger for the monthly financial settlement.
 * Fires at midnight on the 1st of every month (server local time).
 *
 * <p>{@code @EnableScheduling} is already declared on {@code SmartmoneyApplication}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonthlySettlementScheduler {

    private final PerformMonthlySettlementUseCase performMonthlySettlementUseCase;

    /**
     * Cron: {@code 0 0 0 1 * *} → second=0, minute=0, hour=0, day=1, every month, every day-of-week.
     */
    @Scheduled(cron = "0 0 0 1 * *")
    public void runMonthlySettlement() {
        log.info("[Scheduler] Monthly settlement triggered.");
        performMonthlySettlementUseCase.execute();
    }
}
