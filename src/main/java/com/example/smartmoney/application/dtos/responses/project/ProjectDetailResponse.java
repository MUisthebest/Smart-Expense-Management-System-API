package com.example.smartmoney.application.dtos.responses.project;

import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDetailResponse {
    private String projectId;
    private String ownerId;
    private ProjectType type;
    private UUID groupProjectId;

    private String name;
    private String description;

    private BigDecimal targetAmount;
    private String currency;

    private BigDecimal totalContributed;
    private BigDecimal remaining;
    private BigDecimal progressPercent;

    private LocalDate deadline;

    private String status;
    private Integer monthsLeft;
    private String deadlineLabel;
    private String statusLabel;

    private ProjectPriority priority;

    private BigDecimal monthlySaving;
    private Instant createdAt;
    private Integer durationMonths;
    private Integer currentMonth;
    private BigDecimal moneyOwed;
    private BigDecimal netSaved;

    private List<ProjectHistoryResponse> histories;

    public static ProjectDetailResponse fromDomain(Project p) {
        return fromDomain(p, List.of());
    }

    public static ProjectDetailResponse fromDomain(Project p, List<ProjectHistoryResponse> histories) {
        BigDecimal targetAmount = p.getTargetAmount().amount();
        // Net savings (money_saved − money_owed) is the source of truth for progress.
        BigDecimal totalContributed = p.netSavedAmount();
        BigDecimal progressPercent = p.progressPercent();
        BigDecimal remaining = targetAmount.subtract(totalContributed).max(BigDecimal.ZERO);

        LocalDate deadline = p.getDeadline();
        LocalDate today = LocalDate.now();

        String status;
        int monthsLeft = 0;
        String deadlineLabel;
        String statusLabel;

        // Reflect the real domain lifecycle status first; fall back to the
        // progress/deadline-derived display status only while genuinely live.
        com.example.smartmoney.domain.models.project.enums.ProjectStatus domainStatus = p.getStatus();
        if (domainStatus == com.example.smartmoney.domain.models.project.enums.ProjectStatus.FROZEN
                || domainStatus == com.example.smartmoney.domain.models.project.enums.ProjectStatus.EXPIRED
                || domainStatus == com.example.smartmoney.domain.models.project.enums.ProjectStatus.ABANDONED
                || domainStatus == com.example.smartmoney.domain.models.project.enums.ProjectStatus.CANCELLED) {
            status = domainStatus.name();
            deadlineLabel = capitalize(domainStatus.name());
            statusLabel = capitalize(domainStatus.name());
        } else if (progressPercent.compareTo(BigDecimal.valueOf(100)) >= 0
                || domainStatus == com.example.smartmoney.domain.models.project.enums.ProjectStatus.COMPLETED) {
            status = "COMPLETED";
            deadlineLabel = "Completed";
            statusLabel = "Completed";
        } else if (deadline != null && deadline.isBefore(today)) {
            status = "OVERDUE";
            deadlineLabel = "Overdue";
            statusLabel = "Overdue";
        } else {
            status = "ONGOING";
            if (deadline != null) {
                Period period = Period.between(today, deadline);
                monthsLeft = period.getYears() * 12 + period.getMonths();
                if (period.getDays() > 0) monthsLeft += 1;
                if (monthsLeft < 1) monthsLeft = 1;
            }
            deadlineLabel = monthsLeft == 1 ? "1 month" : monthsLeft + " months";
            statusLabel = monthsLeft == 1 ? "1 month left" : monthsLeft + " months left";
        }

        BigDecimal monthlySaving = p.getTracking() != null ? p.getTracking().getMonthlySaving() : BigDecimal.ZERO;
        int durationMonths = 0;
        if (p.getCreatedAt() != null && deadline != null) {
            LocalDate createdDate = p.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            Period period = Period.between(createdDate, deadline);
            durationMonths = period.getYears() * 12 + period.getMonths();
            if (deadline.getDayOfMonth() >= createdDate.getDayOfMonth()) durationMonths += 1;
            if (durationMonths < 1) durationMonths = 1;
        }
        Integer currentMonth = p.getTracking() != null ? p.getTracking().getCurrentMonth() : null;
        BigDecimal moneyOwed = p.getTracking() != null ? p.getTracking().getMoneyOwed() : BigDecimal.ZERO;

        return ProjectDetailResponse.builder()
                .projectId(p.getId().toString())
                .ownerId(p.getOwnerId().toString())
                .type(p.getType())
                .groupProjectId(p.getGroupProjectId())
                .name(p.getName())
                .description(p.getDescription())
                .targetAmount(p.getTargetAmount().amount())
                .currency(p.getTargetAmount().currency())
                .totalContributed(totalContributed)
                .remaining(remaining)
                .progressPercent(progressPercent)
                .deadline(deadline)
                .status(status)
                .monthsLeft(monthsLeft)
                .deadlineLabel(deadlineLabel)
                .statusLabel(statusLabel)
                .priority(p.getPriority())
                .monthlySaving(monthlySaving)
                .createdAt(p.getCreatedAt())
                .durationMonths(durationMonths)
                .currentMonth(currentMonth)
                .moneyOwed(moneyOwed)
                .netSaved(totalContributed)
                .histories(histories)
                .build();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
