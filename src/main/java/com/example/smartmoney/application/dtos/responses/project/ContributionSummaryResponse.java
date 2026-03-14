package com.example.smartmoney.application.dtos.responses.project;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ContributionSummaryResponse {
    private BigDecimal totalContributed;
    private BigDecimal remaining;
    private BigDecimal progressPercent;
    private List<ContributorResponse> contributors;
}
