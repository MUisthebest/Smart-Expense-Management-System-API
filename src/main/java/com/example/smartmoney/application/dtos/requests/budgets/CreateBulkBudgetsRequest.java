package com.example.smartmoney.application.dtos.requests.budgets;

import com.example.smartmoney.domain.models.transaction.enums.Category;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateBulkBudgetsRequest {
    @NotNull(message = "Budgets list is required")
    @NotEmpty(message = "Budgets list cannot be empty")
    @Size(min = 1, max = 20, message = "Budgets list must contain between 1 and 20 items")
    private List<BudgetItem> budgets;

    @NotNull(message = "Month is required")
    @Min(value = 1, message = "Month must be between 1 and 12")
    @Max(value = 12, message = "Month must be between 1 and 12")
    private Integer month;

    @NotNull(message = "Year is required")
    @Min(value = 1900, message = "Year must be valid (>= 1900)")
    private Integer year;

    @Data
    public static class BudgetItem {
        @NotNull(message = "Category is required")
        private Category category;

        @NotNull(message = "Amount limit is required")
        @DecimalMin(value = "0.01", message = "Amount limit must be greater than 0")
        private BigDecimal amountLimit;
    }
}
