package com.example.smartmoney.application.usecases.suggestion;

import com.example.smartmoney.application.dtos.requests.budgets.UpdateBudgetRequest;
import com.example.smartmoney.application.dtos.responses.suggestion.SuggestionResponse;
import com.example.smartmoney.application.usecases.budget.UpdateBudgetUseCase;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.suggestion.Suggestion;
import com.example.smartmoney.domain.models.suggestion.SuggestionStatus;
import com.example.smartmoney.domain.repositories.SuggestionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Records the user's yes/no decision on a {@link Suggestion} and, on accept, executes the
 * whitelisted action (C6). The status flip and the action run in one transaction, and the
 * transition is idempotent: a repeated or losing-race call is a safe no-op.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RespondToSuggestionUseCase {

    private final SuggestionRepository suggestionRepository;
    private final UpdateBudgetUseCase updateBudgetUseCase;
    private final SuggestionResponseAssembler assembler;
    private final ObjectMapper objectMapper;

    @Transactional
    public SuggestionResponse execute(UUID userId, UUID suggestionId, boolean accept) {
        if (userId == null) {
            throw new DomainException("User ID is required");
        }
        if (suggestionId == null) {
            throw new DomainException("Suggestion ID is required");
        }

        Suggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new DomainException("Suggestion not found with id: " + suggestionId));

        if (!userId.equals(suggestion.getUserId())) {
            throw new DomainException("Suggestion does not belong to the authenticated user");
        }

        SuggestionStatus newStatus = accept ? SuggestionStatus.ACCEPTED : SuggestionStatus.DISMISSED;

        // Idempotent transition: only a PENDING row owned by this user is touched.
        int updated = suggestionRepository.markDecidedIfPending(
                suggestionId, userId, newStatus, Instant.now());

        if (updated == 0) {
            // Already decided (or not transitionable) → no-op; return the current state.
            log.info("[Suggestion] respond no-op (already decided): id={}, status={}",
                    suggestionId, suggestion.getStatus());
            return assembler.toResponse(suggestion);
        }

        if (accept) {
            executeAction(suggestion, userId);
        }

        Suggestion decided = suggestionRepository.findById(suggestionId).orElse(suggestion);
        log.info("[Suggestion] respond applied: id={}, accept={}, newStatus={}",
                suggestionId, accept, newStatus);
        return assembler.toResponse(decided);
    }

    /** Whitelist of executable actions — anything not matched here is intentionally inert. */
    private void executeAction(Suggestion suggestion, UUID userId) {
        SuggestionPayload payload = parsePayload(suggestion);
        switch (suggestion.getType()) {
            case RAISE_BUDGET -> raiseBudget(payload, userId);
        }
    }

    private void raiseBudget(SuggestionPayload payload, UUID userId) {
        SuggestionPayload.ProposedAction action = payload != null ? payload.getProposedAction() : null;
        if (action == null || action.getBudgetId() == null || action.getResolvedValue() == null) {
            throw new DomainException("RAISE_BUDGET suggestion is missing budgetId or resolvedValue");
        }
        UpdateBudgetRequest request = new UpdateBudgetRequest();
        request.setAmountLimit(action.getResolvedValue()); // honor the consented number
        updateBudgetUseCase.execute(action.getBudgetId(), userId, request);
    }

    private SuggestionPayload parsePayload(Suggestion suggestion) {
        try {
            return objectMapper.readValue(suggestion.getPayloadJson(), SuggestionPayload.class);
        } catch (Exception e) {
            throw new DomainException("Could not parse suggestion payload: " + e.getMessage());
        }
    }
}
