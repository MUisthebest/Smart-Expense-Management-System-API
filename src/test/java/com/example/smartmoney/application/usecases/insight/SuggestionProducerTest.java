package com.example.smartmoney.application.usecases.insight;

import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.application.usecases.suggestion.SuggestionPayload;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.suggestion.Suggestion;
import com.example.smartmoney.domain.models.suggestion.SuggestionTopicStat;
import com.example.smartmoney.domain.models.suggestion.SuggestionType;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.SuggestionRepository;
import com.example.smartmoney.domain.repositories.SuggestionTopicStatRepository;
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
    @Mock private ProjectRepository projectRepository;
    @Mock private SuggestionRepository suggestionRepository;
    @Mock private SuggestionTopicStatRepository topicStatRepository;
    @Mock private CreateNotificationUseCase createNotificationUseCase;

    private SuggestionProducer producer;
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final UUID userId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();
    private static final LocalDate AS_OF = LocalDate.of(2026, 6, 15);

    @BeforeEach
    void setUp() {
        producer = new SuggestionProducer(new SpendingInsightAnalyzer(), transactionRepository,
                budgetRepository, userRepository, projectRepository, suggestionRepository,
                topicStatRepository, createNotificationUseCase, objectMapper);
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

    // ---- P2-A: dismissal suppression -----------------------------------------------------

    @Test
    @DisplayName("P2-A: a topic dismissed twice is suppressed for a non-CRITICAL ask")
    void suppressedAfterRepeatedDismissals() {
        stubUser(Intervention_Level.GENTLE);
        stubAnalysisData(); // WARN pace
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(topicStatRepository.find(eq(userId), anyString()))
                .thenReturn(Optional.of(topicStat("RAISE_BUDGET:FOOD:2026-06", 2)));

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        verify(suggestionRepository, never()).save(any());
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    @Test
    @DisplayName("P2-A: a CRITICAL escalation breaks through dismissal suppression")
    void criticalBreaksThroughSuppression() {
        stubUser(Intervention_Level.HARD);
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026)))
                .thenReturn(List.of(criticalPaceBudget())); // ratio 1.4 → CRITICAL
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        // No topicStat stub: a CRITICAL ask short-circuits before the suppression lookup.
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        verify(suggestionRepository).save(any(Suggestion.class));
        verify(createNotificationUseCase).createNotification(eq(userId), anyString(), startsWith("app://suggestions/"));
    }

    // ---- P2-B: CREATE_BUDGET (overspend anomaly, no budget) ------------------------------

    @Test
    @DisplayName("P2-B: OVERSPEND_ANOMALY on a category with no budget → CREATE_BUDGET at the trailing median")
    void overspendWithoutBudgetCreatesBudgetSuggestion() throws Exception {
        stubUser(Intervention_Level.GENTLE);
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(foodAnomalyHistory()); // [80,120,80,120] baseline + 145 spike → WARN
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026)))
                .thenReturn(List.of()); // no FOOD budget
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        Suggestion saved = captor.getValue();
        assertEquals(SuggestionType.CREATE_BUDGET, saved.getType());
        assertEquals("CREATE_BUDGET:FOOD:2026-06", saved.getDedupKey());

        SuggestionPayload payload = objectMapper.readValue(saved.getPayloadJson(), SuggestionPayload.class);
        SuggestionPayload.ProposedAction action = payload.getProposedAction();
        assertEquals(Category.FOOD, action.getCategory());
        assertEquals(6, action.getMonth());
        assertEquals(2026, action.getYear());
        assertNull(action.getBudgetId());
        // median of trailing 3 months [80,120,80] = 80
        assertEquals(0, new BigDecimal("80").compareTo(action.getResolvedValue()));

        verify(createNotificationUseCase).createNotification(eq(userId),
                eq("notification.suggestion.create_budget|FOOD"), startsWith("app://suggestions/"));
    }

    // ---- P2-B/D/E: CONTRIBUTE_TO_PROJECT on income, scaled by savingPace -----------------

    @Test
    @DisplayName("P2-B/D: income with savings headroom → CONTRIBUTE_TO_PROJECT toward the active project")
    void incomeHeadroomProducesContribution() throws Exception {
        UUID projectId = UUID.randomUUID();
        stubUser(Intervention_Level.GENTLE, Saving_Pace.BALANCED);
        stubSavingsData(projectId, "10000");
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduceSavingsContribution(userId, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        Suggestion saved = captor.getValue();
        assertEquals(SuggestionType.CONTRIBUTE_TO_PROJECT, saved.getType());
        assertEquals("CONTRIBUTE_TO_PROJECT:" + projectId + ":2026-06", saved.getDedupKey());

        SuggestionPayload payload = objectMapper.readValue(saved.getPayloadJson(), SuggestionPayload.class);
        assertEquals(projectId, payload.getProposedAction().getProjectId());
        // disposable 5000 × BALANCED 0.20 = 1000
        assertEquals(0, new BigDecimal("1000.00").compareTo(payload.getProposedAction().getResolvedValue()));

        verify(createNotificationUseCase).createNotification(eq(userId),
                eq("notification.suggestion.contribute_to_project|Trip"), startsWith("app://suggestions/"));
    }

    // P2-E: savingPace scales the contribution (disposable 5000, project remaining 10000 — no cap binds).
    @Test
    @DisplayName("P2-E: RELAXED suggests 10% of disposable")
    void relaxedPaceScales() {
        assertEquals(0, new BigDecimal("500.00").compareTo(contributionAmountFor(Saving_Pace.RELAXED)));
    }

    @Test
    @DisplayName("P2-E: BALANCED suggests 20% of disposable")
    void balancedPaceScales() {
        assertEquals(0, new BigDecimal("1000.00").compareTo(contributionAmountFor(Saving_Pace.BALANCED)));
    }

    @Test
    @DisplayName("P2-E: AGGRESIVE suggests 30% of disposable")
    void aggresivePaceScales() {
        assertEquals(0, new BigDecimal("1500.00").compareTo(contributionAmountFor(Saving_Pace.AGGRESIVE)));
    }

    @Test
    @DisplayName("P2-E: the suggested contribution never exceeds what the project still needs")
    void contributionCappedAtProjectRemaining() throws Exception {
        UUID projectId = UUID.randomUUID();
        stubUser(Intervention_Level.GENTLE, Saving_Pace.AGGRESIVE); // 0.30 × 5000 = 1500
        stubSavingsData(projectId, "400"); // project only needs 400
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduceSavingsContribution(userId, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        SuggestionPayload payload = objectMapper.readValue(captor.getValue().getPayloadJson(), SuggestionPayload.class);
        assertEquals(0, new BigDecimal("400.00").compareTo(payload.getProposedAction().getResolvedValue()));
    }

    @Test
    @DisplayName("P2-B: no active saving project → no contribution suggestion")
    void noProjectNoContribution() {
        stubUser(Intervention_Level.GENTLE, Saving_Pace.BALANCED);
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(incomeHistory());
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026))).thenReturn(List.of());
        when(projectRepository.findAllByUser(any())).thenReturn(List.of());

        producer.maybeProduceSavingsContribution(userId, AS_OF);

        verify(suggestionRepository, never()).save(any());
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    // ---- helpers for the Phase-2 cases ---------------------------------------------------

    /** FOOD budget projected to 1400 (700 by day 15 of 30) vs 1000 limit → ratio 1.4 → CRITICAL. */
    private Budget criticalPaceBudget() {
        return Budget.builder().id(budgetId).userId(userId).category(Category.FOOD)
                .amountLimit(new BigDecimal("1000")).spent(new BigDecimal("700")).month(6).year(2026).build();
    }

    private void stubUser(Intervention_Level level, Saving_Pace pace) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(
                User.builder().id(userId).interventionLevel(level).savingPace(pace).build()));
    }

    private SuggestionTopicStat topicStat(String dedupKey, int dismissCount) {
        return SuggestionTopicStat.builder()
                .id(UUID.randomUUID()).userId(userId).dedupKey(dedupKey).dismissCount(dismissCount).build();
    }

    private Transaction expense(Category category, int year, int month, String amount) {
        return Transaction.create(userId, new Amount(new BigDecimal(amount)), TransactionType.EXPENSE,
                category, "test", java.time.LocalDateTime.of(year, month, 10, 12, 0));
    }

    private List<Transaction> foodAnomalyHistory() {
        List<Transaction> history = new java.util.ArrayList<>();
        history.add(expense(Category.FOOD, 2026, 5, "80"));
        history.add(expense(Category.FOOD, 2026, 4, "120"));
        history.add(expense(Category.FOOD, 2026, 3, "80"));
        history.add(expense(Category.FOOD, 2026, 2, "120"));
        history.add(expense(Category.FOOD, 2026, 6, "145")); // z = 2.25 → WARN
        return history;
    }

    private List<Transaction> incomeHistory() {
        List<Transaction> history = new java.util.ArrayList<>();
        history.add(Transaction.create(userId, new Amount(new BigDecimal("5000")), TransactionType.INCOME,
                Category.OTHER, "salary", java.time.LocalDateTime.of(2026, 6, 5, 12, 0)));
        return history;
    }

    private Project activeProject(UUID projectId, String remaining) {
        Project project = mock(Project.class);
        when(project.getStatus()).thenReturn(ProjectStatus.ACTIVE);
        when(project.isSubPersonal()).thenReturn(false);
        when(project.remaining()).thenReturn(new Money(new BigDecimal(remaining), "VND"));
        when(project.getId()).thenReturn(new ProjectId(projectId));
        when(project.getName()).thenReturn("Trip");
        // getDeadline() intentionally unstubbed: min() over a single-element stream never compares.
        return project;
    }

    private void stubSavingsData(UUID projectId, String remaining) {
        // Build the project mock before the stubbing call below — nesting one when(...) inside
        // another's thenReturn argument confuses Mockito (UnfinishedStubbing).
        Project project = activeProject(projectId, remaining);
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(incomeHistory());
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026))).thenReturn(List.of());
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(project));
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
    }

    private BigDecimal contributionAmountFor(Saving_Pace pace) {
        stubUser(Intervention_Level.GENTLE, pace);
        stubSavingsData(UUID.randomUUID(), "10000");
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduceSavingsContribution(userId, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        try {
            return objectMapper.readValue(captor.getValue().getPayloadJson(), SuggestionPayload.class)
                    .getProposedAction().getResolvedValue();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
