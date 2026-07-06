package com.example.smartmoney.domain.exceptions;

public final class BudgetErrorCode {

    private BudgetErrorCode() {
        // Prevent instantiation
    }

    public static final String USER_NOT_FOUND = "BUDGET_USER_NOT_FOUND";

    public static final String FINANCIAL_SETUP_REQUIRED =
            "BUDGET_FINANCIAL_SETUP_REQUIRED";

    public static final String AI_REQUEST_FAILED =
            "BUDGET_AI_REQUEST_FAILED";

    public static final String INVALID_AI_RESPONSE =
            "BUDGET_INVALID_AI_RESPONSE";
}