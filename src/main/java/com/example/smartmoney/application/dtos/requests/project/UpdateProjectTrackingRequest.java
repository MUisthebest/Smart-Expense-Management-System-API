package com.example.smartmoney.application.dtos.requests.project;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateProjectTrackingRequest {

    @DecimalMin(value = "0.00", message = "moneySaved must be >= 0")
    private BigDecimal moneySaved;

    @Min(value = 1, message = "currentMonth must be > 0")
    private Integer currentMonth;

    @Min(value = 0, message = "monthLeft must be >= 0")
    private Integer monthLeft;

    @DecimalMin(value = "0.00", message = "moneyOwed must be >= 0")
    private BigDecimal moneyOwed;
}
