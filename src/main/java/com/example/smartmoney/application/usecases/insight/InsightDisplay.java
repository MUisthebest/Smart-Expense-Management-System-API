package com.example.smartmoney.application.usecases.insight;

import com.example.smartmoney.domain.models.insight.Insight;
import com.example.smartmoney.domain.services.MoneyRounding;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tidies an {@link Insight}'s figures for the moment they stop being a measurement and become
 * something a person reads.
 *
 * <p>This lives outside both callers on purpose. The same insight surfaces twice — frozen onto a
 * suggestion card by {@code SuggestionProducer}, and listed live by {@code GenerateInsightsUseCase} —
 * and the two showing different numbers for the same fact is worse than either number being slightly
 * off. Rounding in one caller is how that drift starts, so there is one function and both call it.
 *
 * <p>Rounding deliberately does <em>not</em> happen in {@code SpendingInsightAnalyzer}: the exact
 * figure is what drives {@code overshootRatio} and therefore the WARN/CRITICAL boundary, so snapping
 * it at the source would move severity thresholds as a side effect of a display change.
 */
final class InsightDisplay {

    private InsightDisplay() {
    }

    /**
     * A copy of {@code insight} with its month-end forecast snapped to the money step, so a card can
     * never show an odd 927,500 above a table of clean ±10,000 moves.
     *
     * <p>Only the forecast is rounded. {@code spent} and {@code limit} are facts — the user's own
     * numbers — and restating them as anything else would be a lie, however tidy. Statistical metrics
     * (z-scores, medians, daily rates) aren't amounts anyone acts on, so they keep their precision.
     * Returns the insight untouched when it carries no forecast.
     */
    static Insight rounded(Insight insight, MoneyRounding rounding) {
        if (insight == null || rounding == null) {
            return insight;
        }
        Map<String, Object> metrics = insight.getMetrics();
        if (metrics == null || metrics.get("projectedEndOfMonth") == null) {
            return insight;
        }
        Map<String, Object> display = new LinkedHashMap<>(metrics);
        // Snapped UP: a forecast that warns about an overshoot should never quietly understate it.
        display.put("projectedEndOfMonth",
                rounding.snapUp(new BigDecimal(String.valueOf(metrics.get("projectedEndOfMonth")))));
        return Insight.builder()
                .type(insight.getType())
                .category(insight.getCategory())
                .severity(insight.getSeverity())
                .period(insight.getPeriod())
                .metrics(display)
                .code(insight.getCode())
                .build();
    }
}
