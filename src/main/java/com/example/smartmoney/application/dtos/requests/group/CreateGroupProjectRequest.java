package com.example.smartmoney.application.dtos.requests.group;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateGroupProjectRequest {
    @NotBlank(message = "Group ID is required")
    private String groupId;

    @NotBlank(message = "Project name is required")
    @Size(max = 255)
    private String name;

    private String description;

    @NotNull(message = "Target amount is required")
    @DecimalMin(value = "0.01", message = "Target amount must be > 0")
    private BigDecimal targetAmount;

    @NotBlank(message = "Currency is required")
    @Size(max = 10)
    private String currency;

    @NotNull(message = "Total months is required")
    @Min(value = 1, message = "Total months must be >= 1")
    @Max(value = 60, message = "Total months must be <= 60")
    private Integer totalMonths;
}
