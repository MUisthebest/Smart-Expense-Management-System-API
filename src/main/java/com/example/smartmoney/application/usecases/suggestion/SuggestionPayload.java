package com.example.smartmoney.application.usecases.suggestion;

import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
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
     * The resolved action a suggestion proposes — a concrete number the user consents to, never
     * a recompute recipe. The shape is polymorphic across {@code SuggestionType}; each accept
     * branch validates only the fields it needs:
     * <ul>
     *   <li>RAISE_BUDGET — {@code budgetId} + {@code resolvedValue} (new limit)</li>
     *   <li>CREATE_BUDGET — {@code category} + {@code month}/{@code year} + {@code resolvedValue} (limit)</li>
     *   <li>CONTRIBUTE_TO_PROJECT — {@code projectId} + {@code resolvedValue} (contribution amount)</li>
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
    }
}
