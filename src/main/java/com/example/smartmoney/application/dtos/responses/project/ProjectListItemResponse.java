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
import java.time.LocalDate;
import java.time.Period;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectListItemResponse {
    private String projectId;
    private String name;
    private ProjectType type;
    private ProjectPriority priority;
    private ProjectStatus status;
    private BigDecimal targetAmount;
    private String currency;

    private BigDecimal totalContributed;
    private BigDecimal remaining;
    private BigDecimal progressPercent;

    private LocalDate deadline;

    private Integer monthsLeft;         // 0, 1, 2,...
    private String deadlineLabel;       // "6 months", "Completed", "Overdue"

    public static ProjectListItemResponse fromDomain(Project p) {
        BigDecimal targetAmount = p.getTargetAmount().amount();
        BigDecimal totalContributed = p.netSavedAmount();
        BigDecimal progressPercent = p.progressPercent();
        BigDecimal remaining = targetAmount.subtract(totalContributed).max(BigDecimal.ZERO);

        LocalDate deadline = p.getDeadline();
        LocalDate today = LocalDate.now();

        int monthsLeft = 0;
        String deadlineLabel;

        if (progressPercent.compareTo(BigDecimal.valueOf(100)) >= 0) {
            deadlineLabel = "Completed";
        } else if (deadline != null && deadline.isBefore(today)) {
            deadlineLabel = "Overdue";
        } else {
            if (deadline != null) {
                Period period = Period.between(today, deadline);
                monthsLeft = period.getYears() * 12 + period.getMonths();
                if (period.getDays() > 0) monthsLeft += 1;
                if (monthsLeft < 1) monthsLeft = 1;
            }
            deadlineLabel = monthsLeft == 1 ? "1 month" : monthsLeft + " months";
        }

        return ProjectListItemResponse.builder()
                .projectId(p.getId().toString())
                .name(p.getName())
                .type(p.getType())
                .targetAmount(targetAmount)
                .currency(p.getTargetAmount().currency())
                .totalContributed(totalContributed)
                .remaining(remaining)
                .progressPercent(progressPercent)
                .deadline(deadline)
                .priority(p.getPriority())
                .status(p.getStatus())
                .monthsLeft(monthsLeft)
                .deadlineLabel(deadlineLabel)
                .build();
    }
}