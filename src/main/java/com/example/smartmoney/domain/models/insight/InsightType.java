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
    SAVINGS_CAPACITY,
    /**
     * The sum of this month's budget limits exceeds the user's declared income — an account-wide,
     * informational "soft envelope" warning. Never enforced (budgets stay independent); it only
     * surfaces the over-commitment so the user can rebalance. See docs/redesign-what-and-why.md.
     */
    BUDGET_OVER_INCOME,
    /**
     * The inverse of {@link #BUDGET_PACE}: a budgeted category whose trailing-median actual sits
     * consistently well below its limit and whose current-month pace confirms it — the budget is
     * over-provisioned and can be trimmed. Informational; backs a REDUCE_BUDGET suggestion
     * (Wave 1, docs/insight-suggestion-expansion-plan.md).
     */
    BUDGET_UNDERSPEND,
    /**
     * A single expense that is a statistical outlier versus that category's typical per-transaction
     * size (per-charge z-score). Notification-only — evaluated synchronously on transaction create,
     * never routed through the suggestion machinery. Not emitted by {@code analyze()}.
     */
    LARGE_TRANSACTION,
    /**
     * A near-immediate repeat of the same category + exact amount + normalized description within a
     * short window — a likely double-charge. Notification-only, like {@link #LARGE_TRANSACTION}.
     */
    DUPLICATE_CHARGE,
    /**
     * A learned recurring charge (see {@link #RECURRING}) whose current-month amount breaks the
     * group's upward tolerance band — the subscription's price went up. Backs the acknowledge-only
     * REVIEW_SUBSCRIPTION suggestion (Wave 2, docs/insight-suggestion-expansion-plan.md).
     */
    SUBSCRIPTION_PRICE_HIKE,
    /**
     * A closed month's INCOME total is a statistical low-outlier vs the trailing income baseline
     * (the mirror of {@link #OVERSPEND_ANOMALY} on the income series) — or, on a flat baseline,
     * half or less of the usual amount arrived. Only meaningful once the month has ended, so it is
     * evaluated by the month-rollover recap, never by {@code analyze()}. Notification-only (Wave 3).
     */
    INCOME_DROP,
    /**
     * A closed month's expenses exceeded its income. Mid-month this is normal (salary timing), so
     * like {@link #INCOME_DROP} it is judged only at month close by the recap. Notification-only
     * (Wave 3).
     */
    CASHFLOW_NEGATIVE
}
