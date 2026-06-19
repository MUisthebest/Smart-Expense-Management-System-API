package com.example.smartmoney.application.dtos.responses.project;

import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

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

    // ── UI enrichment (Phase 0) ───────────────────────────────────────────────
    private Integer monthsToDeadline;          // calendar months from today to deadline
    private PaceStatus paceStatus;             // monthLeft vs monthsToDeadline
    private Integer maxFrozenMonths;           // config cap; render frozenMonths/maxFrozenMonths
    private Integer debtClearEstimateMonths;   // ceil(moneyOwed / monthlySaving); null when no debt

    private Instant createdAt;
    private Instant updatedAt;

    public static ProjectTrackingResponse fromDomain(ProjectTracking tracking, Project project, int maxFrozenMonths) {
        BigDecimal saved = tracking.getMoneySaved() != null ? tracking.getMoneySaved() : BigDecimal.ZERO;
        BigDecimal owed = tracking.getMoneyOwed() != null ? tracking.getMoneyOwed() : BigDecimal.ZERO;
        BigDecimal net = saved.subtract(owed).max(BigDecimal.ZERO);
        BigDecimal monthlySaving = tracking.getMonthlySaving();

        Integer monthsToDeadline = calculateMonthsToDeadline(project);
        PaceStatus paceStatus = calculatePaceStatus(project, tracking.getMonthLeft(), monthsToDeadline);
        Integer debtClearEstimateMonths = calculateDebtClearEstimate(owed, monthlySaving);

        return ProjectTrackingResponse.builder()
                .id(tracking.getId().toString())
                .projectId(tracking.getProjectId().toString())
                .moneySaved(saved)
                .moneyOwed(owed)
                .netSaved(net)
                .currentMonth(tracking.getCurrentMonth())
                .monthlySaving(monthlySaving)
                .monthLeft(tracking.getMonthLeft())
                .frozenMonths(tracking.getFrozenMonths())
                .monthsToDeadline(monthsToDeadline)
                .paceStatus(paceStatus)
                .maxFrozenMonths(maxFrozenMonths)
                .debtClearEstimateMonths(debtClearEstimateMonths)
                .createdAt(tracking.getCreatedAt())
                .updatedAt(tracking.getUpdatedAt())
                .build();
    }

    /** Calendar months from today to the deadline (ceiling, floored at 0). Null when no deadline. */
    private static Integer calculateMonthsToDeadline(Project project) {
        if (project == null || project.getDeadline() == null) return null;
        LocalDate today = LocalDate.now();
        LocalDate deadline = project.getDeadline();
        if (!deadline.isAfter(today)) return 0;
        Period period = Period.between(today, deadline);
        int months = period.getYears() * 12 + period.getMonths();
        if (period.getDays() > 0) months += 1;
        return Math.max(0, months);
    }

    private static PaceStatus calculatePaceStatus(Project project, int monthLeft, Integer monthsToDeadline) {
        if (project == null || monthsToDeadline == null || isTerminal(project.getStatus())) {
            return PaceStatus.NOT_APPLICABLE;
        }
        if (monthLeft < monthsToDeadline) return PaceStatus.AHEAD;
        if (monthLeft > monthsToDeadline) return PaceStatus.BEHIND;
        return PaceStatus.ON_TRACK;
    }

    private static Integer calculateDebtClearEstimate(BigDecimal owed, BigDecimal monthlySaving) {
        if (owed.compareTo(BigDecimal.ZERO) <= 0
                || monthlySaving == null || monthlySaving.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return owed.divide(monthlySaving, 0, RoundingMode.CEILING).intValue();
    }

    private static boolean isTerminal(ProjectStatus status) {
        return status == ProjectStatus.COMPLETED
                || status == ProjectStatus.EXPIRED
                || status == ProjectStatus.ABANDONED
                || status == ProjectStatus.CANCELLED;
    }
}
