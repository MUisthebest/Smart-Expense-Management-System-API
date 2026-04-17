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
public class BudgetListResponse {
    @JsonProperty("month")
    private Integer month;
    
    @JsonProperty("year")
    private Integer year;
    
    @JsonProperty("items")
    private List<BudgetItemResponse> items;
}
