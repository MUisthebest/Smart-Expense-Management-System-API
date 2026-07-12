package com.example.smartmoney.application.usecases.suggestion;

import com.example.smartmoney.application.dtos.responses.suggestion.SuggestionResponse;
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
        return SuggestionResponse.builder()
                .id(suggestion.getId())
                .type(suggestion.getType())
                .status(suggestion.getStatus())
                .dedupKey(suggestion.getDedupKey())
                .payload(payload)
                .narrative(payload != null ? payload.getNarrative() : null)
                .createdAt(suggestion.getCreatedAt())
                .expiresAt(suggestion.getExpiresAt())
                .decidedAt(suggestion.getDecidedAt())
                .build();
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
