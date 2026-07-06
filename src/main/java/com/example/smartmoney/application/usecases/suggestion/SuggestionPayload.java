package com.example.smartmoney.application.usecases.suggestion;

import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The JSON body persisted on a {@link com.example.smartmoney.domain.models.suggestion.Suggestion}.
 * Carries an immutable snapshot of the originating insight plus the <em>resolved</em> action —
 * a concrete number the user consents to, not a recompute recipe. Serialized to / from the
 * suggestion's {@code payload} text column with the shared {@code ObjectMapper}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SuggestionPayload {

    /** Structured copy of the insight that triggered the ask (type, severity, code, metrics). */
    private Map<String, Object> insightSnapshot;

    private ProposedAction proposedAction;

    /**
     * Human-readable one-liner from the {@code InsightNarrator} (LLM), captured once at ask time so
     * the in-app card and the push body read the same sentence. {@code null} when narration is
     * off/unavailable — the FE then localizes the insight {@code code} itself (Phase-1 behaviour).
     */
    private String narrative;

    /**
     * The resolved action a suggestion proposes — a concrete number the user consents to, never
     * a recompute recipe. The shape is polymorphic across {@code SuggestionType}; each accept
     * branch validates only the fields it needs:
     * <ul>
     *   <li>RAISE_BUDGET — {@code budgetId} + {@code resolvedValue} (new limit)</li>
     *   <li>CREATE_BUDGET — {@code category} + {@code month}/{@code year} + {@code resolvedValue} (limit)</li>
     *   <li>CONTRIBUTE_TO_PROJECT — {@code projectId} + {@code resolvedValue} (contribution amount)</li>
     *   <li>REBALANCE_BUDGETS — {@code budgetAdjustments} (per-budget new limits) + {@code month}/
     *       {@code year}; {@code resolvedValue} is the new committed total, for display</li>
     *   <li>REVIEW_SUBSCRIPTION — no proposed action (acknowledge-only; the card renders from the
     *       insight snapshot)</li>
     * </ul>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProposedAction {
        private Category category;
        private UUID budgetId;
        /** Target month/year for a CREATE_BUDGET action. */
        private Integer month;
        private Integer year;
        /** Target project for a CONTRIBUTE_TO_PROJECT action. */
        private UUID projectId;
        /**
         * The exact number the user is consenting to: the new budget limit (RAISE_BUDGET /
         * CREATE_BUDGET) or the contribution amount (CONTRIBUTE_TO_PROJECT).
         */
        private BigDecimal resolvedValue;
        /**
         * REBALANCE_BUDGETS only (null for every other type): the per-budget limits the user is
         * consenting to, snapshotted at ask time. Accept honors this snapshot verbatim — it never
         * recomputes, and a budget deleted since the ask is simply skipped.
         */
        private List<BudgetAdjustment> budgetAdjustments;
    }

    /** One budget's consented new limit inside a REBALANCE_BUDGETS ask. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BudgetAdjustment {
        private UUID budgetId;
        /** For display on the card — not used by the executor. */
        private Category category;
        /** The limit at ask time, for a before → after rendering. */
        private BigDecimal currentLimit;
        /** The consented new limit the executor applies. */
        private BigDecimal newLimit;
    }
}
