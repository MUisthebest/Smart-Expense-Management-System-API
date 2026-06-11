package com.example.smartmoney.application.dtos.responses.group;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class GroupProjectSuggestionsResponse {
    /** Sum of all JOINED members' capacity snapshots. */
    private BigDecimal totalCapacity;
    /** Minimum months needed to reach inputAmount at totalCapacity per month. */
    private Integer suggestedMonths;
    /** Amount achievable if saving at totalCapacity for inputMonths. */
    private BigDecimal suggestedAmount;
}
