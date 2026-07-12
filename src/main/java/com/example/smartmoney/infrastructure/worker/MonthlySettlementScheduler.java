package com.example.smartmoney.infrastructure.worker;

import com.example.smartmoney.application.usecases.settlement.PerformMonthlySettlementUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Infrastructure trigger for the monthly financial settlement.
 * Fires at midnight on the 1st of every month (server local time).
 *
 * <p><b>Deprecated by the redesign (Pivot B — press-to-contribute).</b> The automated
 * settlement engine is being retired in favour of explicit, user-pressed contributions
 * (see docs/redesign-technical-implementation.md §5.2). This bean is therefore registered
 * only when {@code app.settlement.enabled=true}; it is <b>disabled by default</b>, so the
 * monthly cron no longer runs. The {@code PerformMonthlySettlementUseCase} and the manual
 * {@code AdminSettlementController} trigger are left in place but dormant.
 *
 * <p>{@code @EnableScheduling} is already declared on {@code SmartmoneyApplication}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.settlement", name = "enabled", havingValue = "true", matchIfMissing = false)
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
