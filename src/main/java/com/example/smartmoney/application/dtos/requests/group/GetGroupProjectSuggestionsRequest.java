package com.example.smartmoney.application.dtos.requests.group;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class GetGroupProjectSuggestionsRequest {
    @NotBlank(message = "groupId is required")
    private String groupId;

    /** Admin's desired total amount — backend suggests minimum months. */
    private BigDecimal inputAmount;

    /** Admin's desired total months — backend suggests achievable amount. */
    private Integer inputMonths;
}
