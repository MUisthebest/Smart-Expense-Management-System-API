package com.example.smartmoney.application.usecases.suggestion;

import com.example.smartmoney.application.dtos.requests.budgets.CreateBudgetRequest;
import com.example.smartmoney.application.dtos.requests.budgets.UpdateBudgetRequest;
import com.example.smartmoney.application.dtos.requests.project.AddProjectContributionRequest;
import com.example.smartmoney.application.dtos.responses.suggestion.SuggestionResponse;
import com.example.smartmoney.application.usecases.budget.CreateBudgetUseCase;
import com.example.smartmoney.application.usecases.budget.UpdateBudgetUseCase;
import com.example.smartmoney.application.usecases.project.AddProjectContributionUseCase;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.suggestion.Suggestion;
import com.example.smartmoney.domain.models.suggestion.SuggestionStatus;
import com.example.smartmoney.domain.models.suggestion.SuggestionTopicStat;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.SuggestionRepository;
import com.example.smartmoney.domain.repositories.SuggestionTopicStatRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Records the user's yes/no decision on a {@link Suggestion} and, on accept, executes the
 * whitelisted action (C6). The status flip and the action run in one transaction, and the
 * transition is idempotent: a repeated or losing-race call is a safe no-op.
 *
 * <p>P2-A: a real dismissal (the first time a PENDING row flips to DISMISSED) increments the
 * per-topic {@link SuggestionTopicStat}, in the same transaction, so the producer can later
 * back off on topics the user keeps declining.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RespondToSuggestionUseCase {

    private final SuggestionRepository suggestionRepository;
    private final SuggestionTopicStatRepository topicStatRepository;
    private final BudgetRepository budgetRepository;
    private final UpdateBudgetUseCase updateBudgetUseCase;
    private final CreateBudgetUseCase createBudgetUseCase;
    private final AddProjectContributionUseCase addProjectContributionUseCase;
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
        } else {
            recordDismissal(suggestion); // P2-A: feed the suppression learning loop
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
            case RAISE_BUDGET, REDUCE_BUDGET -> resizeBudget(payload, userId);
            case CREATE_BUDGET, SET_CATEGORY_LIMIT -> createBudget(payload, userId);
            case CONTRIBUTE_TO_PROJECT -> contributeToProject(payload, userId);
            case REBALANCE_BUDGETS -> rebalanceBudgets(payload, userId);
            // Acknowledge-only: the real action (cancelling the subscription) happens outside the
            // app; "yes" just marks it reviewed. The ACCEPTED flip is the entire effect.
            case REVIEW_SUBSCRIPTION -> { }
        }
    }

    /** Set an existing budget's limit to the consented value — shared by RAISE_BUDGET and REDUCE_BUDGET. */
    private void resizeBudget(SuggestionPayload payload, UUID userId) {
        SuggestionPayload.ProposedAction action = requireAction(payload);
        if (action.getBudgetId() == null || action.getResolvedValue() == null) {
            throw new DomainException("Budget-resize suggestion is missing budgetId or resolvedValue");
        }
        UpdateBudgetRequest request = new UpdateBudgetRequest();
        request.setAmountLimit(action.getResolvedValue()); // honor the consented number
        updateBudgetUseCase.execute(action.getBudgetId(), userId, request);
    }

    private void createBudget(SuggestionPayload payload, UUID userId) {
        SuggestionPayload.ProposedAction action = requireAction(payload);
        if (action.getCategory() == null || action.getResolvedValue() == null
                || action.getMonth() == null || action.getYear() == null) {
            throw new DomainException("CREATE_BUDGET suggestion is missing category, month, year or resolvedValue");
        }
        CreateBudgetRequest request = new CreateBudgetRequest();
        request.setCategory(action.getCategory());
        request.setAmountLimit(action.getResolvedValue()); // the consented limit
        request.setMonth(action.getMonth());
        request.setYear(action.getYear());
        createBudgetUseCase.execute(userId, request);
    }

    private void contributeToProject(SuggestionPayload payload, UUID userId) {
        SuggestionPayload.ProposedAction action = requireAction(payload);
        if (action.getProjectId() == null || action.getResolvedValue() == null
                || action.getResolvedValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new DomainException("CONTRIBUTE_TO_PROJECT suggestion is missing projectId or a positive resolvedValue");
        }
        AddProjectContributionRequest request = new AddProjectContributionRequest();
        request.setAmount(action.getResolvedValue()); // the consented contribution amount
        addProjectContributionUseCase.execute(
                userId.toString(), action.getProjectId().toString(), request);
    }

    /**
     * Apply a REBALANCE_BUDGETS snapshot: set every listed budget to its consented new limit, inside
     * the surrounding transaction. Consent is to the concrete numbers taken at ask time, so the
     * snapshot is honored verbatim — never recomputed — even if limits changed in between; a budget
     * deleted since the ask is skipped. Ownership of each budget is enforced by
     * {@link UpdateBudgetUseCase}, which rejects any budget that isn't the caller's.
     */
    private void rebalanceBudgets(SuggestionPayload payload, UUID userId) {
        SuggestionPayload.ProposedAction action = requireAction(payload);
        List<SuggestionPayload.BudgetAdjustment> adjustments = action.getBudgetAdjustments();
        if (adjustments == null || adjustments.isEmpty()) {
            throw new DomainException("REBALANCE_BUDGETS suggestion has no budget adjustments");
        }
        // Validate the whole snapshot before touching anything — all-or-nothing consent.
        for (SuggestionPayload.BudgetAdjustment adjustment : adjustments) {
            if (adjustment == null || adjustment.getBudgetId() == null || adjustment.getNewLimit() == null
                    || adjustment.getNewLimit().compareTo(BigDecimal.ZERO) <= 0) {
                throw new DomainException("REBALANCE_BUDGETS adjustment is missing budgetId or a positive newLimit");
            }
        }
        for (SuggestionPayload.BudgetAdjustment adjustment : adjustments) {
            if (budgetRepository.findById(adjustment.getBudgetId()).isEmpty()) {
                log.info("[Suggestion] rebalance: budget {} was deleted after the ask — skipping it",
                        adjustment.getBudgetId());
                continue;
            }
            UpdateBudgetRequest request = new UpdateBudgetRequest();
            request.setAmountLimit(adjustment.getNewLimit()); // the consented snapshot number
            // No rebalance re-check mid-loop: the loop's end state is exactly the consented fit.
            updateBudgetUseCase.execute(adjustment.getBudgetId(), userId, request, false);
        }
    }

    /** Increment the (user, topic) dismissal counter, creating the stat row on first dismissal. */
    private void recordDismissal(Suggestion suggestion) {
        try {
            SuggestionTopicStat stat = topicStatRepository
                    .find(suggestion.getUserId(), suggestion.getDedupKey())
                    .orElseGet(() -> SuggestionTopicStat.start(
                            suggestion.getUserId(), suggestion.getDedupKey()));
            stat.recordDismissal(Instant.now());
            topicStatRepository.save(stat);
        } catch (Exception e) {
            // Suppression learning is best-effort: never fail a dismissal because the counter slipped.
            log.warn("[Suggestion] could not record dismissal for topic {}: {}",
                    suggestion.getDedupKey(), e.getMessage());
        }
    }

    private SuggestionPayload.ProposedAction requireAction(SuggestionPayload payload) {
        SuggestionPayload.ProposedAction action = payload != null ? payload.getProposedAction() : null;
        if (action == null) {
            throw new DomainException("Suggestion payload has no proposed action");
        }
        return action;
    }

    private SuggestionPayload parsePayload(Suggestion suggestion) {
        try {
            return objectMapper.readValue(suggestion.getPayloadJson(), SuggestionPayload.class);
        } catch (Exception e) {
            throw new DomainException("Could not parse suggestion payload: " + e.getMessage());
        }
    }
}
