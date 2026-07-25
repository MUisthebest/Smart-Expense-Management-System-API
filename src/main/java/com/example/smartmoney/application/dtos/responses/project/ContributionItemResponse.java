package com.example.smartmoney.application.dtos.responses.project;

import com.example.smartmoney.domain.models.project.ProjectContribution;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A single contribution row for the project contribution history: a manual
 * press-to-contribute or a settlement-credited auto-saving. Sum of these rows is
 * the project's totalContributed (the source of progress).
 */
@Data
@Builder
public class ContributionItemResponse {
    private UUID id;
    private UUID userId;
    private BigDecimal amount;
    private String currency;
    private String note;
    private Instant createdAt;
    private boolean auto;   // true when credited by the monthly settlement engine, not a manual press

    /** Note stamped by settlement's auto-saving; used to flag system-credited contributions. */
    private static final String AUTO_SAVED_NOTE = "Auto-saved this month";

    public static ContributionItemResponse fromDomain(ProjectContribution c) {
        return ContributionItemResponse.builder()
                .id(c.getId().value())
                .userId(c.getUserId().value())
                .amount(c.getMoney().amount())
                .currency(c.getMoney().currency())
                .note(c.getNote())
                .createdAt(c.getCreatedAt())
                .auto(AUTO_SAVED_NOTE.equals(c.getNote()))
                .build();
    }
}
