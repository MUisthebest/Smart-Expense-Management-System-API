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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProposedAction {
        private Category category;
        private UUID budgetId;
        /** The exact new budget limit the user is consenting to (for RAISE_BUDGET). */
        private BigDecimal resolvedValue;
    }
}
