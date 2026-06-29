package com.example.smartmoney.application.usecases.insight;

import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.application.usecases.suggestion.SuggestionPayload;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.suggestion.Suggestion;
import com.example.smartmoney.domain.models.suggestion.SuggestionType;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.SuggestionRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.services.SpendingInsightAnalyzer;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SuggestionProducer — intervention-gated RAISE_BUDGET")
class SuggestionProducerTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private UserRepository userRepository;
    @Mock private SuggestionRepository suggestionRepository;
    @Mock private CreateNotificationUseCase createNotificationUseCase;

    private SuggestionProducer producer;
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final UUID userId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();
    private static final LocalDate AS_OF = LocalDate.of(2026, 6, 15);

    @BeforeEach
    void setUp() {
        producer = new SuggestionProducer(new SpendingInsightAnalyzer(), transactionRepository,
                budgetRepository, userRepository, suggestionRepository, createNotificationUseCase, objectMapper);
    }

    /** FOOD budget projected to overshoot (600 by day 15 of 30 → 1200 vs 1000 limit) → WARN pace. */
    private Budget warnPaceBudget() {
        return Budget.builder().id(budgetId).userId(userId).category(Category.FOOD)
                .amountLimit(new BigDecimal("1000")).spent(new BigDecimal("600")).month(6).year(2026).build();
    }

    private void stubUser(Intervention_Level level) {
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.builder().id(userId).interventionLevel(level).build()));
    }

    private void stubAnalysisData() {
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026)))
                .thenReturn(List.of(warnPaceBudget()));
    }

    @Test
    @DisplayName("NOTIFY users get no suggestion and no push")
    void notifyUserProducesNothing() {
        stubUser(Intervention_Level.NOTIFY);

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        verify(suggestionRepository, never()).save(any());
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    @Test
    @DisplayName("GENTLE + WARN → persists a RAISE_BUDGET suggestion and pushes a deep-linked notification")
    void gentleWarnInsertsAndPushes() throws Exception {
        stubUser(Intervention_Level.GENTLE);
        stubAnalysisData();
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        Suggestion saved = captor.getValue();
        assertEquals(SuggestionType.RAISE_BUDGET, saved.getType());
        assertEquals("RAISE_BUDGET:FOOD:2026-06", saved.getDedupKey());

        SuggestionPayload payload = objectMapper.readValue(saved.getPayloadJson(), SuggestionPayload.class);
        assertEquals(budgetId, payload.getProposedAction().getBudgetId());
        assertEquals(Category.FOOD, payload.getProposedAction().getCategory());
        // resolvedValue = max(median=0, projected=1200) = 1200
        assertEquals(0, new BigDecimal("1200").compareTo(payload.getProposedAction().getResolvedValue()));

        verify(createNotificationUseCase).createNotification(eq(userId), anyString(),
                startsWith("app://suggestions/"));
    }

    @Test
    @DisplayName("dedup: an existing open ask short-circuits — no second insert, no push")
    void dedupSkips() {
        stubUser(Intervention_Level.GENTLE);
        stubAnalysisData();
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.of(mock(Suggestion.class)));

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        verify(suggestionRepository, never()).save(any());
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }
}
