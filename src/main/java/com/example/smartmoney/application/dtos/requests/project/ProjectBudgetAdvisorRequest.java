package com.example.smartmoney.application.dtos.requests.project;

import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request to get an AI-generated budget split advice for a project.
 * Same fields as {@link CreateProjectRequest} plus a {@code mode} field.
 */
@Data
public class ProjectBudgetAdvisorRequest {

    @NotBlank(message = "Project name is required")
    @Size(max = 120)
    private String name;

    @Size(max = 500)
    private String description;

    @NotNull(message = "Project type is required")
    private ProjectType type;

    private ProjectPriority priority;

    @NotNull(message = "Target amount is required")
    @DecimalMin(value = "0.01", message = "Target amount must be > 0")
    private BigDecimal targetAmount;

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotNull(message = "Deadline is required")
    private LocalDate deadline;

    /**
     * Savings mode: {@code URGENT} → minimise months, {@code RELAXED} → add 1 month to deadline.
     */
    @NotNull(message = "mode is required (URGENT or RELAXED)")
    private Mode mode;

    public enum Mode { URGENT, RELAXED }
}
