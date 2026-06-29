package com.example.smartmoney.application.dtos.requests.project;

import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request to get a monthly-saving plan for a project. The savings pace is no longer
 * supplied here — it is read from the user's {@code savingPace} trait (Pivot D /
 * redesign §5.5), so the per-request {@code Mode} (URGENT/RELAXED) was removed.
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
}
