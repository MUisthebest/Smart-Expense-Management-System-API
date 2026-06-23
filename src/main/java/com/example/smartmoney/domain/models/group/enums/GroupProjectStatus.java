package com.example.smartmoney.domain.models.group.enums;

public enum GroupProjectStatus {
    ACTIVE,
    COMPLETED,
    DISSOLVED,
    /** Every member's sub-project dropped out (EXPIRED/ABANDONED), so no participants remain. */
    EXPIRED
}
