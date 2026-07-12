package com.example.smartmoney.domain.models.insight;

import com.example.smartmoney.domain.models.transaction.enums.Category;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.YearMonth;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable result of the deterministic {@code SpendingInsightAnalyzer}. Carries the
 * structured facts ({@link #metrics}) — never prose — plus a stable {@link #code} the
 * front-end uses for i18n. Phrasing with an LLM is deferred to Phase 3; nothing here
 * does or stores natural language. See docs/redesign-workstream-c-plan.md §C1.1.
 */
@Getter
@Builder
@ToString
public class Insight {
    private final InsightType type;
    /** Nullable: some insight types are not category-scoped. */
    private final Category category;
    private final Severity severity;
    private final YearMonth period;
    /** Structured metrics (baseline, actual, zScore, projectedEndOfMonth, ...) — NOT prose. */
    private final Map<String, Object> metrics;
    /** Stable i18n key, e.g. "insight.overspend_anomaly.food". */
    private final String code;

    public Map<String, Object> getMetrics() {
        return metrics == null ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
    }
}
