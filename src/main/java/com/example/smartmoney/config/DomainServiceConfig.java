package com.example.smartmoney.config;

import com.example.smartmoney.domain.services.SpendingInsightAnalyzer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes pure domain services as Spring beans without coupling the domain classes to
 * Spring. {@link SpendingInsightAnalyzer} stays a plain, fully unit-testable class
 * (docs/redesign-workstream-c-plan.md §0); this is the only place that wires it for DI.
 */
@Configuration
public class DomainServiceConfig {

    @Bean
    public SpendingInsightAnalyzer spendingInsightAnalyzer() {
        return new SpendingInsightAnalyzer();
    }
}
