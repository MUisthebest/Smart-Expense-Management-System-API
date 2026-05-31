package com.example.smartmoney.application.dtos.requests.budgets;

import com.example.smartmoney.domain.models.transaction.enums.Category;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateBudgetRequest {
    @NotNull(message = "Category is required")
    private Category category;
    
    @NotNull(message = "Amount limit is required")
    @DecimalMin(value = "0.01", message = "Amount limit must be greater than 0")
    private BigDecimal amountLimit;
    
    @NotNull(message = "Month is required")
    @Min(value = 1, message = "Month must be between 1 and 12")
    @Max(value = 12, message = "Month must be between 1 and 12")
    private Integer month;
    
    @NotNull(message = "Year is required")
    @Min(value = 1900, message = "Year must be valid (>= 1900)")
    private Integer year;
}
