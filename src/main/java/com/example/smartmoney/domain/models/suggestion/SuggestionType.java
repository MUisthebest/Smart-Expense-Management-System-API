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
    REDUCE_BUDGET,
    /**
     * Scale all of the month's budget limits proportionally so their total fits the user's declared
     * income (BUDGET_OVER_INCOME). The consented numbers are the per-budget snapshot in
     * {@code proposedAction.budgetAdjustments}; accept applies exactly that snapshot (Wave 2 item 5).
     */
    REBALANCE_BUDGETS,
    /**
     * A recurring charge's price jumped (SUBSCRIPTION_PRICE_HIKE) — ask the user to review it.
     * Acknowledge-only: accepting marks it reviewed; the real action (cancelling or renegotiating
     * the subscription) happens outside the app, so accept executes nothing (Wave 2 item 4).
     */
    REVIEW_SUBSCRIPTION,
    /**
     * Contribute a catch-up amount to a saving project that is behind the pace its deadline
     * requires (PROJECT_OFF_TRACK). Executes the same add-contribution action as
     * {@link #CONTRIBUTE_TO_PROJECT}, with the resolved amount sized to close the gap
     * (Wave 4 item 8).
     */
    INCREASE_CONTRIBUTION,
    /**
     * Cover a budget that is on pace to overshoot (BUDGET_PACE) by pulling headroom from other
     * budgets the same month that are confirmed underspent (BUDGET_UNDERSPEND) — a zero-sum move
     * that leaves the committed total unchanged. Executes the same all-or-nothing snapshot action
     * as {@link #REBALANCE_BUDGETS} ({@code proposedAction.budgetAdjustments}): the recipient's
     * limit goes up and each donor's goes down by the consented amounts. Preferred over
     * {@link #RAISE_BUDGET} because it needs no new money.
     */
    REALLOCATE_BUDGET,
    /**
     * Rebalance over-committed saving projects (OVER_COMMITTED) so their combined monthly saving
     * fits the user's pace capacity — each project's deadline is extended (lowering its required
     * monthly). The consented numbers are the per-project new deadlines snapshotted in
     * {@code proposedAction.projectAdjustments}; accept applies exactly that snapshot, all-or-nothing
     * (docs/settlement-insights-followup.md item 4).
     */
    REBALANCE_PROJECTS
}
