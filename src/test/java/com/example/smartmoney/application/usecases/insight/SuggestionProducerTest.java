package com.example.smartmoney.application.usecases.insight;

import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.application.usecases.settlement.SettlementProperties;
import com.example.smartmoney.application.usecases.suggestion.SuggestionPayload;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.insight.Insight;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectContribution;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.ContributionId;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.suggestion.Suggestion;
import com.example.smartmoney.domain.models.suggestion.SuggestionTopicStat;
import com.example.smartmoney.domain.models.suggestion.SuggestionType;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
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
import java.util.Map;
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
        producer = buildProducer(false); // settlement off by default (matches yaml)
    }

    private SuggestionProducer buildProducer(boolean settlementEnabled) {
        return new SuggestionProducer(new SpendingInsightAnalyzer(), transactionRepository,
                budgetRepository, userRepository, projectRepository, suggestionRepository,
                topicStatRepository, createNotificationUseCase, objectMapper, fatigue, clock, narrator,
                new SettlementProperties(settlementEnabled));
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
    @DisplayName("Narration on: the LLM sentence is frozen onto the card payload AND reused as the push body")
    void narrationRidesOnPayloadAndPush() throws Exception {
        stubUser(Intervention_Level.GENTLE);
        stubAnalysisData();
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));
        String sentence = "Your food budget is on track to overspend this month.";
        when(narrator.narrate(any(Insight.class))).thenReturn(sentence);

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        SuggestionPayload payload = objectMapper.readValue(captor.getValue().getPayloadJson(), SuggestionPayload.class);
        // Frozen onto the persisted card so GET /suggestions can render it without another LLM call...
        assertEquals(sentence, payload.getNarrative());
        // ...and the very same sentence is the push body (not the i18n key) — the two never diverge.
        verify(createNotificationUseCase).createNotification(eq(userId), eq(sentence),
                startsWith("app://suggestions/"));
        // Narrated exactly once for both surfaces.
        verify(narrator, times(1)).narrate(any(Insight.class));
    }

    @Test
    @DisplayName("Narration off (narrator returns null): payload.narrative is null and the push falls back to the i18n code")
    void narrationOffLeavesNullAndFallsBackToCode() throws Exception {
        stubUser(Intervention_Level.GENTLE);
        stubAnalysisData();
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));
        // narrator (mock) returns null by default → the disabled/unavailable narration path.

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        SuggestionPayload payload = objectMapper.readValue(captor.getValue().getPayloadJson(), SuggestionPayload.class);
        assertNull(payload.getNarrative());
        verify(createNotificationUseCase).createNotification(eq(userId),
                eq("notification.suggestion.raise_budget|FOOD"), startsWith("app://suggestions/"));
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
        assertEquals(0, new BigDecimal("1500.00").compareTo(contributionAmountFor(Saving_Pace.AGGRESSIVE)));
    }

    @Test
    @DisplayName("P2-E: the suggested contribution never exceeds what the project still needs")
    void contributionCappedAtProjectRemaining() throws Exception {
        UUID projectId = UUID.randomUUID();
        stubUser(Intervention_Level.GENTLE, Saving_Pace.AGGRESSIVE); // 0.30 × 5000 = 1500
        stubSavingsData(projectId, "400"); // project only needs 400
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduceSavingsContribution(userId, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        SuggestionPayload payload = objectMapper.readValue(captor.getValue().getPayloadJson(), SuggestionPayload.class);
        assertEquals(0, new BigDecimal("400.00").compareTo(payload.getProposedAction().getResolvedValue()));
    }

    @Test
    @DisplayName("Wave 4 item 10: no active saving project → a navigate-only CREATE_PROJECT suggestion (not a contribution ask)")
    void noProjectNudgesCreateProject() throws Exception {
        stubUser(Intervention_Level.GENTLE, Saving_Pace.BALANCED);
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(incomeHistory());
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026))).thenReturn(List.of());
        when(projectRepository.findAllByUser(any())).thenReturn(List.of());
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduceSavingsContribution(userId, AS_OF);

        // Now a persisted card, not a bare notification: it lives in GET /suggestions and inherits
        // dedup/cooldown/dismissal-backoff. Accept is inert (navigate-only) — the FE reads the seed.
        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        Suggestion saved = captor.getValue();
        assertEquals(SuggestionType.CREATE_PROJECT, saved.getType());
        assertEquals("CREATE_PROJECT:2026-06", saved.getDedupKey());

        // The pre-fill draft rides in the payload: monthly = 5000 × BALANCED 0.20 = 1000 (no income cap,
        // income unset); target = monthly × 6-month horizon = 6000; deadline = 6 months past AS_OF.
        SuggestionPayload payload = objectMapper.readValue(saved.getPayloadJson(), SuggestionPayload.class);
        SuggestionPayload.ProposedAction action = payload.getProposedAction();
        assertEquals(0, new BigDecimal("6000.00").compareTo(action.getResolvedValue()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(action.getMonthlySaving()));
        assertEquals(LocalDate.of(2026, 12, 15), action.getDeadline());

        // Pushed like any suggestion: the notification deep-links to the in-app card.
        verify(createNotificationUseCase).createNotification(eq(userId), any(), startsWith("app://suggestions/"));
    }

    @Test
    @DisplayName("Wave 4 item 10: the seeded monthly is clamped to 20% of income so the derived project stays inside the obligation cap")
    void createProjectSeedClampedToIncomeCap() throws Exception {
        // AGGRESIVE would seed 0.30 × 5000 disposable = 1500/mo, but 20% of income (5000) caps it at 1000/mo.
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder()
                .id(userId).interventionLevel(Intervention_Level.GENTLE)
                .savingPace(Saving_Pace.AGGRESSIVE).income(new BigDecimal("5000")).build()));
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(incomeHistory());
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026))).thenReturn(List.of());
        when(projectRepository.findAllByUser(any())).thenReturn(List.of());
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduceSavingsContribution(userId, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        SuggestionPayload.ProposedAction action = objectMapper
                .readValue(captor.getValue().getPayloadJson(), SuggestionPayload.class).getProposedAction();
        assertEquals(0, new BigDecimal("1000.00").compareTo(action.getMonthlySaving())); // 1500 clamped to 1000
        assertEquals(0, new BigDecimal("6000.00").compareTo(action.getResolvedValue())); // 1000 × 6-month horizon
    }

    @Test
    @DisplayName("Wave 4 item 10: an already-open CREATE_PROJECT ask is not duplicated (dedup)")
    void createProjectNudgeDedup() {
        stubUser(Intervention_Level.GENTLE, Saving_Pace.BALANCED);
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(incomeHistory());
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026))).thenReturn(List.of());
        when(projectRepository.findAllByUser(any())).thenReturn(List.of());
        when(suggestionRepository.findOpenByUserIdAndDedupKey(userId, "CREATE_PROJECT:2026-06"))
                .thenReturn(Optional.of(mock(Suggestion.class)));

        producer.maybeProduceSavingsContribution(userId, AS_OF);

        verify(suggestionRepository, never()).save(any());
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    // ---- item 1: gate contribute-nudges on autoInvestSurplus × settlement-enabled --------

    @Test
    @DisplayName("Item 1: settlement live + autoInvestSurplus on → CONTRIBUTE_TO_PROJECT nudge suppressed")
    void settlementAutoInvestSuppressesContributionNudge() {
        producer = buildProducer(true);                     // settlement engine live
        stubUser(Intervention_Level.GENTLE, Saving_Pace.BALANCED); // autoInvestSurplus defaults on
        producer.maybeProduceSavingsContribution(userId, AS_OF);
        verify(suggestionRepository, never()).save(any());
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    @Test
    @DisplayName("Item 1 gap: settlement live + autoInvestSurplus on but NO project → CREATE_PROJECT suggestion still fires (auto-invest has nothing to fund)")
    void settlementAutoInvestStillNudgesCreateProjectWhenNoProject() {
        producer = buildProducer(true);                            // settlement engine live
        stubUser(Intervention_Level.GENTLE, Saving_Pace.BALANCED); // autoInvestSurplus defaults on
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(incomeHistory());
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026))).thenReturn(List.of());
        when(projectRepository.findAllByUser(any())).thenReturn(List.of());  // no project to invest into
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduceSavingsContribution(userId, AS_OF);

        // The contribute-suppression must NOT reach here: with no project, settlement's auto-invest is
        // a no-op, so the create-project ask is the only path that puts the surplus to work.
        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        assertEquals(SuggestionType.CREATE_PROJECT, captor.getValue().getType());
    }

    @Test
    @DisplayName("Item 1: settlement live but autoInvestSurplus OFF → contribute nudge still fires")
    void settlementButAutoInvestOffKeepsContributionNudge() {
        producer = buildProducer(true);
        stubUser(Intervention_Level.GENTLE, Saving_Pace.BALANCED, false); // opted out of auto-invest
        stubSavingsData(UUID.randomUUID(), "10000");
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduceSavingsContribution(userId, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        assertEquals(SuggestionType.CONTRIBUTE_TO_PROJECT, captor.getValue().getType());
    }

    @Test
    @DisplayName("Item 1: settlement OFF (default) → contribute nudge fires even with autoInvestSurplus on")
    void settlementOffKeepsContributionNudge() {
        // producer from setUp has settlement disabled; autoInvestSurplus defaults on. The nudge must
        // survive: with settlement off it is the ONLY funding path (autoInvestSurplus defaults true).
        stubUser(Intervention_Level.GENTLE, Saving_Pace.BALANCED);
        stubSavingsData(UUID.randomUUID(), "10000");
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduceSavingsContribution(userId, AS_OF);

        verify(suggestionRepository).save(any(Suggestion.class));
    }

    @Test
    @DisplayName("Item 1: settlement live + autoInvestSurplus on → INCREASE_CONTRIBUTION (project-health) suppressed")
    void settlementAutoInvestSuppressesProjectHealthNudge() {
        producer = buildProducer(true);
        stubUser(Intervention_Level.HARD, Saving_Pace.BALANCED); // autoInvestSurplus defaults on
        producer.maybeProduceProjectHealth(userId, AS_OF);
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

    private void stubUser(Intervention_Level level, Saving_Pace pace, boolean autoInvestSurplus) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder()
                .id(userId).interventionLevel(level).savingPace(pace)
                .autoInvestSurplus(autoInvestSurplus).build()));
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
    @DisplayName("Wave 1: CATEGORY_TREND on a budgetless category → SET_CATEGORY_LIMIT floored at current-month spend (never born exceeded)")
    void trendWithoutBudgetFloorsLimitAtCurrentSpend() throws Exception {
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
        // The trend fires because June (190) is already above the trailing-3 median [180,160,140]=160.
        // The proposed limit must be floored at what's already spent this month (190), not the lower
        // median — otherwise CreateBudget would backfill 190 onto a 160 limit and the budget would be
        // born EXCEEDED (remaining -30). Floored → remaining = 190 - 190 = 0, never negative.
        assertEquals(0, new BigDecimal("190").compareTo(action.getResolvedValue()));

        verify(createNotificationUseCase).createNotification(eq(userId),
                eq("notification.suggestion.set_category_limit|FOOD"), startsWith("app://suggestions/"));
    }

    /** FOOD rising Jan–Jun but with a modest June (210) that sits below the trailing median (250). */
    private List<Transaction> risingFoodTrendBelowMedianHistory() {
        List<Transaction> history = new java.util.ArrayList<>();
        int[] amounts = {100, 150, 200, 250, 300, 210};
        for (int i = 0; i < amounts.length; i++) {
            history.add(expense(Category.FOOD, 2026, i + 1, String.valueOf(amounts[i])));
        }
        return history;
    }

    @Test
    @DisplayName("Wave 1: SET_CATEGORY_LIMIT keeps the trailing median when the current-month spend is below it (floor only raises)")
    void trendWithoutBudgetKeepsMedianWhenBelowIt() throws Exception {
        stubUser(Intervention_Level.GENTLE);
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(risingFoodTrendBelowMedianHistory());
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

        SuggestionPayload payload = objectMapper.readValue(saved.getPayloadJson(), SuggestionPayload.class);
        // trailing-3 median [200,250,300]=250 wins; June's 210 spend is below it so the floor is inert.
        assertEquals(0, new BigDecimal("250").compareTo(payload.getProposedAction().getResolvedValue()));
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

    // ---- UC1: REALLOCATE_BUDGET (cover an overspend from underspent budgets) --------------

    /** SHOPPING budget spent well under its limit, with a positive trailing median → a valid donor. */
    private Budget underspentShoppingBudget(UUID id, String limit, String spent) {
        return Budget.builder().id(id).userId(userId).category(Category.SHOPPING)
                .amountLimit(new BigDecimal(limit)).spent(new BigDecimal(spent)).month(6).year(2026).build();
    }

    /** SHOPPING history at a steady, low monthly total so BUDGET_UNDERSPEND fires (positive median). */
    private List<Transaction> steadyShoppingHistory(String monthly) {
        List<Transaction> history = new java.util.ArrayList<>();
        history.add(expense(Category.SHOPPING, 2026, 5, monthly));
        history.add(expense(Category.SHOPPING, 2026, 4, monthly));
        history.add(expense(Category.SHOPPING, 2026, 3, monthly));
        return history;
    }

    @Test
    @DisplayName("UC1: an overshoot fully coverable by an underspent budget → REALLOCATE_BUDGET (zero-sum, donor floored above spend)")
    void overshootWithHeadroomReallocates() throws Exception {
        UUID shoppingBudgetId = UUID.randomUUID();
        stubUser(Intervention_Level.GENTLE);
        // FOOD projects to 1200 vs its 1000 limit (gap 200); SHOPPING sits far under its 1000 limit.
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(steadyShoppingHistory("150"));
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026)))
                .thenReturn(List.of(warnPaceBudget(), underspentShoppingBudget(shoppingBudgetId, "1000", "50")));
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        Suggestion saved = captor.getValue();
        assertEquals(SuggestionType.REALLOCATE_BUDGET, saved.getType());
        assertEquals("REALLOCATE_BUDGET:FOOD:2026-06", saved.getDedupKey());

        SuggestionPayload payload = objectMapper.readValue(saved.getPayloadJson(), SuggestionPayload.class);
        SuggestionPayload.ProposedAction action = payload.getProposedAction();
        assertEquals(0, new BigDecimal("200").compareTo(action.getResolvedValue())); // amount moved

        List<SuggestionPayload.BudgetAdjustment> adj = action.getBudgetAdjustments();
        SuggestionPayload.BudgetAdjustment food = adj.stream()
                .filter(a -> a.getBudgetId().equals(budgetId)).findFirst().orElseThrow();
        SuggestionPayload.BudgetAdjustment shop = adj.stream()
                .filter(a -> a.getBudgetId().equals(shoppingBudgetId)).findFirst().orElseThrow();
        // Recipient up to 1200, donor down to 800 — and the donor's new limit stays above its 50 spent.
        assertEquals(0, new BigDecimal("1200").compareTo(food.getNewLimit()));
        assertEquals(0, new BigDecimal("800").compareTo(shop.getNewLimit()));
        assertTrue(shop.getNewLimit().compareTo(new BigDecimal("50")) >= 0);
        // Zero-sum: the deltas across all adjustments cancel out.
        BigDecimal netDelta = adj.stream()
                .map(a -> a.getNewLimit().subtract(a.getCurrentLimit()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, BigDecimal.ZERO.compareTo(netDelta));

        verify(createNotificationUseCase).createNotification(eq(userId),
                eq("notification.suggestion.reallocate_budget|FOOD"), startsWith("app://suggestions/"));
    }

    @Test
    @DisplayName("UC1: an overshoot the underspent budgets can't fully cover → falls back to RAISE_BUDGET")
    void overshootWithoutEnoughHeadroomFallsBackToRaise() throws Exception {
        UUID shoppingBudgetId = UUID.randomUUID();
        stubUser(Intervention_Level.GENTLE);
        // FOOD gap is 200 but the only underspent budget can spare at most ~90 → reallocation isn't offered.
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(steadyShoppingHistory("30"));
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026)))
                .thenReturn(List.of(warnPaceBudget(), underspentShoppingBudget(shoppingBudgetId, "100", "5")));
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduce(userId, Category.FOOD, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        Suggestion saved = captor.getValue();
        assertEquals(SuggestionType.RAISE_BUDGET, saved.getType());
        assertEquals("RAISE_BUDGET:FOOD:2026-06", saved.getDedupKey());
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

    // ---- Wave 4: PROJECT_OFF_TRACK / INCREASE_CONTRIBUTION ---------------------------------

    /** A rehydrated project with explicit createdAt/deadline/contributions — fully clock-independent. */
    private Project savingProject(UUID projectId, String target, LocalDate deadline, Instant createdAt,
                                  List<ProjectContribution> contributions) {
        return Project.rehydrate(new ProjectId(projectId), new UserId(userId), ProjectType.PERSONAL,
                ProjectStatus.ACTIVE, ProjectPriority.MEDIUM, "Trip", null,
                new Money(new BigDecimal(target), "VND"), deadline, null, contributions, null, createdAt);
    }

    private ProjectContribution contribution(String amount, String instant) {
        return new ProjectContribution(ContributionId.newId(), new UserId(userId),
                new Money(new BigDecimal(amount), "VND"), null, Instant.parse(instant));
    }

    @Test
    @DisplayName("Wave 4: a project behind its required pace → INCREASE_CONTRIBUTION sized to the catch-up rate")
    void behindPaceProducesIncreaseContribution() throws Exception {
        stubUser(Intervention_Level.GENTLE);
        UUID projectId = UUID.randomUUID();
        // Target 10000, deadline Sep 30 → 4 saving months left (Jun–Sep). Contributed 1000/month
        // Mar–May → observed 1000/mo; remaining 7000 needs 1750/mo → behind, but < 2× → WARN.
        Project project = savingProject(projectId, "10000", LocalDate.of(2026, 9, 30),
                Instant.parse("2026-03-01T00:00:00Z"),
                List.of(contribution("1000", "2026-03-10T00:00:00Z"),
                        contribution("1000", "2026-04-10T00:00:00Z"),
                        contribution("1000", "2026-05-10T00:00:00Z")));
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(project));
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduceProjectHealth(userId, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        Suggestion saved = captor.getValue();
        assertEquals(SuggestionType.INCREASE_CONTRIBUTION, saved.getType());
        assertEquals("INCREASE_CONTRIBUTION:" + projectId + ":2026-06", saved.getDedupKey());

        SuggestionPayload payload = objectMapper.readValue(saved.getPayloadJson(), SuggestionPayload.class);
        assertEquals(projectId, payload.getProposedAction().getProjectId());
        // requiredMonthly = remaining 7000 ÷ 4 months = 1750 (well under the 7000 remaining cap).
        assertEquals(0, new BigDecimal("1750.00").compareTo(payload.getProposedAction().getResolvedValue()));
        assertEquals("PROJECT_OFF_TRACK", payload.getInsightSnapshot().get("type"));
        assertEquals("WARN", payload.getInsightSnapshot().get("severity"));

        verify(createNotificationUseCase).createNotification(eq(userId),
                eq("notification.suggestion.increase_contribution|Trip"), startsWith("app://suggestions/"));
    }

    /**
     * On-pace-by-heuristic project (2000/mo observed, 1000/mo required) but carrying settlement
     * arrears of {@code moneyOwed} and {@code frozenMonths}. Target 10000, deadline 2026-09-30,
     * created 2026-03-01 — same shape as {@link #onPaceProducesNothing()}, so any suggestion it
     * yields is driven purely by the settlement signal.
     */
    private Project onPaceProjectWithArrears(UUID projectId, String moneyOwed, int frozenMonths) {
        ProjectTracking tracking = new ProjectTracking(UUID.randomUUID(), new ProjectId(projectId),
                new BigDecimal("6000"), 4, new BigDecimal("1000"), 4,
                new BigDecimal(moneyOwed), frozenMonths, Instant.now(), Instant.now());
        return Project.rehydrate(new ProjectId(projectId), new UserId(userId), ProjectType.PERSONAL,
                ProjectStatus.ACTIVE, ProjectPriority.MEDIUM, "Trip", null,
                new Money(new BigDecimal("10000"), "VND"), LocalDate.of(2026, 9, 30), null,
                List.of(contribution("2000", "2026-03-10T00:00:00Z"),
                        contribution("2000", "2026-04-10T00:00:00Z"),
                        contribution("2000", "2026-05-10T00:00:00Z")),
                tracking, Instant.parse("2026-03-01T00:00:00Z"));
    }

    @Test
    @DisplayName("Item 3: settlement arrears (moneyOwed>0) flags a project off-track even when the pace heuristic says on-pace")
    void settlementArrearsOverridesOnPaceHeuristic() throws Exception {
        stubUser(Intervention_Level.GENTLE);
        UUID projectId = UUID.randomUUID();
        // Heuristic alone (see onPaceProducesNothing) would yield nothing; the 500 arrears drives it.
        Project project = onPaceProjectWithArrears(projectId, "500", 0);
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(project));
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduceProjectHealth(userId, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        Suggestion saved = captor.getValue();
        assertEquals(SuggestionType.INCREASE_CONTRIBUTION, saved.getType());
        SuggestionPayload payload = objectMapper.readValue(saved.getPayloadJson(), SuggestionPayload.class);
        assertEquals("WARN", payload.getInsightSnapshot().get("severity"));
        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) payload.getInsightSnapshot().get("metrics");
        assertEquals("settlement", metrics.get("source"));
        // catch-up is the required monthly rate (1000), well under the 4000 remaining.
        assertEquals(0, new BigDecimal("1000.00").compareTo(payload.getProposedAction().getResolvedValue()));
    }

    @Test
    @DisplayName("Item 3: deep arrears (owe ≥ 2× required) escalates to CRITICAL — even a HARD user hears it")
    void deepArrearsIsCriticalForHardUser() throws Exception {
        stubUser(Intervention_Level.HARD); // gate = CRITICAL; heuristic-on-pace would be silent
        UUID projectId = UUID.randomUUID();
        Project project = onPaceProjectWithArrears(projectId, "2500", 0); // 2500 ≥ 2×1000
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(project));
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduceProjectHealth(userId, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        SuggestionPayload payload = objectMapper.readValue(captor.getValue().getPayloadJson(), SuggestionPayload.class);
        assertEquals("CRITICAL", payload.getInsightSnapshot().get("severity"));
    }

    @Test
    @DisplayName("Finding 1: off-track judges the re-fetched aggregate's contributions, not the contribution-less findAllByUser stub")
    void offTrackUsesRefetchedContributions() {
        stubUser(Intervention_Level.GENTLE);
        UUID projectId = UUID.randomUUID();
        // findAllByUser returns projects WITHOUT contributions (as it does in production).
        Project stub = savingProject(projectId, "10000", LocalDate.of(2026, 9, 30),
                Instant.parse("2026-03-01T00:00:00Z"), List.of());
        // The re-fetched full aggregate has on-pace contributions (2000/mo vs 1000 required) → on track.
        Project full = savingProject(projectId, "10000", LocalDate.of(2026, 9, 30),
                Instant.parse("2026-03-01T00:00:00Z"),
                List.of(contribution("2000", "2026-03-10T00:00:00Z"),
                        contribution("2000", "2026-04-10T00:00:00Z"),
                        contribution("2000", "2026-05-10T00:00:00Z")));
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(stub));
        when(projectRepository.findById(new ProjectId(projectId))).thenReturn(Optional.of(full));

        producer.maybeProduceProjectHealth(userId, AS_OF);

        // Judging the contribution-less stub would read observed=0 → a bogus CRITICAL; the re-fetch
        // shows the project is on pace → nothing fires.
        verify(suggestionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Wave 4: a project on (or ahead of) the required pace produces nothing")
    void onPaceProducesNothing() {
        stubUser(Intervention_Level.GENTLE);
        // Contributed 2000/month Mar–May → remaining 4000 over 4 months needs 1000/mo ≤ observed 2000.
        Project project = savingProject(UUID.randomUUID(), "10000", LocalDate.of(2026, 9, 30),
                Instant.parse("2026-03-01T00:00:00Z"),
                List.of(contribution("2000", "2026-03-10T00:00:00Z"),
                        contribution("2000", "2026-04-10T00:00:00Z"),
                        contribution("2000", "2026-05-10T00:00:00Z")));
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(project));

        producer.maybeProduceProjectHealth(userId, AS_OF);

        verify(suggestionRepository, never()).save(any());
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    @Test
    @DisplayName("Wave 4: a stalled project (nothing contributed in the window) escalates to CRITICAL — even HARD users hear it")
    void stalledProjectIsCriticalForHardUser() throws Exception {
        stubUser(Intervention_Level.HARD);
        UUID projectId = UUID.randomUUID();
        Project project = savingProject(projectId, "10000", LocalDate.of(2026, 9, 30),
                Instant.parse("2026-03-01T00:00:00Z"), List.of()); // three closed months, zero saved
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(project));
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduceProjectHealth(userId, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        SuggestionPayload payload = objectMapper.readValue(captor.getValue().getPayloadJson(), SuggestionPayload.class);
        assertEquals("CRITICAL", payload.getInsightSnapshot().get("severity"));
        // requiredMonthly = 10000 ÷ 4 = 2500
        assertEquals(0, new BigDecimal("2500.00").compareTo(payload.getProposedAction().getResolvedValue()));
    }

    @Test
    @DisplayName("Wave 4: a project younger than one month isn't judged yet")
    void youngProjectNotJudged() {
        stubUser(Intervention_Level.GENTLE);
        Project project = savingProject(UUID.randomUUID(), "10000", LocalDate.of(2026, 9, 30),
                Instant.parse("2026-06-05T00:00:00Z"), List.of()); // created this month
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(project));

        producer.maybeProduceProjectHealth(userId, AS_OF);

        verify(suggestionRepository, never()).save(any());
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    @Test
    @DisplayName("Wave 4: a NOTIFY (pull-only) user gets no project-health pass at all")
    void notifyUserNoProjectHealth() {
        stubUser(Intervention_Level.NOTIFY);

        producer.maybeProduceProjectHealth(userId, AS_OF);

        verify(projectRepository, never()).findAllByUser(any());
        verify(suggestionRepository, never()).save(any());
    }

    // ---- item 4: OVER_COMMITTED → REBALANCE_PROJECTS -------------------------------------

    /** A just-created ACTIVE project (so the off-track heuristic doesn't judge it yet) with the given
     *  monthly saving + months-left on its tracking, target 50000, deadline 2027-01-01. */
    private Project activeSavingProject(UUID projectId, String monthlySaving, int monthLeft) {
        ProjectTracking tracking = new ProjectTracking(UUID.randomUUID(), new ProjectId(projectId),
                BigDecimal.ZERO, 1, new BigDecimal(monthlySaving), monthLeft,
                BigDecimal.ZERO, 0, Instant.now(), Instant.now());
        return Project.rehydrate(new ProjectId(projectId), new UserId(userId), ProjectType.PERSONAL,
                ProjectStatus.ACTIVE, ProjectPriority.MEDIUM, "Goal", null,
                new Money(new BigDecimal("50000"), "VND"), LocalDate.of(2027, 1, 1), null,
                List.of(), tracking, Instant.parse("2026-06-01T00:00:00Z"));
    }

    @Test
    @DisplayName("Item 4: combined monthly saving over pace capacity → one REBALANCE_PROJECTS ask with per-project extended deadlines")
    void overCommittedProducesRebalanceProjects() throws Exception {
        // income 10000 × BALANCED 0.20 = capacity 2000; two projects at 1500/mo = 3000 committed > 2000.
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder()
                .id(userId).interventionLevel(Intervention_Level.GENTLE)
                .savingPace(Saving_Pace.BALANCED).income(new BigDecimal("10000")).build()));
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(
                activeSavingProject(p1, "1500", 10), activeSavingProject(p2, "1500", 10)));
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduceProjectHealth(userId, AS_OF);

        // Exactly one portfolio-level ask (the young projects don't trip the per-project off-track pass).
        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        Suggestion saved = captor.getValue();
        assertEquals(SuggestionType.REBALANCE_PROJECTS, saved.getType());
        assertEquals("REBALANCE_PROJECTS:2026-06", saved.getDedupKey());
        SuggestionPayload payload = objectMapper.readValue(saved.getPayloadJson(), SuggestionPayload.class);
        assertEquals(2, payload.getProposedAction().getProjectAdjustments().size());
        // each project's monthly saving is scaled DOWN (via a later deadline) so the total fits capacity
        payload.getProposedAction().getProjectAdjustments().forEach(a -> {
            assertTrue(a.getNewMonthlySaving().compareTo(a.getCurrentMonthlySaving()) < 0);
            assertTrue(a.getNewDeadline().isAfter(a.getCurrentDeadline()));
        });
    }

    // ---- item 6: TRACK_ONLY softens the manual save/contribute nudges -------------------

    private void stubUserWithFocus(Intervention_Level level, Focus_Mode focus) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder()
                .id(userId).interventionLevel(level).focusMode(focus).build()));
    }

    @Test
    @DisplayName("Item 6: TRACK_ONLY suppresses the CONTRIBUTE_TO_PROJECT nudge (passive tracking)")
    void trackOnlySuppressesContributionNudge() {
        stubUserWithFocus(Intervention_Level.GENTLE, Focus_Mode.TRACK_ONLY);
        producer.maybeProduceSavingsContribution(userId, AS_OF);
        verify(suggestionRepository, never()).save(any());
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    @Test
    @DisplayName("Item 6: TRACK_ONLY still suppresses the create-project nudge (retained gate — passive tracking opts out of goal-setting)")
    void trackOnlySuppressesCreateProjectNudge() {
        stubUserWithFocus(Intervention_Level.GENTLE, Focus_Mode.TRACK_ONLY);
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(incomeHistory());
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026))).thenReturn(List.of());
        when(projectRepository.findAllByUser(any())).thenReturn(List.of());  // capacity but no project

        producer.maybeProduceSavingsContribution(userId, AS_OF);

        verify(suggestionRepository, never()).save(any());
        verify(createNotificationUseCase, never()).createNotification(any(), any(), any());
    }

    @Test
    @DisplayName("Item 6: TRACK_ONLY suppresses the per-project INCREASE_CONTRIBUTION nudge")
    void trackOnlySuppressesProjectHealthNudge() {
        stubUserWithFocus(Intervention_Level.GENTLE, Focus_Mode.TRACK_ONLY); // no income → no OVER_COMMITTED
        // A stalled project that would otherwise raise INCREASE_CONTRIBUTION.
        Project project = savingProject(UUID.randomUUID(), "10000", LocalDate.of(2026, 9, 30),
                Instant.parse("2026-03-01T00:00:00Z"), List.of());
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(project));

        producer.maybeProduceProjectHealth(userId, AS_OF);

        verify(suggestionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Item 6: TRACK_ONLY still gets OVER_COMMITTED (rebalancing down fits passive tracking)")
    void trackOnlyStillGetsOverCommitted() throws Exception {
        // TRACK_ONLY → capacity rate 0.10; income 10000 → capacity 1000; one project at 1500 > 1000.
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder()
                .id(userId).interventionLevel(Intervention_Level.GENTLE)
                .savingPace(Saving_Pace.BALANCED).focusMode(Focus_Mode.TRACK_ONLY)
                .income(new BigDecimal("10000")).build()));
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(
                activeSavingProject(UUID.randomUUID(), "1500", 10)));
        when(suggestionRepository.findOpenByUserIdAndDedupKey(eq(userId), anyString()))
                .thenReturn(Optional.empty());
        when(suggestionRepository.save(any(Suggestion.class))).thenAnswer(inv -> inv.getArgument(0));

        producer.maybeProduceProjectHealth(userId, AS_OF);

        ArgumentCaptor<Suggestion> captor = ArgumentCaptor.forClass(Suggestion.class);
        verify(suggestionRepository).save(captor.capture());
        assertEquals(SuggestionType.REBALANCE_PROJECTS, captor.getValue().getType());
    }

    @Test
    @DisplayName("Item 4: committed monthly within pace capacity → no OVER_COMMITTED ask")
    void withinCapacityNoRebalance() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder()
                .id(userId).interventionLevel(Intervention_Level.GENTLE)
                .savingPace(Saving_Pace.BALANCED).income(new BigDecimal("10000")).build())); // capacity 2000
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(
                activeSavingProject(UUID.randomUUID(), "800", 10))); // 800 ≤ 2000
        producer.maybeProduceProjectHealth(userId, AS_OF);
        verify(suggestionRepository, never()).save(any());
    }
}
