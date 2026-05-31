package com.example.smartmoney.application.dtos.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkBudgetsResponse {
    @JsonProperty("totalCreated")
    private Integer totalCreated;

    @JsonProperty("month")
    private Integer month;

    @JsonProperty("year")
    private Integer year;

    @JsonProperty("budgets")
    private List<BudgetItemResponse> budgets;

    @JsonProperty("failedItems")
    private List<BudgetCreationError> failedItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetCreationError {
        @JsonProperty("category")
        private String category;

        @JsonProperty("error")
        private String error;
    }
}
