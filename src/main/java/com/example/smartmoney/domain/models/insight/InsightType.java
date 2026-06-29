package com.example.smartmoney.domain.models.insight;

/**
 * Kinds of deterministic spending insight the {@code SpendingInsightAnalyzer} can emit.
 * Phase 1 (workstream C) ships only the two below; CATEGORY_TREND / RECURRING /
 * SAVINGS_CAPACITY are Phase 2+ (see docs/redesign-workstream-c-plan.md §C1.1).
 */
public enum InsightType {
    /** Current-month category spend is a statistical outlier vs the trailing baseline (rolling z-score). */
    OVERSPEND_ANOMALY,
    /** Linear end-of-month projection for a budget is on track to exceed its limit. */
    BUDGET_PACE
}
