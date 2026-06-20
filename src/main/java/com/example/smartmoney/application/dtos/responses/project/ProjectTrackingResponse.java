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
    private BigDecimal moneySaved;   // gross reserved
    private BigDecimal moneyOwed;    // overspend debt
    private BigDecimal netSaved;     // moneySaved − moneyOwed (drives progress/completion)
    private Integer currentMonth;
    private BigDecimal monthlySaving;
    private Integer monthLeft;
    private Integer frozenMonths;
    private Instant createdAt;
    private Instant updatedAt;

    public static ProjectTrackingResponse fromDomain(ProjectTracking tracking) {
        BigDecimal saved = tracking.getMoneySaved() != null ? tracking.getMoneySaved() : BigDecimal.ZERO;
        BigDecimal owed = tracking.getMoneyOwed() != null ? tracking.getMoneyOwed() : BigDecimal.ZERO;
        BigDecimal net = saved.subtract(owed).max(BigDecimal.ZERO);
        return ProjectTrackingResponse.builder()
                .id(tracking.getId().toString())
                .projectId(tracking.getProjectId().toString())
                .moneySaved(saved)
                .moneyOwed(owed)
                .netSaved(net)
                .currentMonth(tracking.getCurrentMonth())
                .monthlySaving(tracking.getMonthlySaving())
                .monthLeft(tracking.getMonthLeft())
                .frozenMonths(tracking.getFrozenMonths())
                .createdAt(tracking.getCreatedAt())
                .updatedAt(tracking.getUpdatedAt())
                .build();
    }
}
