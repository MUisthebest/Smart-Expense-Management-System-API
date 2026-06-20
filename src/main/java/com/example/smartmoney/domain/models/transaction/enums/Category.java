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
    PROJECT_REFUND
}
