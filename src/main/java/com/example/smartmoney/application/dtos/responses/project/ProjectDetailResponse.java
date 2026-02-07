package com.example.smartmoney.application.dtos.responses.project;

import com.example.smartmoney.domain.models.project.enums.ProjectType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
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
}
