package com.example.smartmoney.application.dtos.responses.project;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ProjectAIContextResponse {

    private String projectId;
    private String projectName;

    private BigDecimal targetAmount;

    private BigDecimal moneySaved;

    private BigDecimal expectedSaved;

    private BigDecimal savingGap;

    private BigDecimal remainingToGoal;

    private BigDecimal requiredMonthlyFromNow;

    private BigDecimal monthlySaving;

    private BigDecimal overspendingDebt;

    private Integer monthLeft;

    private Integer progressPercent;

    public static ProjectAIContextResponse fromDomain(
            String projectId,
            String projectName,
            BigDecimal targetAmount,
            BigDecimal moneySaved,
            BigDecimal expectedSaved,
            BigDecimal savingGap,
            BigDecimal remainingToGoal,
            BigDecimal requiredMonthlyFromNow,
            BigDecimal monthlySaving,
            BigDecimal overspendingDebt,
            Integer monthLeft,
            Integer progressPercent
    ) {
        return ProjectAIContextResponse.builder()
                .projectId(projectId)
                .projectName(projectName)
                .targetAmount(targetAmount)
                .moneySaved(moneySaved)
                .expectedSaved(expectedSaved)
                .savingGap(savingGap)
                .remainingToGoal(remainingToGoal)
                .requiredMonthlyFromNow(requiredMonthlyFromNow)
                .monthlySaving(monthlySaving)
                .overspendingDebt(overspendingDebt)
                .monthLeft(monthLeft)
                .progressPercent(progressPercent)
                .build();
    }
}