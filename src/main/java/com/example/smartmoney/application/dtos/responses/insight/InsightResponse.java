package com.example.smartmoney.application.dtos.responses.insight;

import com.example.smartmoney.domain.models.insight.Insight;
import com.example.smartmoney.domain.models.insight.InsightType;
import com.example.smartmoney.domain.models.insight.Severity;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * FE-facing contract for a pulled insight (C3). Deliberately carries a stable {@code code}
 * for i18n and a generic {@code metrics} map for rendering, so the front-end can present
 * insights without server churn as new metric keys appear.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InsightResponse {
    private InsightType type;
    private Category category;
    private Severity severity;
    /** ISO year-month, e.g. "2026-06". */
    private String period;
    private String code;
    private Map<String, Object> metrics;

    public static InsightResponse fromDomain(Insight insight) {
        return InsightResponse.builder()
                .type(insight.getType())
                .category(insight.getCategory())
                .severity(insight.getSeverity())
                .period(insight.getPeriod() != null ? insight.getPeriod().toString() : null)
                .code(insight.getCode())
                .metrics(insight.getMetrics())
                .build();
    }
}
