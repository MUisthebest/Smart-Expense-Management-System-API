package com.example.smartmoney.infrastructure.worker;

import com.example.smartmoney.application.usecases.project.AssessProjectArrearsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Infrastructure trigger for the monthly group / personal project arrears
 * assessment. Fires at 00:30 on the 1st of every month — deliberately 30
 * minutes after {@link MonthlySettlementScheduler} so that any pending
 * income/expense settlement has already been applied to user incomes before
 * we recompute who's behind on their pledged contributions.
 *
 * <p>{@code @EnableScheduling} is already declared on
 * {@code SmartmoneyApplication}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupDebtAssessmentWorker {

    private final AssessProjectArrearsUseCase assessProjectArrearsUseCase;

    /**
     * Cron: {@code 0 30 0 1 * *} → 00:30 on day 1 of every month.
     */
    @Scheduled(cron = "0 30 0 1 * *")
    public void runArrearsAssessment() {
        log.info("[Scheduler] Project arrears assessment triggered.");
        try {
            assessProjectArrearsUseCase.execute();
        } catch (Exception ex) {
            log.error("[Scheduler] Arrears assessment failed: {}", ex.getMessage(), ex);
        }
    }
}
