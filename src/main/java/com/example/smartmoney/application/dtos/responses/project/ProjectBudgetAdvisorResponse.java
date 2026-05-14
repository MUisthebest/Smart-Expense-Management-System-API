package com.example.smartmoney.application.dtos.responses.project;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Result returned by the AI project-budget advisor.
 */
@Data
@Builder
public class ProjectBudgetAdvisorResponse {

    /** Adjusted monthly savings the user must set aside (VND). */
    private BigDecimal monthlySaving;

    /** Number of months needed to reach the target under the chosen mode. */
    private int numberOfMonths;
}
