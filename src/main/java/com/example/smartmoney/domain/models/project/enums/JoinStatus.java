package com.example.smartmoney.domain.models.project.enums;

public enum JoinStatus {
    INVITED,
    JOINED,
    LEFT,
    /**
     * Member was kicked after their {@code arrears_balance} reached
     * 3× their monthly commitment. Arrears are wiped at the moment of kicking.
     */
    DEFAULTED
}
