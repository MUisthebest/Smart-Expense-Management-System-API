package com.example.smartmoney.application.dtos.responses.project;

import com.example.smartmoney.domain.models.project.Project;
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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDetailResponse {
    private String projectId;
    private String ownerId;
    private ProjectType type;

    private String name;
    private String description;

    private BigDecimal targetAmount;
    private String currency;

    private BigDecimal totalContributed;
    private BigDecimal remaining;
    private BigDecimal progressPercent;

    private LocalDate deadline;

    private String status;          // ONGOING | COMPLETED | OVERDUE
    private Integer monthsLeft;     // 0, 1, 2,...
    private String deadlineLabel;   // "6 months", "Completed", "Overdue"
    private String statusLabel;     // "3 months left", "Completed", "Overdue"

    private com.example.smartmoney.domain.models.project.enums.ProjectPriority priority;

    private BigDecimal monthlySaving;
    private Instant createdAt;
    private Integer durationMonths;
    private Integer currentMonth;
    private BigDecimal moneyOwed;

    private List<ProjectHistoryResponse> histories;
    private List<ProjectMemberResponse> members;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectMemberResponse {
        private String userId;
        private String username;
        private String email;
        private String fullName;
        private String joinStatus;
        private boolean admin;
    }

    public static ProjectDetailResponse fromDomain(Project p) {
        return fromDomain(p, List.of(), List.of());
    }

    public static ProjectDetailResponse fromDomain(Project p, List<ProjectHistoryResponse> histories) {
        return fromDomain(p, histories, List.of());
    }

    public static ProjectDetailResponse fromDomain(Project p, List<ProjectHistoryResponse> histories, List<ProjectMemberResponse> members) {
        ProjectDetailResponse detail = fromDomainInternal(p, histories);
        detail.setMembers(members);
        return detail;
    }

    private static ProjectDetailResponse fromDomainInternal(Project p, List<ProjectHistoryResponse> histories) {
        BigDecimal targetAmount = p.getTargetAmount().amount();
        BigDecimal totalContributed = p.getTracking() != null ? p.getTracking().getMoneySaved() : p.totalContributed().amount();
        BigDecimal progressPercent = p.progressPercent();
        BigDecimal remaining = targetAmount.subtract(totalContributed).max(BigDecimal.ZERO);

        LocalDate deadline = p.getDeadline();
        LocalDate today = LocalDate.now();

        String status;
        int monthsLeft = 0;
        String deadlineLabel;
        String statusLabel;

        if (progressPercent.compareTo(BigDecimal.valueOf(100)) >= 0) {
            status = "COMPLETED";
            deadlineLabel = "Completed";
            statusLabel = "Completed";
        } else if (deadline != null && deadline.isBefore(today)) {
            status = "OVERDUE";
            deadlineLabel = "Overdue";
            statusLabel = "Overdue";
        } else {
            status = "ONGOING";

            if (p.getTracking() != null) {
                monthsLeft = p.getTracking().getMonthLeft();
            } else if (deadline != null) {
                Period period =  Period.between(today, deadline);
                monthsLeft = period.getYears() * 12 + period.getMonths();

                if (deadline.getDayOfMonth() >= today.getDayOfMonth()) {
                    monthsLeft += 1;
                }

                if (monthsLeft < 1) {
                    monthsLeft = 1;
                }
            }

            deadlineLabel = monthsLeft == 1 ? "1 month" : monthsLeft + " months";
            statusLabel = monthsLeft == 1 ? "1 month left" : monthsLeft + " months left";
        }

        BigDecimal monthlySaving = p.getTracking() != null ? p.getTracking().getMonthlySaving() : BigDecimal.ZERO;
        Instant createdAt = p.getCreatedAt();
        int durationMonths = 0;
        if (p.getCreatedAt() != null && deadline != null) {
            LocalDate createdDate = p.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            Period period = Period.between(createdDate, deadline);
            durationMonths = period.getYears() * 12 + period.getMonths();
            if (deadline.getDayOfMonth() >= createdDate.getDayOfMonth()) {
                durationMonths += 1;
            }
            if (durationMonths < 1) {
                durationMonths = 1;
            }
        }
        Integer currentMonth = p.getTracking() != null ? p.getTracking().getCurrentMonth() : null;
        BigDecimal moneyOwed = p.getTracking() != null ? p.getTracking().getMoneyOwed() : BigDecimal.ZERO;

        return ProjectDetailResponse.builder()
                .projectId(p.getId().toString())
                .ownerId(p.getOwnerId().toString())
                .type(p.getType())
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
                .createdAt(createdAt)
                .durationMonths(durationMonths)
                .currentMonth(currentMonth)
                .moneyOwed(moneyOwed)
                .histories(histories)
                .build();
    }
}
