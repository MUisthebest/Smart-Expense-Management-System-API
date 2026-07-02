package com.example.smartmoney.domain.models.suggestion;

/**
 * Action a {@code Suggestion} proposes. Phase 1 (workstream C) shipped RAISE_BUDGET; Phase 2
 * (P2-B) adds CREATE_BUDGET and CONTRIBUTE_TO_PROJECT. Stored as a plain varchar (no DB CHECK),
 * so adding values stays a code-only change. Every {@code switch} on this enum is a closed
 * whitelist — an unmapped value is intentionally inert.
 */
public enum SuggestionType {
    /** Raise an existing budget's limit to the consented value (BUDGET_PACE overshoot). */
    RAISE_BUDGET,
    /** Create a budget for a category that has none, at the consented limit (OVERSPEND_ANOMALY). */
    CREATE_BUDGET,
    /** Contribute the consented amount to an active saving project (SAVINGS_CAPACITY headroom). */
    CONTRIBUTE_TO_PROJECT,
    /**
     * Set a first budget for a budgetless category that is trending up, at the consented limit
     * (CATEGORY_TREND). Executes the same create-budget action as {@link #CREATE_BUDGET}.
     */
    SET_CATEGORY_LIMIT,
    /**
     * Lower an over-provisioned budget's limit to the consented value (BUDGET_UNDERSPEND). Executes
     * the same update-budget action as {@link #RAISE_BUDGET}, in the opposite direction.
     */
    REDUCE_BUDGET
}
