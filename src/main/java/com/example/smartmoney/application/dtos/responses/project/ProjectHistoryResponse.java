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
    private Integer monthLeftBefore;
    private Integer monthLeftAfter;
    private Instant createdAt;

    public static ProjectHistoryResponse fromDomain(ProjectHistory history) {
        return ProjectHistoryResponse.builder()
                .id(history.getId())
                .projectId(history.getProjectId().value())
                .year(history.getYear())
                .month(history.getMonth())
                .moneySavedBefore(history.getMoneySavedBefore())
                .moneySavedAfter(history.getMoneySavedAfter())
                .monthlySaving(history.getMonthlySaving())
                .penalty(history.getPenalty())
                .surplusInvested(history.getSurplusInvested())
                .monthLeftBefore(history.getMonthLeftBefore())
                .monthLeftAfter(history.getMonthLeftAfter())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
