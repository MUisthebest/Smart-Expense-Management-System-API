package com.example.smartmoney.application.dtos.requests.budgets;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class UpdateBudgetRequest {
    @NotNull(message = "Amount limit is required")
    @DecimalMin(value = "0.01", message = "Amount limit must be greater than 0")
    private BigDecimal amountLimit;
    
    @DecimalMin(value = "0", message = "Spent amount must be greater than or equal to 0")
    private BigDecimal spent;
}
