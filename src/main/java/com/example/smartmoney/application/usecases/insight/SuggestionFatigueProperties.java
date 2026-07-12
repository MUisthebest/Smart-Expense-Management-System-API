package com.example.smartmoney.application.usecases.insight;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Tunables for the P3-A fatigue ceiling (see docs/redesign-phase2-3-plan.md §P3-A):
 * <ul>
 *   <li>{@code dailyCap} — max suggestions pushed to a user per calendar day.</li>
 *   <li>{@code cooldownHours} — minimum spacing between pushes on the same topic.</li>
 * </ul>
 * A CRITICAL escalation bypasses both (enforced in {@link SuggestionProducer}).
 */
@Component
@Getter
public class SuggestionFatigueProperties {

    private final int dailyCap;
    private final long cooldownHours;

    public SuggestionFatigueProperties(
            @Value("${app.suggestion.daily-cap:3}") int dailyCap,
            @Value("${app.suggestion.cooldown-hours:24}") long cooldownHours) {
        this.dailyCap = dailyCap;
        this.cooldownHours = cooldownHours;
    }
}
