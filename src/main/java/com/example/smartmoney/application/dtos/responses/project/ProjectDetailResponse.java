package com.example.smartmoney.application.dtos.responses.project;

import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;

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

    public static ProjectDetailResponse fromDomain(Project p) {
        BigDecimal targetAmount = p.getTargetAmount().amount();
        BigDecimal totalContributed = p.totalContributed().amount();
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

            if (deadline != null) {
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

        return ProjectDetailResponse.builder()
                .projectId(p.getId().toString())
                .ownerId(p.getOwnerId().toString())
                .type(p.getType())
                .name(p.getName())
                .description(p.getDescription())
                .targetAmount(p.getTargetAmount().amount())
                .currency(p.getTargetAmount().currency())
                .totalContributed(p.totalContributed().amount())
                .remaining(p.remaining().amount())
                .progressPercent(p.progressPercent())
                .deadline(deadline)
                .status(status)
                .monthsLeft(monthsLeft)
                .deadlineLabel(deadlineLabel)
                .statusLabel(statusLabel)
                .build();
    }
}
