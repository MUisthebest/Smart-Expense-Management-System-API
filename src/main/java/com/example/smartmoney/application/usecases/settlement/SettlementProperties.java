package com.example.smartmoney.application.usecases.settlement;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Exposes whether the monthly settlement engine is live ({@code app.settlement.enabled}) to code
 * outside the scheduler. The {@link org.springframework.boot.autoconfigure.condition.ConditionalOnProperty}
 * on {@code MonthlySettlementScheduler} gates the cron itself; this bean lets collaborators (e.g. the
 * suggestion producer) branch on the same flag — a manual "contribute" nudge should only be suppressed
 * as a settlement double-ask when settlement is actually running. Default {@code false}, matching yaml.
 */
@Component
@Getter
public class SettlementProperties {

    private final boolean enabled;

    public SettlementProperties(@Value("${app.settlement.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }
}
