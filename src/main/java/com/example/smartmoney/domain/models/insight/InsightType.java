package com.example.smartmoney.domain.models.insight;

/**
 * Kinds of deterministic spending insight the {@code SpendingInsightAnalyzer} can emit.
 * The first two shipped in Phase 1 (workstream C); the last three were added in Phase 2
 * (P2-C, see docs/redesign-phase2-3-plan.md).
 */
public enum InsightType {
    /** Current-month category spend is a statistical outlier vs the trailing baseline (rolling z-score). */
    OVERSPEND_ANOMALY,
    /** Linear end-of-month projection for a budget is on track to exceed its limit. */
    BUDGET_PACE,
    /** A category's monthly spend is trending up over the trailing window (regression slope). */
    CATEGORY_TREND,
    /** A near-equal charge repeats across several months — a learned recurring cost. */
    RECURRING,
    /** Estimated monthly room to save: income − recurring costs − committed budgets. */
    SAVINGS_CAPACITY
}
