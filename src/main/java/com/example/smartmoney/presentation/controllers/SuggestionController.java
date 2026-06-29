package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.suggestion.RespondToSuggestionRequest;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.suggestion.SuggestionResponse;
import com.example.smartmoney.application.usecases.suggestion.ListSuggestionsUseCase;
import com.example.smartmoney.application.usecases.suggestion.RespondToSuggestionUseCase;
import com.example.smartmoney.domain.models.suggestion.SuggestionStatus;
import com.example.smartmoney.infrastructure.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suggestions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Suggestions", description = "Persisted yes/no suggestions derived from high-value insights")
@SecurityRequirement(name = "bearerAuth")
public class SuggestionController {

    private final RespondToSuggestionUseCase respondToSuggestionUseCase;
    private final ListSuggestionsUseCase listSuggestionsUseCase;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(
            summary = "List suggestions",
            description = "List the authenticated user's suggestions filtered by status (defaults to PENDING)."
    )
    @GetMapping
    public ResponseEntity<CheckResponse<List<SuggestionResponse>>> listSuggestions(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Status filter (default PENDING)")
            @RequestParam(required = false) SuggestionStatus status) {

        try {
            UUID userId = extractUserIdFromToken(authHeader);
            List<SuggestionResponse> suggestions = listSuggestionsUseCase.execute(userId, status);

            return ResponseEntity.ok(CheckResponse.<List<SuggestionResponse>>builder()
                    .success(true)
                    .message("Suggestions retrieved successfully")
                    .data(suggestions)
                    .build());

        } catch (Exception e) {
            log.error("Failed to retrieve suggestions", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    CheckResponse.<List<SuggestionResponse>>builder()
                            .success(false)
                            .message("Failed to retrieve suggestions: " + e.getMessage())
                            .build());
        }
    }

    @Operation(
            summary = "Respond to a suggestion",
            description = "Accept (execute the proposed action) or dismiss a suggestion. Idempotent: "
                    + "responding again returns the current state without re-running the action."
    )
    @PostMapping("/{id}/respond")
    public ResponseEntity<CheckResponse<SuggestionResponse>> respond(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Suggestion ID", required = true)
            @PathVariable UUID id,
            @Valid @RequestBody RespondToSuggestionRequest request) {

        try {
            UUID userId = extractUserIdFromToken(authHeader);
            SuggestionResponse result = respondToSuggestionUseCase.execute(userId, id, request.getAccept());

            return ResponseEntity.ok(CheckResponse.<SuggestionResponse>builder()
                    .success(true)
                    .message(request.getAccept() ? "Suggestion accepted" : "Suggestion dismissed")
                    .data(result)
                    .build());

        } catch (Exception e) {
            log.error("Failed to respond to suggestion ID: {}", id, e);

            HttpStatus httpStatus = e.getMessage() != null && e.getMessage().contains("not found")
                    ? HttpStatus.NOT_FOUND
                    : e.getMessage() != null && e.getMessage().contains("does not belong")
                    ? HttpStatus.FORBIDDEN
                    : HttpStatus.BAD_REQUEST;

            return ResponseEntity.status(httpStatus).body(
                    CheckResponse.<SuggestionResponse>builder()
                            .success(false)
                            .message("Failed to respond to suggestion: " + e.getMessage())
                            .build());
        }
    }

    private UUID extractUserIdFromToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return jwtTokenProvider.getUserIdFromToken(authHeader.substring(7));
        }
        throw new IllegalArgumentException("Invalid Authorization header");
    }
}
