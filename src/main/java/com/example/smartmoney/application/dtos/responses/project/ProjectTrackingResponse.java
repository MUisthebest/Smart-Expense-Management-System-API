package com.example.smartmoney.application.dtos.responses.project;

import com.example.smartmoney.domain.models.project.ProjectTracking;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class ProjectTrackingResponse {
    private String id;
    private String projectId;
    private BigDecimal moneySaved;
    private Integer currentMonth;
    private Integer monthLeft;
    private BigDecimal moneyOwed;
    private Instant createdAt;
    private Instant updatedAt;

    public static ProjectTrackingResponse fromDomain(ProjectTracking tracking) {
        return ProjectTrackingResponse.builder()
                .id(tracking.getId().toString())
                .projectId(tracking.getProjectId().toString())
                .moneySaved(tracking.getMoneySaved())
                .currentMonth(tracking.getCurrentMonth())
                .monthLeft(tracking.getMonthLeft())
                .moneyOwed(tracking.getMoneyOwed())
                .createdAt(tracking.getCreatedAt())
                .updatedAt(tracking.getUpdatedAt())
                .build();
    }
}
