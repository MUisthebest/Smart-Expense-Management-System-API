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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
    @Mock private InsightNarrator narrator; // narrate() returns null by default → push uses the i18n code

    private SuggestionProducer producer;
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final UUID userId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();
    private static final LocalDate AS_OF = LocalDate.of(2026, 6, 15);
    // Fixed "now" (noon UTC on AS_OF) so the P3-A cooldown / daily-cap rules are deterministic.
    private static final Instant NOW = AS_OF.atTime(12, 0).toInstant(ZoneOffset.UTC);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    // Generous defaults so the pre-existing tests aren't throttled; tuned per-test where it matters.
    private final SuggestionFatigueProperties fatigue = new SuggestionFatigueProperties(3, 24);

    @BeforeEach
    void setUp() {
        producer = new SuggestionProducer(new SpendingInsightAnalyzer(), transactionRepository,
                budgetRepository, userRepository, projectRepository, suggestionRepository,
                topicStatRepository, createNotificationUseCase, objectMapper, fatigue, clock, narrator);
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

    // ---- affordability cap: a budget limit never exceeds income --------------------------

    @Test
    @DisplayName("RAISE_BUDGET: the proposed limit is capped at the user's monthly income")
    void raiseBudgetCappedAtIncome() throws Exception {
        // projected 1200 (600 by day 15 of 30) would be the raw raise; income 1100 clamps it.
        stubUserWithIncome(Intervention_Level.GENTLE, "1100");
        stubAnalysisData();
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        SuggestionPayload payload = objectMapper.readValue(captor.getValue().getPayloadJson(), SuggestionPayload.class);
        // raw 1200 → capped to income 1100
        assertEquals(0, new BigDecimal("1100").compareTo(payload.getProposedAction().getResolvedValue()));
    }

    @Test
    @DisplayName("RAISE_BUDGET: if the income cap leaves nothing above the current limit, no ask is raised")
    void raiseBudgetSuppressedWhenCapNotAboveLimit() {
        // income 1000 == the current limit → capping the raise to 1000 is no raise at all → skip.
        stubUserWithIncome(Intervention_Level.GENTLE, "1000");
        stubAnalysisData();
        lenient().when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());

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

    // ---- P3-A: cooldown / daily cap ------------------------------------------------------

    @Test
    @DisplayName("P3-A: a topic pushed within the cooldown window is held back (no second push)")
    void cooldownBlocksRepeatTopic() {
        stubUser(Intervention_Level.GENTLE);
        stubAnalysisData(); // WARN pace
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        // Last pushed 1h ago, cooldown is 24h → still cooling down.
        when(topicStatRepository.find(eq(userId), anyString()))
                .thenReturn(Optional.of(pushedTopicStat(NOW.minus(Duration.ofHours(1)))));

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        verify(suggestionRepository, never()).save(any());
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    @Test
    @DisplayName("P3-A: once the cooldown has elapsed the topic can be pushed again")
    void cooldownExpiredAllowsRepeat() {
        stubUser(Intervention_Level.GENTLE);
        stubAnalysisData(); // WARN pace
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        // Last pushed 25h ago, cooldown is 24h → window elapsed.
        when(topicStatRepository.find(eq(userId), anyString()))
                .thenReturn(Optional.of(pushedTopicStat(NOW.minus(Duration.ofHours(25)))));
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        verify(suggestionRepository).save(any(Suggestion.class));
        verify(createNotificationUseCase).createNotification(eq(userId), anyString(), startsWith("app://suggestions/"));
    }

    @Test
    @DisplayName("P3-A overflow: at the daily cap the card is still created, but a single roll-up ping replaces the per-suggestion push")
    void dailyCapOverflowCreatesCardWithRollup() {
        stubUser(Intervention_Level.GENTLE);
        stubAnalysisData(); // WARN pace
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.countTowardDailyCap(eq(userId), any()))
                .thenReturn(3); // exactly at the cap → first overflow today
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        // The actionable card is still persisted — nothing is silently lost.
        verify(suggestionRepository).save(any(Suggestion.class));
        // The ping is the single roll-up to the list, NOT a per-suggestion deep link.
        verify(createNotificationUseCase).createNotification(eq(userId),
                eq("notification.digest.pending_suggestions"), eq("app://suggestions"));
        verify(createNotificationUseCase, never()).createNotification(eq(userId), anyString(),
                startsWith("app://suggestions/"));
        // No individual push happened → the topic's last-pushed time is not stamped.
        verify(topicStatRepository, never()).save(any());
    }

    @Test
    @DisplayName("P3-A overflow: past the first crossing the card is created but NO ping is sent (one roll-up per day)")
    void dailyCapOverflowBeyondFirstSendsNoPing() {
        stubUser(Intervention_Level.GENTLE);
        stubAnalysisData(); // WARN pace
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.countTowardDailyCap(eq(userId), any()))
                .thenReturn(4); // already past the cap → not the first overflow
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        verify(suggestionRepository).save(any(Suggestion.class));
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    @Test
    @DisplayName("P3-A: a CRITICAL escalation bypasses both the cooldown and the daily cap")
    void criticalBypassesCooldownAndDailyCap() {
        stubUser(Intervention_Level.HARD);
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026)))
                .thenReturn(List.of(criticalPaceBudget())); // ratio 1.4 → CRITICAL
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        // Cooldown active + cap exceeded — both should be ignored for a CRITICAL ask (lenient: skipped).
        lenient().when(topicStatRepository.find(eq(userId), anyString()))
                .thenReturn(Optional.of(pushedTopicStat(NOW.minus(Duration.ofMinutes(1)))));
        lenient().when(suggestionRepository.countTowardDailyCap(eq(userId), any())).thenReturn(99);
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        verify(suggestionRepository).save(any(Suggestion.class));
        verify(createNotificationUseCase).createNotification(eq(userId), anyString(), startsWith("app://suggestions/"));
    }

    @Test
    @DisplayName("P3-A: a successful push stamps the topic's last-pushed time (drives the next cooldown)")
    void successfulPushStampsLastPushedAt() {
        stubUser(Intervention_Level.GENTLE);
        stubAnalysisData(); // WARN pace
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        ArgumentCaptor<SuggestionTopicStat> captor = ArgumentCaptor.forClass(SuggestionTopicStat.class);
        verify(topicStatRepository).save(captor.capture());
        SuggestionTopicStat stamped = captor.getValue();
        assertEquals(NOW, stamped.getLastPushedAt());
        assertEquals("RAISE_BUDGET:FOOD:2026-06", stamped.getDedupKey());
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
        // Trailing-3-month median [80,120,80] = 80, but never below this month's actual (145) — a
        // limit under the overspend would be born already-EXCEEDED once CreateBudget backfills spent.
        assertEquals(0, new BigDecimal("145").compareTo(action.getResolvedValue()));

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

    private void stubUserWithIncome(Intervention_Level level, String income) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(
                User.builder().id(userId).interventionLevel(level).income(new BigDecimal(income)).build()));
    }

    private void stubUser(Intervention_Level level, Saving_Pace pace) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(
                User.builder().id(userId).interventionLevel(level).savingPace(pace).build()));
    }

    private SuggestionTopicStat topicStat(String dedupKey, int dismissCount) {
        return SuggestionTopicStat.builder()
                .id(UUID.randomUUID()).userId(userId).dedupKey(dedupKey).dismissCount(dismissCount).build();
    }

    /** A topic stat last pushed at {@code lastPushedAt}, with no dismissals (so only cooldown applies). */
    private SuggestionTopicStat pushedTopicStat(Instant lastPushedAt) {
        return SuggestionTopicStat.builder()
                .id(UUID.randomUUID()).userId(userId).dedupKey("RAISE_BUDGET:FOOD:2026-06")
                .dismissCount(0).lastPushedAt(lastPushedAt).build();
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

    // ---- Wave 1: SET_CATEGORY_LIMIT (category trend, no budget, no anomaly) ---------------

    /** FOOD climbing 100→190 over Jan–Jun: an upward trend, but June's z-score stays below WARN. */
    private List<Transaction> risingFoodTrendHistory() {
        List<Transaction> history = new java.util.ArrayList<>();
        int[] amounts = {100, 120, 140, 160, 180, 190};
        for (int i = 0; i < amounts.length; i++) {
            history.add(expense(Category.FOOD, 2026, i + 1, String.valueOf(amounts[i])));
        }
        return history;
    }

    @Test
    @DisplayName("Wave 1: CATEGORY_TREND on a budgetless category (no anomaly) → SET_CATEGORY_LIMIT at the trailing median")
    void trendWithoutBudgetSetsCategoryLimit() throws Exception {
        stubUser(Intervention_Level.GENTLE);
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(risingFoodTrendHistory());
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026)))
                .thenReturn(List.of()); // no FOOD budget
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        Suggestion saved = captor.getValue();
        assertEquals(SuggestionType.SET_CATEGORY_LIMIT, saved.getType());
        assertEquals("SET_CATEGORY_LIMIT:FOOD:2026-06", saved.getDedupKey());

        SuggestionPayload payload = objectMapper.readValue(saved.getPayloadJson(), SuggestionPayload.class);
        SuggestionPayload.ProposedAction action = payload.getProposedAction();
        assertEquals(Category.FOOD, action.getCategory());
        assertEquals(6, action.getMonth());
        assertEquals(2026, action.getYear());
        assertNull(action.getBudgetId());
        // trailing-3 median [180,160,140] = 160
        assertEquals(0, new BigDecimal("160").compareTo(action.getResolvedValue()));

        verify(createNotificationUseCase).createNotification(eq(userId),
                eq("notification.suggestion.set_category_limit|FOOD"), startsWith("app://suggestions/"));
    }

    // ---- Wave 1: REDUCE_BUDGET (budget consistently underspent) --------------------------

    /** FOOD budget with a low, healthy spend: limit 1000, spent 100 (projected 200). */
    private Budget underspentBudget() {
        return Budget.builder().id(budgetId).userId(userId).category(Category.FOOD)
                .amountLimit(new BigDecimal("1000")).spent(new BigDecimal("100")).month(6).year(2026).build();
    }

    private List<Transaction> lowFoodHistory() {
        List<Transaction> history = new java.util.ArrayList<>();
        history.add(expense(Category.FOOD, 2026, 5, "100"));
        history.add(expense(Category.FOOD, 2026, 4, "120"));
        history.add(expense(Category.FOOD, 2026, 3, "80"));
        return history;
    }

    @Test
    @DisplayName("Wave 1: a budgeted category spent well under its limit → REDUCE_BUDGET at median + headroom")
    void underspentBudgetProducesReduce() throws Exception {
        stubUser(Intervention_Level.GENTLE);
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(lowFoodHistory());
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026)))
                .thenReturn(List.of(underspentBudget()));
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        Suggestion saved = captor.getValue();
        assertEquals(SuggestionType.REDUCE_BUDGET, saved.getType());
        assertEquals("REDUCE_BUDGET:FOOD:2026-06", saved.getDedupKey());

        SuggestionPayload payload = objectMapper.readValue(saved.getPayloadJson(), SuggestionPayload.class);
        assertEquals(budgetId, payload.getProposedAction().getBudgetId());
        // median actual 100 × 1.15 headroom = 115.00
        assertEquals(0, new BigDecimal("115.00").compareTo(payload.getProposedAction().getResolvedValue()));

        verify(createNotificationUseCase).createNotification(eq(userId),
                eq("notification.suggestion.reduce_budget|FOOD"), startsWith("app://suggestions/"));
    }

    // ---- Wave 1: notification-only per-transaction anomalies -----------------------------

    private List<Transaction> typicalShoppingHistory() {
        List<Transaction> history = new java.util.ArrayList<>();
        history.add(expense(Category.SHOPPING, 2026, 5, "100"));
        history.add(expense(Category.SHOPPING, 2026, 4, "110"));
        history.add(expense(Category.SHOPPING, 2026, 3, "90"));
        history.add(expense(Category.SHOPPING, 2026, 2, "100"));
        return history;
    }

    @Test
    @DisplayName("Wave 1: a large expense fires a notification-only LARGE_TRANSACTION ping (no suggestion)")
    void largeTransactionNotifies() {
        stubUser(Intervention_Level.GENTLE);
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(typicalShoppingHistory());

        producer.maybeNotifyTransactionAnomalies(userId, expense(Category.SHOPPING, 2026, 6, "1000"));

        verify(createNotificationUseCase).createNotification(eq(userId),
                eq("notification.insight.large_transaction|SHOPPING"), eq("app://transactions"));
        verify(suggestionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Wave 1: a NOTIFY (pull-only) user gets no per-transaction anomaly ping")
    void notifyUserGetsNoAnomalyPing() {
        stubUser(Intervention_Level.NOTIFY);

        producer.maybeNotifyTransactionAnomalies(userId, expense(Category.SHOPPING, 2026, 6, "1000"));

        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
        verify(suggestionRepository, never()).save(any());
    }

    // ---- Wave 2: REBALANCE_BUDGETS (budget write pushes committed total over income) ------

    private Budget monthBudget(UUID id, Category category, String limit) {
        return Budget.builder().id(id).userId(userId).category(category)
                .amountLimit(new BigDecimal(limit)).spent(BigDecimal.ZERO).month(6).year(2026).build();
    }

    @Test
    @DisplayName("Wave 2: committed budgets over declared income → REBALANCE_BUDGETS with a proportional per-budget snapshot")
    void overCommittedProducesRebalance() throws Exception {
        UUID foodId = UUID.randomUUID();
        UUID shoppingId = UUID.randomUUID();
        stubUserWithIncome(Intervention_Level.GENTLE, "1000"); // committed 1500 > income 1000
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026)))
                .thenReturn(List.of(monthBudget(foodId, Category.FOOD, "900"),
                        monthBudget(shoppingId, Category.SHOPPING, "600")));
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduceRebalance(userId, 6, 2026);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        Suggestion saved = captor.getValue();
        assertEquals(SuggestionType.REBALANCE_BUDGETS, saved.getType());
        assertEquals("REBALANCE_BUDGETS:2026-06", saved.getDedupKey());

        SuggestionPayload payload = objectMapper.readValue(saved.getPayloadJson(), SuggestionPayload.class);
        SuggestionPayload.ProposedAction action = payload.getProposedAction();
        assertEquals(6, action.getMonth());
        assertEquals(2026, action.getYear());
        // factor = 1000/1500 = 0.666666 (rounded DOWN): 900 → 599.99, 600 → 399.99; total 999.98 ≤ income.
        assertEquals(2, action.getBudgetAdjustments().size());
        SuggestionPayload.BudgetAdjustment food = action.getBudgetAdjustments().stream()
                .filter(a -> foodId.equals(a.getBudgetId())).findFirst().orElseThrow();
        assertEquals(Category.FOOD, food.getCategory());
        assertEquals(0, new BigDecimal("900").compareTo(food.getCurrentLimit()));
        assertEquals(0, new BigDecimal("599.99").compareTo(food.getNewLimit()));
        SuggestionPayload.BudgetAdjustment shopping = action.getBudgetAdjustments().stream()
                .filter(a -> shoppingId.equals(a.getBudgetId())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("399.99").compareTo(shopping.getNewLimit()));
        // resolvedValue = the new committed total, for display.
        assertEquals(0, new BigDecimal("999.98").compareTo(action.getResolvedValue()));

        verify(createNotificationUseCase).createNotification(eq(userId),
                eq("notification.suggestion.rebalance_budgets"), startsWith("app://suggestions/"));
    }

    @Test
    @DisplayName("Wave 2: committed budgets within income → no rebalance ask")
    void withinIncomeNoRebalance() {
        stubUserWithIncome(Intervention_Level.GENTLE, "2000"); // committed 1500 ≤ income 2000
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026)))
                .thenReturn(List.of(monthBudget(UUID.randomUUID(), Category.FOOD, "900"),
                        monthBudget(UUID.randomUUID(), Category.SHOPPING, "600")));

        producer.maybeProduceRebalance(userId, 6, 2026);

        verify(suggestionRepository, never()).save(any());
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    @Test
    @DisplayName("Wave 2: a HARD (CRITICAL-only) user doesn't get the WARN-level rebalance ask")
    void hardUserNoRebalance() {
        stubUserWithIncome(Intervention_Level.HARD, "1000");
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026)))
                .thenReturn(List.of(monthBudget(UUID.randomUUID(), Category.FOOD, "1500")));

        producer.maybeProduceRebalance(userId, 6, 2026);

        verify(suggestionRepository, never()).save(any());
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    @Test
    @DisplayName("Wave 2: a NOTIFY (pull-only) user gets no rebalance ask")
    void notifyUserNoRebalance() {
        stubUser(Intervention_Level.NOTIFY);

        producer.maybeProduceRebalance(userId, 6, 2026);

        verify(suggestionRepository, never()).save(any());
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    // ---- Wave 2: REVIEW_SUBSCRIPTION (price hike on the expense-create path) --------------

    /** A stable "Netflix Premium" charge (200 × Mar–May) that posts at 260 in June: a +30% hike. */
    private List<Transaction> hikedSubscriptionHistory() {
        List<Transaction> history = new java.util.ArrayList<>();
        for (int m = 3; m <= 5; m++) {
            history.add(describedExpense(Category.ENTERTAINMENT, m, "200", "Netflix Premium"));
        }
        history.add(describedExpense(Category.ENTERTAINMENT, 6, "260", "Netflix Premium"));
        return history;
    }

    private Transaction describedExpense(Category category, int month, String amount, String description) {
        return Transaction.create(userId, new Amount(new BigDecimal(amount)), TransactionType.EXPENSE,
                category, description, java.time.LocalDateTime.of(2026, month, 10, 12, 0));
    }

    @Test
    @DisplayName("Wave 2: a hiked subscription charge → acknowledge-only REVIEW_SUBSCRIPTION (no proposed action)")
    void priceHikeProducesReviewSubscription() throws Exception {
        stubUser(Intervention_Level.GENTLE);
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(hikedSubscriptionHistory());
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026)))
                .thenReturn(List.of()); // no ENTERTAINMENT budget; no anomaly/trend fires on this series
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduce(userId, Category.ENTERTAINMENT, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        Suggestion saved = captor.getValue();
        assertEquals(SuggestionType.REVIEW_SUBSCRIPTION, saved.getType());
        // The subscription's description is part of the topic, slugged for key-safety.
        assertEquals("REVIEW_SUBSCRIPTION:ENTERTAINMENT:netflix-premium:2026-06", saved.getDedupKey());

        SuggestionPayload payload = objectMapper.readValue(saved.getPayloadJson(), SuggestionPayload.class);
        assertNull(payload.getProposedAction()); // acknowledge-only: nothing to execute
        assertEquals("SUBSCRIPTION_PRICE_HIKE", payload.getInsightSnapshot().get("type"));

        verify(createNotificationUseCase).createNotification(eq(userId),
                eq("notification.suggestion.review_subscription|netflix premium"),
                startsWith("app://suggestions/"));
    }
}
