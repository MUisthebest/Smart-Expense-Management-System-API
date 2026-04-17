package com.example.smartmoney.application.dtos.responses;

import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BudgetItemResponse {
    @JsonProperty("budgetId")
    private UUID budgetId;
    
    @JsonProperty("userId")
    private UUID userId;
    
    @JsonProperty("category")
    private Category category;
    
    @JsonProperty("amountLimit")
    private BigDecimal amountLimit;
    
    @JsonProperty("month")
    private Integer month;
    
    @JsonProperty("year")
    private Integer year;
    
    @JsonProperty("spent")
    private BigDecimal spent;
    
    @JsonProperty("remaining")
    private BigDecimal remaining;
    
    @JsonProperty("progressPercent")
    private Double progressPercent;
    
    @JsonProperty("alertLevel")
    private Budget.AlertLevel alertLevel;
    
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;
    
    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;
}
