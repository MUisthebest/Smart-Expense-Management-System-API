package com.example.smartmoney.application.dtos.requests.project;

import com.example.smartmoney.domain.models.project.enums.ProjectType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CreateProjectRequest {
    @NotBlank(message = "Project name is required")
    @Size(max = 120, message = "Project name must not exceed 120 characters")
    private String name;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @NotNull(message = "Project type is required")
    private ProjectType type;

    @NotNull(message = "Target amount is required")
    @DecimalMin(value = "0.01", message = "Target amount must be > 0")
    private BigDecimal targetAmount;

    @NotBlank(message = "Currency is required")
    @Size(max = 10, message = "Currency must not exceed 10 characters")
    private String currency;

    @NotNull(message = "Deadline is required")
    private LocalDate deadline;
}
