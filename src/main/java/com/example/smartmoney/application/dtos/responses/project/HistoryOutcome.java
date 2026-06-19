package com.example.smartmoney.application.dtos.responses.project;

/**
 * Classifies what happened to a project during one monthly settlement, so the UI
 * can render the right icon and narrative for each history row. The FE composes
 * the sentence from this enum plus the numeric fields on the snapshot.
 */
public enum HistoryOutcome {
    /** Stayed within budget — the full monthly saving was reserved, no debt. */
    CLEAN_MONTH,
    /** Overspent — the excess became debt ({@code penalty > 0}). */
    OVERSPENT,
    /** Spent under budget — surplus was auto-invested ({@code surplusInvested > 0}). */
    UNDERSPENT_BONUS,
    /** Frozen month — no new saving was reserved and savings did not move. */
    FROZEN_NO_SAVING
}
