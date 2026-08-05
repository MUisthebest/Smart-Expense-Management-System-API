package com.example.smartmoney.config;

import com.example.smartmoney.domain.services.BudgetAllocationCalculator;
import com.example.smartmoney.domain.services.ForecastMethod;
import com.example.smartmoney.domain.services.MoneyRounding;
import com.example.smartmoney.domain.services.SpendingInsightAnalyzer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;

/**
 * Exposes pure domain services as Spring beans without coupling the domain classes to
 * Spring. {@link SpendingInsightAnalyzer} stays a plain, fully unit-testable class
 * (docs/redesign-workstream-c-plan.md §0); this is the only place that wires it for DI.
 */
@Configuration
public class DomainServiceConfig {

    /**
     * @param forecastMethod P3-B budget-pace forecast (LINEAR control vs EXP_SMOOTHING); defaults to
     *                       LINEAR so the live behaviour is unchanged until the flag is flipped.
     */
    @Bean
    public SpendingInsightAnalyzer spendingInsightAnalyzer(
            @Value("${app.insight.forecast-method:LINEAR}") ForecastMethod forecastMethod) {
        return new SpendingInsightAnalyzer(forecastMethod);
    }

    /**
     * Step that proposed budget limits are snapped to, so a card never asks the user to approve
     * the raw output of a proportional split. Currency-dependent, hence configurable; the default
     * is VND-scale.
     */
    @Bean
    public MoneyRounding moneyRounding(
            @Value("${app.money.rounding-step:10000}") BigDecimal roundingStep) {
        return new MoneyRounding(roundingStep);
    }

    /**
     * Pure deterministic budget allocator — the LLM-free path behind
     * {@code POST /api/v1/budgets/allocation/compute}. Stateless, so a single bean is safe to share.
     */
    @Bean
    public BudgetAllocationCalculator budgetAllocationCalculator() {
        return new BudgetAllocationCalculator();
    }

    /**
     * Wall-clock used by time-sensitive use cases (e.g. the P3-A suggestion cooldown / daily cap).
     * Injected rather than read statically so those rules stay deterministic in unit tests.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
