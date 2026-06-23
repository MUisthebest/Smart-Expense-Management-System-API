package com.example.smartmoney.application.dtos.responses.project;

import com.example.smartmoney.domain.models.project.ProjectHistory;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ProjectHistoryResponse {
    private UUID id;
    private UUID projectId;
    private Integer year;
    private Integer month;
    private BigDecimal moneySavedBefore;
    private BigDecimal moneySavedAfter;
    private BigDecimal monthlySaving;
    private BigDecimal penalty;
    private BigDecimal surplusInvested;
    private BigDecimal netChange;      // moneySavedAfter − moneySavedBefore
    private HistoryOutcome outcome;    // classifies the month for the UI narrative
    private Integer monthLeftBefore;
    private Integer monthLeftAfter;
    private Instant createdAt;

    public static ProjectHistoryResponse fromDomain(ProjectHistory history) {
        BigDecimal before = history.getMoneySavedBefore();
        BigDecimal after = history.getMoneySavedAfter();
        BigDecimal netChange = after.subtract(before);
        return ProjectHistoryResponse.builder()
                .id(history.getId())
                .projectId(history.getProjectId().value())
                .year(history.getYear())
                .month(history.getMonth())
                .moneySavedBefore(before)
                .moneySavedAfter(after)
                .monthlySaving(history.getMonthlySaving())
                .penalty(history.getPenalty())
                .surplusInvested(history.getSurplusInvested())
                .netChange(netChange)
                .outcome(classifyOutcome(history.getPenalty(), history.getSurplusInvested(), netChange))
                .monthLeftBefore(history.getMonthLeftBefore())
                .monthLeftAfter(history.getMonthLeftAfter())
                .createdAt(history.getCreatedAt())
                .build();
    }

    private static HistoryOutcome classifyOutcome(BigDecimal penalty, BigDecimal surplusInvested, BigDecimal netChange) {
        if (penalty != null && penalty.compareTo(BigDecimal.ZERO) > 0) {
            return HistoryOutcome.OVERSPENT;
        }
        if (surplusInvested != null && surplusInvested.compareTo(BigDecimal.ZERO) > 0) {
            return HistoryOutcome.UNDERSPENT_BONUS;
        }
        if (netChange.compareTo(BigDecimal.ZERO) > 0) {
            return HistoryOutcome.CLEAN_MONTH;
        }
        return HistoryOutcome.FROZEN_NO_SAVING;
    }
}
