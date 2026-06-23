package com.example.smartmoney.application.dtos.responses.project;

import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
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
    private ProjectStatusReason statusReason;
    private Integer monthsLeft;        // calendar months to deadline (deadline countdown)
    private Integer paceMonthsLeft;    // pace-aware months left from tracking; null when no tracking
    private PaceStatus paceStatus;     // paceMonthsLeft vs monthsLeft
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

        Integer trackingMonthLeft = p.getTracking() != null ? p.getTracking().getMonthLeft() : null;
        ProjectStatusReason statusReason = deriveStatusReason(
                domainStatus, progressPercent, deadline, today, monthsLeft, trackingMonthLeft);
        PaceStatus paceStatus = derivePaceStatus(domainStatus, monthsLeft, trackingMonthLeft);

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
                .statusReason(statusReason)
                .monthsLeft(monthsLeft)
                .paceMonthsLeft(trackingMonthLeft)
                .paceStatus(paceStatus)
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

    /**
     * Machine-readable explanation behind the status, for the UI's "why" banner.
     * For EXPIRED we can only infer the cause (deadline vs stuck-frozen) since the
     * domain persists the status, not the reason: monthLeft &le; 0 implies the
     * deadline ran out, otherwise it expired from staying frozen too long.
     */
    private static ProjectStatusReason deriveStatusReason(
            ProjectStatus domainStatus,
            BigDecimal progressPercent,
            LocalDate deadline,
            LocalDate today,
            int monthsLeft,
            Integer trackingMonthLeft) {
        switch (domainStatus) {
            case ABANDONED:
                return ProjectStatusReason.ABANDONED_BY_USER;
            case FROZEN:
                return ProjectStatusReason.FROZEN_DEBT;
            case COMPLETED:
                return ProjectStatusReason.COMPLETED;
            case CANCELLED:
                return ProjectStatusReason.NONE;
            case EXPIRED:
                return (trackingMonthLeft != null && trackingMonthLeft <= 0)
                        ? ProjectStatusReason.EXPIRED_DEADLINE
                        : ProjectStatusReason.EXPIRED_FROZEN_TOO_LONG;
            case ACTIVE:
            default:
                if (progressPercent.compareTo(BigDecimal.valueOf(100)) >= 0) {
                    return ProjectStatusReason.COMPLETED;
                }
                boolean overdue = deadline != null && deadline.isBefore(today);
                boolean behindPace = trackingMonthLeft != null && monthsLeft > 0 && trackingMonthLeft > monthsLeft;
                return (overdue || behindPace) ? ProjectStatusReason.BEHIND_PACE : ProjectStatusReason.ON_TRACK;
        }
    }

    /**
     * Pace-aware months left ({@code trackingMonthLeft}) vs the calendar countdown
     * ({@code monthsLeft}). Mirrors {@link ProjectTrackingResponse}'s pace logic so both
     * responses agree. NOT_APPLICABLE for terminal projects or when tracking is absent.
     */
    private static PaceStatus derivePaceStatus(ProjectStatus domainStatus, int monthsLeft, Integer trackingMonthLeft) {
        if (trackingMonthLeft == null || isTerminal(domainStatus)) {
            return PaceStatus.NOT_APPLICABLE;
        }
        if (trackingMonthLeft < monthsLeft) return PaceStatus.AHEAD;
        if (trackingMonthLeft > monthsLeft) return PaceStatus.BEHIND;
        return PaceStatus.ON_TRACK;
    }

    private static boolean isTerminal(ProjectStatus status) {
        return status == ProjectStatus.COMPLETED
                || status == ProjectStatus.EXPIRED
                || status == ProjectStatus.ABANDONED
                || status == ProjectStatus.CANCELLED;
    }
}
