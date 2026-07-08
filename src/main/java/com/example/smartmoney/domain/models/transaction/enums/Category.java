package com.example.smartmoney.domain.models.transaction.enums;

public enum Category {
    FOOD,
    TRANSPORTATION,
    CLOTHING,
    UTILITIES,
    ENTERTAINMENT,
    HEALTH,
    EDUCATION,
    SHOPPING,
    OTHER,
    /**
     * System-generated INCOME transactions emitted when a project is abandoned —
     * one per past contribution. Restores the contributor's safe-spending headroom
     * via the ledger without any real-world money movement.
     */
    PROJECT_REFUND,
    /**
     * EXPENSE transaction emitted when a user presses "contribute" on a saving
     * project (press-to-contribute model). Moves real money out of spendable into
     * the project so progress can never diverge from the ledger. Carries the
     * project id on the transaction. See docs/redesign-technical-implementation.md §5.4.
     */
    PROJECT_CONTRIBUTION
}
