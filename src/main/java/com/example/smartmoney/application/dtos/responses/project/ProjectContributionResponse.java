package com.example.smartmoney.application.dtos.responses.project;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
public class ProjectContributionResponse {
    private String contributionId;
    private String userId;

    private BigDecimal amount;
    private String currency;

    private LocalDate contributionDate;
    private String note;
    private Instant createdAt;
}
