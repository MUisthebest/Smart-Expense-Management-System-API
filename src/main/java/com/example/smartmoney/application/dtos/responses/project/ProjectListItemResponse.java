package com.example.smartmoney.application.dtos.responses.project;

import com.example.smartmoney.domain.models.project.enums.ProjectType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class ProjectListItemResponse {
    private String projectId;
    private String name;
    private ProjectType type;

    private BigDecimal targetAmount;
    private String currency;

    private BigDecimal totalContributed;
    private BigDecimal progressPercent;
    private LocalDate deadline;
}
