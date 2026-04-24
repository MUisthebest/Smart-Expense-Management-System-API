package com.example.smartmoney.application.dtos.requests.project;

import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class UpdateProjectRequest {
    @Size(max = 120, message = "Project name must not exceed 120 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @DecimalMin(value = "0.01", message = "Target amount must be > 0")
    private BigDecimal targetAmount;

    @Size(max = 10, message = "Currency must not exceed 10 characters")
    private String currency;

    private LocalDate deadline;

    private ProjectStatus status;

    private ProjectPriority priority;
}
