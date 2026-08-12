package com.example.smartmoney.application.usecases.suggestion;

import com.example.smartmoney.application.dtos.responses.suggestion.SuggestionResponse;
import com.example.smartmoney.domain.models.suggestion.Suggestion;
import com.example.smartmoney.domain.models.suggestion.SuggestionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A suggestion freezes its narrative into the payload at ask time and is created with a null
 * {@code expiresAt}, so cards raised before the currency guard existed sit in the list
 * indefinitely. The assembler is the only thing standing between those stored sentences and the
 * screen — the FE renders {@code narrative} verbatim.
 */
@DisplayName("SuggestionResponseAssembler — stored narratives are re-guarded on the way out")
class SuggestionResponseAssemblerTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final SuggestionResponseAssembler assembler = new SuggestionResponseAssembler(objectMapper);

    private Suggestion suggestionWith(String payloadJson) {
        return Suggestion.create(UUID.randomUUID(), SuggestionType.RAISE_BUDGET,
                payloadJson, "dedup-" + UUID.randomUUID(), null);
    }

    private String payloadJsonWithNarrative(String narrative) {
        try {
            return objectMapper.writeValueAsString(SuggestionPayload.builder()
                    .narrative(narrative)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("a legacy '$' narrative is relabelled VND on both narrative and payload.narrative")
    void legacyDollarNarrativeIsRepaired() {
        Suggestion suggestion = suggestionWith(payloadJsonWithNarrative(
                "Raise your food budget to $1,500,000 to stay on pace."));

        SuggestionResponse response = assembler.toResponse(suggestion);

        String expected = "Raise your food budget to 1,500,000 VND to stay on pace.";
        assertEquals(expected, response.getNarrative());
        // The FE reads the sentence from both places; cleaning one and serving the other verbatim
        // would simply move the bug.
        assertEquals(expected, response.getPayload().getNarrative());
    }

    @Test
    @DisplayName("an ungrouped stored amount is regrouped as whole dong on both fields")
    void legacyUngroupedAmountIsFormatted() {
        Suggestion suggestion = suggestionWith(payloadJsonWithNarrative(
                "Raise your food budget to 1500000.00 VND to stay on pace."));

        SuggestionResponse response = assembler.toResponse(suggestion);

        String expected = "Raise your food budget to 1,500,000 VND to stay on pace.";
        assertEquals(expected, response.getNarrative());
        assertEquals(expected, response.getPayload().getNarrative());
    }

    @Test
    @DisplayName("an unsalvageable legacy narrative is dropped, so the FE localizes the code instead")
    void unsalvageableLegacyNarrativeIsDropped() {
        Suggestion suggestion = suggestionWith(payloadJsonWithNarrative(
                "Your budget is tracked in USD this month."));

        SuggestionResponse response = assembler.toResponse(suggestion);

        assertNull(response.getNarrative());
        assertNull(response.getPayload().getNarrative());
    }

    @Test
    @DisplayName("a clean narrative passes through untouched (the guard is idempotent)")
    void cleanNarrativeIsUnchanged() {
        String clean = "Raise your food budget to 1,500,000 VND to stay on pace.";
        Suggestion suggestion = suggestionWith(payloadJsonWithNarrative(clean));

        SuggestionResponse response = assembler.toResponse(suggestion);

        assertEquals(clean, response.getNarrative());
        assertEquals(clean, response.getPayload().getNarrative());
    }

    @Test
    @DisplayName("a card with no narrative (narration off) still assembles")
    void missingNarrativeIsTolerated() {
        Suggestion suggestion = suggestionWith(payloadJsonWithNarrative(null));

        SuggestionResponse response = assembler.toResponse(suggestion);

        assertNull(response.getNarrative());
        assertNotNull(response.getPayload());
    }

    @Test
    @DisplayName("a malformed payload still yields a response (unchanged behaviour)")
    void malformedPayloadIsTolerated() {
        Suggestion suggestion = suggestionWith("{not json");

        SuggestionResponse response = assembler.toResponse(suggestion);

        assertNull(response.getPayload());
        assertNull(response.getNarrative());
        assertEquals(SuggestionType.RAISE_BUDGET, response.getType());
    }
}
