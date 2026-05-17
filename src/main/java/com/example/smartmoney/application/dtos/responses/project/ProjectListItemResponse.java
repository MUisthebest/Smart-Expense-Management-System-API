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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectListItemResponse {
    private String projectId;
    private String name;
    private ProjectType type;
    private ProjectStatus status;
    private ProjectPriority priority;

    private BigDecimal targetAmount;
    private String currency;

    private BigDecimal totalContributed;
    private BigDecimal progressPercent;
    private LocalDate deadline;

    public static ProjectListItemResponse fromDomain(Project p) {
        return ProjectListItemResponse.builder()
                .projectId(p.getId().toString())
                .name(p.getName())
                .type(p.getType())
                .status(p.getStatus())
                .priority(p.getPriority())
                .targetAmount(p.getTargetAmount().amount())
                .currency(p.getTargetAmount().currency())
                .totalContributed(p.totalContributed().amount())
                .progressPercent(p.progressPercent())
                .deadline(p.getDeadline())
                .build();
    }
}
