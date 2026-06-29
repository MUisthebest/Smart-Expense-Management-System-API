package com.example.smartmoney.application.usecases.suggestion;

import com.example.smartmoney.application.dtos.requests.budgets.UpdateBudgetRequest;
import com.example.smartmoney.application.usecases.budget.UpdateBudgetUseCase;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.suggestion.Suggestion;
import com.example.smartmoney.domain.models.suggestion.SuggestionStatus;
import com.example.smartmoney.domain.models.suggestion.SuggestionType;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.repositories.SuggestionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RespondToSuggestionUseCase — idempotent decision + action")
class RespondToSuggestionUseCaseTest {

    @Mock private SuggestionRepository suggestionRepository;
    @Mock private UpdateBudgetUseCase updateBudgetUseCase;

    private RespondToSuggestionUseCase useCase;
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    private final UUID userId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();
    private Suggestion suggestion;

    @BeforeEach
    void setUp() {
        useCase = new RespondToSuggestionUseCase(
                suggestionRepository, updateBudgetUseCase,
                new SuggestionResponseAssembler(objectMapper), objectMapper);
        suggestion = raiseBudgetSuggestion(userId);
    }

    private Suggestion raiseBudgetSuggestion(UUID owner) {
        SuggestionPayload payload = SuggestionPayload.builder()
                .proposedAction(SuggestionPayload.ProposedAction.builder()
                        .category(Category.FOOD)
                        .budgetId(budgetId)
                        .resolvedValue(new BigDecimal("1500"))
                        .build())
                .build();
        try {
            return Suggestion.create(owner, SuggestionType.RAISE_BUDGET,
                    objectMapper.writeValueAsString(payload), "RAISE_BUDGET:FOOD:2026-06", null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("accept flips to ACCEPTED and raises the budget to the resolved value once")
    void acceptExecutesRaiseBudget() {
        when(suggestionRepository.findById(suggestion.getId())).thenReturn(Optional.of(suggestion));
        when(suggestionRepository.markDecidedIfPending(eq(suggestion.getId()), eq(userId),
                eq(SuggestionStatus.ACCEPTED), any())).thenReturn(1);

        useCase.execute(userId, suggestion.getId(), true);

        ArgumentCaptor<UpdateBudgetRequest> captor = ArgumentCaptor.forClass(UpdateBudgetRequest.class);
        verify(updateBudgetUseCase).execute(eq(budgetId), eq(userId), captor.capture());
        assertEquals(0, new BigDecimal("1500").compareTo(captor.getValue().getAmountLimit()));
    }

    @Test
    @DisplayName("double-accept executes the action only once (idempotent no-op on the 2nd call)")
    void doubleAcceptExecutesOnce() {
        when(suggestionRepository.findById(suggestion.getId())).thenReturn(Optional.of(suggestion));
        // 0 rows → already decided / lost the race → no action.
        when(suggestionRepository.markDecidedIfPending(eq(suggestion.getId()), eq(userId),
                eq(SuggestionStatus.ACCEPTED), any())).thenReturn(0);

        useCase.execute(userId, suggestion.getId(), true);

        verify(updateBudgetUseCase, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("dismiss flips to DISMISSED and runs no action")
    void dismissRunsNoAction() {
        when(suggestionRepository.findById(suggestion.getId())).thenReturn(Optional.of(suggestion));
        when(suggestionRepository.markDecidedIfPending(eq(suggestion.getId()), eq(userId),
                eq(SuggestionStatus.DISMISSED), any())).thenReturn(1);

        useCase.execute(userId, suggestion.getId(), false);

        verify(updateBudgetUseCase, never()).execute(any(), any(), any());
    }

    @Test
    @DisplayName("a non-owner is rejected and nothing is decided")
    void rejectsNonOwner() {
        Suggestion othersSuggestion = raiseBudgetSuggestion(UUID.randomUUID());
        when(suggestionRepository.findById(othersSuggestion.getId())).thenReturn(Optional.of(othersSuggestion));

        assertThrows(DomainException.class,
                () -> useCase.execute(userId, othersSuggestion.getId(), true));

        verify(suggestionRepository, never()).markDecidedIfPending(any(), any(), any(), any());
        verify(updateBudgetUseCase, never()).execute(any(), any(), any());
    }
}
