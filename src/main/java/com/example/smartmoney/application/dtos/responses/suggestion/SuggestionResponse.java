package com.example.smartmoney.application.dtos.responses.suggestion;

import com.example.smartmoney.application.usecases.suggestion.SuggestionPayload;
import com.example.smartmoney.domain.models.suggestion.SuggestionStatus;
import com.example.smartmoney.domain.models.suggestion.SuggestionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * FE-facing view of a {@link com.example.smartmoney.domain.models.suggestion.Suggestion}
 * for the in-app yes/no card screen. The payload is returned parsed so the card can render
 * the proposed action without re-parsing JSON.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SuggestionResponse {
    private UUID id;
    private SuggestionType type;
    private SuggestionStatus status;
    private String dedupKey;
    private SuggestionPayload payload;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant decidedAt;
}
