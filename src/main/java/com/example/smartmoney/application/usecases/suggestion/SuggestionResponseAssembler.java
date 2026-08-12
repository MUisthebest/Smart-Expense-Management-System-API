package com.example.smartmoney.application.usecases.suggestion;

import com.example.smartmoney.application.dtos.responses.suggestion.SuggestionResponse;
import com.example.smartmoney.application.usecases.insight.NarrationCurrencyGuard;
import com.example.smartmoney.domain.models.suggestion.Suggestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Builds {@link SuggestionResponse}s, parsing the stored JSON payload once. */
@Component
@Slf4j
@RequiredArgsConstructor
public class SuggestionResponseAssembler {

    private final ObjectMapper objectMapper;

    public SuggestionResponse toResponse(Suggestion suggestion) {
        SuggestionPayload payload = parse(suggestion.getPayloadJson());
        String narrative = guardCurrency(payload);
        return SuggestionResponse.builder()
                .id(suggestion.getId())
                .type(suggestion.getType())
                .status(suggestion.getStatus())
                .dedupKey(suggestion.getDedupKey())
                .payload(payload)
                .narrative(narrative)
                .createdAt(suggestion.getCreatedAt())
                .expiresAt(suggestion.getExpiresAt())
                .decidedAt(suggestion.getDecidedAt())
                .build();
    }

    /**
     * Re-run the VND currency guard on the stored sentence, and return it.
     *
     * <p>{@link com.example.smartmoney.application.usecases.insight.InsightNarrator} already guards
     * at generation, so for anything raised since, this is a no-op. It exists for the cards raised
     * <em>before</em> it did: a suggestion freezes its narrative into the payload at ask time and is
     * created with a null {@code expiresAt}, so an untreated card would keep serving its original
     * "$" — or its ungrouped "1200000" — on every list request for as long as the user leaves it
     * undecided.
     *
     * <p>The parsed payload is mutated in step, because the FE reads the sentence from both
     * {@code narrative} and the nested {@code payload.narrative} — cleaning one and serving the
     * other verbatim would just move the bug. Nothing is written back to the database; the stored
     * row keeps its original text and this repairs the projection on the way out.
     */
    private String guardCurrency(SuggestionPayload payload) {
        if (payload == null || payload.getNarrative() == null) {
            return null;
        }
        String stored = payload.getNarrative();
        // Null when unsalvageable — the FE then localizes payload.insightSnapshot.code, which is
        // exactly the fallback it already uses for a card raised while narration was off.
        String guarded = NarrationCurrencyGuard.enforce(stored);
        if (!stored.equals(guarded)) {
            log.info("[Suggestion] repaired the amounts in a stored narrative on read (dropped={})",
                    guarded == null);
        }
        payload.setNarrative(guarded);
        return guarded;
    }

    private SuggestionPayload parse(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payloadJson, SuggestionPayload.class);
        } catch (Exception e) {
            // A malformed payload must not break the list/respond response; surface the rest.
            log.warn("[Suggestion] Could not parse payload: {}", e.getMessage());
            return null;
        }
    }
}
