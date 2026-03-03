package com.example.smartmoney.application.dtos.responses.project;

import com.example.smartmoney.domain.models.project.ProjectContribution;
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

    public static ProjectContributionResponse fromDomain(ProjectContribution c) {
        return ProjectContributionResponse.builder()
                .contributionId(c.getId().toString())
                .userId(c.getUserId().toString())
                .amount(c.getMoney().amount())
                .currency(c.getMoney().currency())
                .note(c.getNote())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
