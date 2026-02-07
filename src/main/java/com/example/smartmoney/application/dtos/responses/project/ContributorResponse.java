package com.example.smartmoney.application.dtos.responses.project;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class ContributorResponse {
    private String userId;
    private BigDecimal totalAmount;
    private BigDecimal percentOfTarget;
    private LocalDate lastContributionDate;
}
