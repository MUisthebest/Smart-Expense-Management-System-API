package com.example.smartmoney.application.dtos.requests.project;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateProjectTrackingRequest {

    @NotNull(message = "moneySaved is required")
    @DecimalMin(value = "0.00", message = "moneySaved must be >= 0")
    private BigDecimal moneySaved;

    @NotNull(message = "currentMonth is required")
    @Min(value = 1, message = "currentMonth must be > 0")
    private Integer currentMonth;

    @NotNull(message = "monthLeft is required")
    @Min(value = 0, message = "monthLeft must be >= 0")
    private Integer monthLeft;

    @NotNull(message = "moneyOwed is required")
    @DecimalMin(value = "0.00", message = "moneyOwed must be >= 0")
    private BigDecimal moneyOwed;
}
