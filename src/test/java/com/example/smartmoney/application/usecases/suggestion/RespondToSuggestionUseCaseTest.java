package com.example.smartmoney.application.usecases.suggestion;

import com.example.smartmoney.application.dtos.requests.budgets.CreateBudgetRequest;
import com.example.smartmoney.application.dtos.requests.budgets.UpdateBudgetRequest;
import com.example.smartmoney.application.dtos.requests.project.AddProjectContributionRequest;
import com.example.smartmoney.application.dtos.requests.project.UpdateProjectRequest;
import com.example.smartmoney.application.usecases.budget.CreateBudgetUseCase;
import com.example.smartmoney.application.usecases.budget.UpdateBudgetUseCase;
import com.example.smartmoney.application.usecases.project.AddProjectContributionUseCase;
import com.example.smartmoney.application.usecases.project.UpdateProjectUseCase;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.models.suggestion.Suggestion;
import com.example.smartmoney.domain.models.suggestion.SuggestionStatus;
import com.example.smartmoney.domain.models.suggestion.SuggestionTopicStat;
import com.example.smartmoney.domain.models.suggestion.SuggestionType;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.SuggestionRepository;
import com.example.smartmoney.domain.repositories.SuggestionTopicStatRepository;
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
@DisplayName("RespondToSuggestionUseCase — idempotent decision + action")
class RespondToSuggestionUseCaseTest {

    @Mock private SuggestionRepository suggestionRepository;
    @Mock private SuggestionTopicStatRepository topicStatRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private UpdateBudgetUseCase updateBudgetUseCase;
    @Mock private CreateBudgetUseCase createBudgetUseCase;
    @Mock private AddProjectContributionUseCase addProjectContributionUseCase;
    @Mock private UpdateProjectUseCase updateProjectUseCase;
    @Mock private ProjectRepository projectRepository;

    private RespondToSuggestionUseCase useCase;
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    private final UUID userId = UUID.randomUUID();
    private final UUID budgetId = UUID.randomUUID();
    private Suggestion suggestion;

    @BeforeEach
    void setUp() {
        useCase = new RespondToSuggestionUseCase(
                suggestionRepository, topicStatRepository, budgetRepository, updateBudgetUseCase,
                createBudgetUseCase, addProjectContributionUseCase, updateProjectUseCase, projectRepository,
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
        // rebalanceCheck=false: running the producer inside this transaction could mark it
        // rollback-only and fail the whole accept (see resizeBudget).
        verify(updateBudgetUseCase).execute(eq(budgetId), eq(userId), captor.capture(), eq(false));
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
    @DisplayName("P2-A: a real dismissal increments the topic's dismiss counter")
    void dismissIncrementsTopicStat() {
        when(suggestionRepository.findById(suggestion.getId())).thenReturn(Optional.of(suggestion));
        when(suggestionRepository.markDecidedIfPending(eq(suggestion.getId()), eq(userId),
                eq(SuggestionStatus.DISMISSED), any())).thenReturn(1);
        when(topicStatRepository.find(eq(userId), eq(suggestion.getDedupKey())))
                .thenReturn(Optional.empty());

        useCase.execute(userId, suggestion.getId(), false);

        ArgumentCaptor<SuggestionTopicStat> captor = ArgumentCaptor.forClass(SuggestionTopicStat.class);
        verify(topicStatRepository).save(captor.capture());
        assertEquals(suggestion.getDedupKey(), captor.getValue().getDedupKey());
        assertEquals(1, captor.getValue().getDismissCount());
    }

    @Test
    @DisplayName("P2-A: a no-op respond (already decided) does not touch the topic counter")
    void noOpDoesNotIncrementTopicStat() {
        when(suggestionRepository.findById(suggestion.getId())).thenReturn(Optional.of(suggestion));
        when(suggestionRepository.markDecidedIfPending(eq(suggestion.getId()), eq(userId),
                eq(SuggestionStatus.DISMISSED), any())).thenReturn(0); // already decided

        useCase.execute(userId, suggestion.getId(), false);

        verify(topicStatRepository, never()).save(any());
    }

    @Test
    @DisplayName("P2-B: accepting a CREATE_BUDGET creates the budget at the consented limit")
    void acceptCreateBudget() {
        Suggestion s = createBudgetSuggestion(userId);
        when(suggestionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(suggestionRepository.markDecidedIfPending(eq(s.getId()), eq(userId),
                eq(SuggestionStatus.ACCEPTED), any())).thenReturn(1);

        useCase.execute(userId, s.getId(), true);

        ArgumentCaptor<CreateBudgetRequest> captor = ArgumentCaptor.forClass(CreateBudgetRequest.class);
        verify(createBudgetUseCase).execute(eq(userId), captor.capture());
        CreateBudgetRequest req = captor.getValue();
        assertEquals(Category.FOOD, req.getCategory());
        assertEquals(6, req.getMonth());
        assertEquals(2026, req.getYear());
        assertEquals(0, new BigDecimal("80").compareTo(req.getAmountLimit()));
    }

    @Test
    @DisplayName("P2-B: accepting a CONTRIBUTE_TO_PROJECT contributes the consented amount to the project")
    void acceptContributeToProject() {
        UUID projectId = UUID.randomUUID();
        Suggestion s = contributeSuggestion(userId, projectId);
        when(suggestionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(suggestionRepository.markDecidedIfPending(eq(s.getId()), eq(userId),
                eq(SuggestionStatus.ACCEPTED), any())).thenReturn(1);

        useCase.execute(userId, s.getId(), true);

        ArgumentCaptor<AddProjectContributionRequest> captor =
                ArgumentCaptor.forClass(AddProjectContributionRequest.class);
        verify(addProjectContributionUseCase).execute(
                eq(userId.toString()), eq(projectId.toString()), captor.capture());
        assertEquals(0, new BigDecimal("1000").compareTo(captor.getValue().getAmount()));
    }

    private Suggestion createBudgetSuggestion(UUID owner) {
        SuggestionPayload payload = SuggestionPayload.builder()
                .proposedAction(SuggestionPayload.ProposedAction.builder()
                        .category(Category.FOOD).month(6).year(2026)
                        .resolvedValue(new BigDecimal("80")).build())
                .build();
        return suggestionOf(owner, SuggestionType.CREATE_BUDGET, payload, "CREATE_BUDGET:FOOD:2026-06");
    }

    private Suggestion contributeSuggestion(UUID owner, UUID projectId) {
        SuggestionPayload payload = SuggestionPayload.builder()
                .proposedAction(SuggestionPayload.ProposedAction.builder()
                        .projectId(projectId).resolvedValue(new BigDecimal("1000")).build())
                .build();
        return suggestionOf(owner, SuggestionType.CONTRIBUTE_TO_PROJECT, payload,
                "CONTRIBUTE_TO_PROJECT:" + projectId + ":2026-06");
    }

    @Test
    @DisplayName("Wave 4: accepting an INCREASE_CONTRIBUTION contributes the consented catch-up amount")
    void acceptIncreaseContribution() {
        UUID projectId = UUID.randomUUID();
        SuggestionPayload payload = SuggestionPayload.builder()
                .proposedAction(SuggestionPayload.ProposedAction.builder()
                        .projectId(projectId).resolvedValue(new BigDecimal("1750.00")).build())
                .build();
        Suggestion s = suggestionOf(userId, SuggestionType.INCREASE_CONTRIBUTION, payload,
                "INCREASE_CONTRIBUTION:" + projectId + ":2026-06");
        when(suggestionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(suggestionRepository.markDecidedIfPending(eq(s.getId()), eq(userId),
                eq(SuggestionStatus.ACCEPTED), any())).thenReturn(1);

        useCase.execute(userId, s.getId(), true);

        // Same executor as CONTRIBUTE_TO_PROJECT — the consented number is what lands.
        ArgumentCaptor<AddProjectContributionRequest> captor =
                ArgumentCaptor.forClass(AddProjectContributionRequest.class);
        verify(addProjectContributionUseCase).execute(
                eq(userId.toString()), eq(projectId.toString()), captor.capture());
        assertEquals(0, new BigDecimal("1750.00").compareTo(captor.getValue().getAmount()));
    }

    @Test
    @DisplayName("Item 4: accepting a REBALANCE_PROJECTS applies each project's consented new deadline")
    void acceptRebalanceProjects() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        LocalDate d1 = LocalDate.of(2027, 6, 1);
        LocalDate d2 = LocalDate.of(2027, 12, 1);
        SuggestionPayload payload = SuggestionPayload.builder()
                .proposedAction(SuggestionPayload.ProposedAction.builder()
                        .resolvedValue(new BigDecimal("600"))
                        .projectAdjustments(List.of(
                                SuggestionPayload.ProjectAdjustment.builder()
                                        .projectId(p1).newDeadline(d1).build(),
                                SuggestionPayload.ProjectAdjustment.builder()
                                        .projectId(p2).newDeadline(d2).build()))
                        .build())
                .build();
        Suggestion s = suggestionOf(userId, SuggestionType.REBALANCE_PROJECTS, payload, "REBALANCE_PROJECTS:2026-06");
        when(suggestionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(suggestionRepository.markDecidedIfPending(eq(s.getId()), eq(userId),
                eq(SuggestionStatus.ACCEPTED), any())).thenReturn(1);
        when(projectRepository.findById(any())).thenReturn(Optional.of(mock(Project.class))); // both still exist

        useCase.execute(userId, s.getId(), true);

        // Each consented new deadline is applied verbatim via UpdateProjectUseCase (which recalibrates
        // that project's monthly saving down).
        ArgumentCaptor<UpdateProjectRequest> captor = ArgumentCaptor.forClass(UpdateProjectRequest.class);
        verify(updateProjectUseCase).execute(eq(userId.toString()), eq(p1.toString()), captor.capture());
        verify(updateProjectUseCase).execute(eq(userId.toString()), eq(p2.toString()), any());
        assertEquals(d1, captor.getValue().getDeadline());
    }

    @Test
    @DisplayName("Item 4: a project deleted since the ask is skipped, not fatal — the rest still rebalance")
    void rebalanceProjectsSkipsDeletedProject() {
        UUID alive = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        SuggestionPayload payload = SuggestionPayload.builder()
                .proposedAction(SuggestionPayload.ProposedAction.builder()
                        .projectAdjustments(List.of(
                                SuggestionPayload.ProjectAdjustment.builder()
                                        .projectId(alive).newDeadline(LocalDate.of(2027, 6, 1)).build(),
                                SuggestionPayload.ProjectAdjustment.builder()
                                        .projectId(deleted).newDeadline(LocalDate.of(2027, 12, 1)).build()))
                        .build())
                .build();
        Suggestion s = suggestionOf(userId, SuggestionType.REBALANCE_PROJECTS, payload, "REBALANCE_PROJECTS:2026-06");
        when(suggestionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(suggestionRepository.markDecidedIfPending(eq(s.getId()), eq(userId),
                eq(SuggestionStatus.ACCEPTED), any())).thenReturn(1);
        when(projectRepository.findById(new ProjectId(alive))).thenReturn(Optional.of(mock(Project.class)));
        when(projectRepository.findById(new ProjectId(deleted))).thenReturn(Optional.empty()); // gone since ask

        useCase.execute(userId, s.getId(), true);

        verify(updateProjectUseCase).execute(eq(userId.toString()), eq(alive.toString()), any());
        verify(updateProjectUseCase, never()).execute(eq(userId.toString()), eq(deleted.toString()), any());
    }

    @Test
    @DisplayName("Wave 1: accepting a SET_CATEGORY_LIMIT creates the budget at the consented limit")
    void acceptSetCategoryLimit() {
        Suggestion s = setCategoryLimitSuggestion(userId);
        when(suggestionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(suggestionRepository.markDecidedIfPending(eq(s.getId()), eq(userId),
                eq(SuggestionStatus.ACCEPTED), any())).thenReturn(1);

        useCase.execute(userId, s.getId(), true);

        ArgumentCaptor<CreateBudgetRequest> captor = ArgumentCaptor.forClass(CreateBudgetRequest.class);
        verify(createBudgetUseCase).execute(eq(userId), captor.capture());
        CreateBudgetRequest req = captor.getValue();
        assertEquals(Category.FOOD, req.getCategory());
        assertEquals(6, req.getMonth());
        assertEquals(2026, req.getYear());
        assertEquals(0, new BigDecimal("160").compareTo(req.getAmountLimit()));
    }

    @Test
    @DisplayName("Wave 1: accepting a REDUCE_BUDGET lowers the budget to the consented value")
    void acceptReduceBudget() {
        Suggestion s = reduceBudgetSuggestion(userId);
        when(suggestionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(suggestionRepository.markDecidedIfPending(eq(s.getId()), eq(userId),
                eq(SuggestionStatus.ACCEPTED), any())).thenReturn(1);

        useCase.execute(userId, s.getId(), true);

        ArgumentCaptor<UpdateBudgetRequest> captor = ArgumentCaptor.forClass(UpdateBudgetRequest.class);
        verify(updateBudgetUseCase).execute(eq(budgetId), eq(userId), captor.capture(), eq(false));
        assertEquals(0, new BigDecimal("115").compareTo(captor.getValue().getAmountLimit()));
    }

    private Suggestion setCategoryLimitSuggestion(UUID owner) {
        SuggestionPayload payload = SuggestionPayload.builder()
                .proposedAction(SuggestionPayload.ProposedAction.builder()
                        .category(Category.FOOD).month(6).year(2026)
                        .resolvedValue(new BigDecimal("160")).build())
                .build();
        return suggestionOf(owner, SuggestionType.SET_CATEGORY_LIMIT, payload, "SET_CATEGORY_LIMIT:FOOD:2026-06");
    }

    private Suggestion reduceBudgetSuggestion(UUID owner) {
        SuggestionPayload payload = SuggestionPayload.builder()
                .proposedAction(SuggestionPayload.ProposedAction.builder()
                        .category(Category.FOOD).budgetId(budgetId)
                        .resolvedValue(new BigDecimal("115")).build())
                .build();
        return suggestionOf(owner, SuggestionType.REDUCE_BUDGET, payload, "REDUCE_BUDGET:FOOD:2026-06");
    }

    private Suggestion suggestionOf(UUID owner, SuggestionType type, SuggestionPayload payload, String dedupKey) {
        try {
            return Suggestion.create(owner, type, objectMapper.writeValueAsString(payload), dedupKey, null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---- Wave 2: REBALANCE_BUDGETS (multi-budget snapshot) + REVIEW_SUBSCRIPTION ----------

    private Suggestion rebalanceSuggestion(UUID owner, UUID foodBudgetId, UUID shoppingBudgetId) {
        SuggestionPayload payload = SuggestionPayload.builder()
                .proposedAction(SuggestionPayload.ProposedAction.builder()
                        .month(6).year(2026)
                        .resolvedValue(new BigDecimal("999.98"))
                        .budgetAdjustments(List.of(
                                SuggestionPayload.BudgetAdjustment.builder()
                                        .budgetId(foodBudgetId).category(Category.FOOD)
                                        .currentLimit(new BigDecimal("900"))
                                        .newLimit(new BigDecimal("599.99")).build(),
                                SuggestionPayload.BudgetAdjustment.builder()
                                        .budgetId(shoppingBudgetId).category(Category.SHOPPING)
                                        .currentLimit(new BigDecimal("600"))
                                        .newLimit(new BigDecimal("399.99")).build()))
                        .build())
                .build();
        return suggestionOf(owner, SuggestionType.REBALANCE_BUDGETS, payload, "REBALANCE_BUDGETS:2026-06");
    }

    @Test
    @DisplayName("Wave 2: accepting a REBALANCE_BUDGETS applies every snapshotted limit, without mid-loop rebalance re-checks")
    void acceptRebalanceAppliesSnapshot() {
        UUID foodBudgetId = UUID.randomUUID();
        UUID shoppingBudgetId = UUID.randomUUID();
        Suggestion s = rebalanceSuggestion(userId, foodBudgetId, shoppingBudgetId);
        when(suggestionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(suggestionRepository.markDecidedIfPending(eq(s.getId()), eq(userId),
                eq(SuggestionStatus.ACCEPTED), any())).thenReturn(1);
        when(budgetRepository.findById(any())).thenReturn(
                Optional.of(mock(com.example.smartmoney.domain.models.budget.Budget.class)));

        useCase.execute(userId, s.getId(), true);

        ArgumentCaptor<UpdateBudgetRequest> captor = ArgumentCaptor.forClass(UpdateBudgetRequest.class);
        verify(updateBudgetUseCase).execute(eq(foodBudgetId), eq(userId), captor.capture(), eq(false));
        assertEquals(0, new BigDecimal("599.99").compareTo(captor.getValue().getAmountLimit()));
        verify(updateBudgetUseCase).execute(eq(shoppingBudgetId), eq(userId), captor.capture(), eq(false));
        assertEquals(0, new BigDecimal("399.99").compareTo(captor.getValue().getAmountLimit()));
    }

    @Test
    @DisplayName("Wave 2: a budget deleted after the ask is skipped; the rest of the snapshot still applies")
    void rebalanceSkipsDeletedBudget() {
        UUID foodBudgetId = UUID.randomUUID();
        UUID shoppingBudgetId = UUID.randomUUID();
        Suggestion s = rebalanceSuggestion(userId, foodBudgetId, shoppingBudgetId);
        when(suggestionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(suggestionRepository.markDecidedIfPending(eq(s.getId()), eq(userId),
                eq(SuggestionStatus.ACCEPTED), any())).thenReturn(1);
        when(budgetRepository.findById(foodBudgetId)).thenReturn(Optional.empty()); // deleted since ask
        when(budgetRepository.findById(shoppingBudgetId)).thenReturn(
                Optional.of(mock(com.example.smartmoney.domain.models.budget.Budget.class)));

        useCase.execute(userId, s.getId(), true);

        verify(updateBudgetUseCase, never()).execute(eq(foodBudgetId), any(), any(), anyBoolean());
        verify(updateBudgetUseCase).execute(eq(shoppingBudgetId), eq(userId),
                any(UpdateBudgetRequest.class), eq(false));
    }

    // ---- UC1: REALLOCATE_BUDGET (same snapshot executor; born-exceeded guard) --------------

    private Budget budget(UUID id, Category category, String limit, String spent) {
        return Budget.builder().id(id).userId(userId).category(category)
                .amountLimit(new BigDecimal(limit)).spent(new BigDecimal(spent)).month(6).year(2026).build();
    }

    private Suggestion reallocateSuggestion(UUID owner, UUID foodBudgetId, UUID shoppingBudgetId) {
        SuggestionPayload payload = SuggestionPayload.builder()
                .proposedAction(SuggestionPayload.ProposedAction.builder()
                        .category(Category.FOOD).month(6).year(2026)
                        .resolvedValue(new BigDecimal("200")) // amount moved
                        .budgetAdjustments(List.of(
                                SuggestionPayload.BudgetAdjustment.builder()
                                        .budgetId(foodBudgetId).category(Category.FOOD)
                                        .currentLimit(new BigDecimal("1000"))
                                        .newLimit(new BigDecimal("1200")).build(), // recipient up
                                SuggestionPayload.BudgetAdjustment.builder()
                                        .budgetId(shoppingBudgetId).category(Category.SHOPPING)
                                        .currentLimit(new BigDecimal("1000"))
                                        .newLimit(new BigDecimal("800")).build()))  // donor down
                        .build())
                .build();
        return suggestionOf(owner, SuggestionType.REALLOCATE_BUDGET, payload, "REALLOCATE_BUDGET:FOOD:2026-06");
    }

    @Test
    @DisplayName("UC1: accepting a REALLOCATE_BUDGET applies the recipient raise and the donor cut as one snapshot")
    void acceptReallocateAppliesSnapshot() {
        UUID foodBudgetId = UUID.randomUUID();
        UUID shoppingBudgetId = UUID.randomUUID();
        Suggestion s = reallocateSuggestion(userId, foodBudgetId, shoppingBudgetId);
        when(suggestionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(suggestionRepository.markDecidedIfPending(eq(s.getId()), eq(userId),
                eq(SuggestionStatus.ACCEPTED), any())).thenReturn(1);
        when(budgetRepository.findById(foodBudgetId))
                .thenReturn(Optional.of(budget(foodBudgetId, Category.FOOD, "1000", "600")));
        when(budgetRepository.findById(shoppingBudgetId))
                .thenReturn(Optional.of(budget(shoppingBudgetId, Category.SHOPPING, "1000", "50")));

        useCase.execute(userId, s.getId(), true);

        ArgumentCaptor<UpdateBudgetRequest> captor = ArgumentCaptor.forClass(UpdateBudgetRequest.class);
        verify(updateBudgetUseCase).execute(eq(foodBudgetId), eq(userId), captor.capture(), eq(false));
        assertEquals(0, new BigDecimal("1200").compareTo(captor.getValue().getAmountLimit()));
        verify(updateBudgetUseCase).execute(eq(shoppingBudgetId), eq(userId), captor.capture(), eq(false));
        assertEquals(0, new BigDecimal("800").compareTo(captor.getValue().getAmountLimit()));
    }

    @Test
    @DisplayName("UC1: a RAISE_BUDGET carrying a snapshot applies every line — the raise AND the cuts funding it")
    void acceptHybridRaiseAppliesWholeSnapshot() {
        UUID foodBudgetId = UUID.randomUUID();
        UUID shoppingBudgetId = UUID.randomUUID();
        // A partly-funded raise: FOOD up 200, of which 70 comes out of SHOPPING.
        SuggestionPayload payload = SuggestionPayload.builder()
                .proposedAction(SuggestionPayload.ProposedAction.builder()
                        .category(Category.FOOD).budgetId(foodBudgetId).month(6).year(2026)
                        .resolvedValue(new BigDecimal("1200")) // the recipient's new limit
                        .budgetAdjustments(List.of(
                                SuggestionPayload.BudgetAdjustment.builder()
                                        .budgetId(foodBudgetId).category(Category.FOOD)
                                        .currentLimit(new BigDecimal("1000"))
                                        .newLimit(new BigDecimal("1200")).build(),
                                SuggestionPayload.BudgetAdjustment.builder()
                                        .budgetId(shoppingBudgetId).category(Category.SHOPPING)
                                        .currentLimit(new BigDecimal("100"))
                                        .newLimit(new BigDecimal("30")).build()))
                        .build())
                .build();
        Suggestion s = suggestionOf(userId, SuggestionType.RAISE_BUDGET, payload, "RAISE_BUDGET:FOOD:2026-06");
        when(suggestionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(suggestionRepository.markDecidedIfPending(eq(s.getId()), eq(userId),
                eq(SuggestionStatus.ACCEPTED), any())).thenReturn(1);
        when(budgetRepository.findById(foodBudgetId))
                .thenReturn(Optional.of(budget(foodBudgetId, Category.FOOD, "1000", "600")));
        when(budgetRepository.findById(shoppingBudgetId))
                .thenReturn(Optional.of(budget(shoppingBudgetId, Category.SHOPPING, "100", "5")));

        useCase.execute(userId, s.getId(), true);

        ArgumentCaptor<UpdateBudgetRequest> captor = ArgumentCaptor.forClass(UpdateBudgetRequest.class);
        verify(updateBudgetUseCase).execute(eq(foodBudgetId), eq(userId), captor.capture(), eq(false));
        assertEquals(0, new BigDecimal("1200").compareTo(captor.getValue().getAmountLimit()));
        verify(updateBudgetUseCase).execute(eq(shoppingBudgetId), eq(userId), captor.capture(), eq(false));
        assertEquals(0, new BigDecimal("30").compareTo(captor.getValue().getAmountLimit()));
    }

    @Test
    @DisplayName("UC1 guard: a snapshot that would cut a budget below its already-spent amount rejects the whole ask — nothing applied")
    void snapshotRejectsReductionBelowSpent() {
        UUID foodBudgetId = UUID.randomUUID();
        UUID shoppingBudgetId = UUID.randomUUID();
        // Reuse the REBALANCE snapshot (shopping 600 → 399.99); the guard protects both types.
        Suggestion s = rebalanceSuggestion(userId, foodBudgetId, shoppingBudgetId);
        when(suggestionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(suggestionRepository.markDecidedIfPending(eq(s.getId()), eq(userId),
                eq(SuggestionStatus.ACCEPTED), any())).thenReturn(1);
        when(budgetRepository.findById(foodBudgetId))
                .thenReturn(Optional.of(budget(foodBudgetId, Category.FOOD, "900", "100")));
        // Shopping has already spent 500, but the snapshot would cut its limit to 399.99 → reject all.
        when(budgetRepository.findById(shoppingBudgetId))
                .thenReturn(Optional.of(budget(shoppingBudgetId, Category.SHOPPING, "600", "500")));

        assertThrows(DomainException.class, () -> useCase.execute(userId, s.getId(), true));
        verify(updateBudgetUseCase, never()).execute(any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Wave 2: a REBALANCE_BUDGETS payload without adjustments is rejected")
    void rebalanceWithoutAdjustmentsRejected() {
        SuggestionPayload payload = SuggestionPayload.builder()
                .proposedAction(SuggestionPayload.ProposedAction.builder().month(6).year(2026).build())
                .build();
        Suggestion s = suggestionOf(userId, SuggestionType.REBALANCE_BUDGETS, payload, "REBALANCE_BUDGETS:2026-06");
        when(suggestionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(suggestionRepository.markDecidedIfPending(eq(s.getId()), eq(userId),
                eq(SuggestionStatus.ACCEPTED), any())).thenReturn(1);

        assertThrows(DomainException.class, () -> useCase.execute(userId, s.getId(), true));

        verify(updateBudgetUseCase, never()).execute(any(), any(), any(), anyBoolean());
    }

    @Test
    @DisplayName("Wave 2: accepting a REVIEW_SUBSCRIPTION is acknowledge-only — the flip is the entire effect")
    void acceptReviewSubscriptionExecutesNothing() {
        SuggestionPayload payload = SuggestionPayload.builder().build(); // no proposed action by design
        Suggestion s = suggestionOf(userId, SuggestionType.REVIEW_SUBSCRIPTION, payload,
                "REVIEW_SUBSCRIPTION:ENTERTAINMENT:netflix-premium:2026-06");
        when(suggestionRepository.findById(s.getId())).thenReturn(Optional.of(s));
        when(suggestionRepository.markDecidedIfPending(eq(s.getId()), eq(userId),
                eq(SuggestionStatus.ACCEPTED), any())).thenReturn(1);

        useCase.execute(userId, s.getId(), true);

        verify(updateBudgetUseCase, never()).execute(any(), any(), any());
        verify(updateBudgetUseCase, never()).execute(any(), any(), any(), anyBoolean());
        verify(createBudgetUseCase, never()).execute(any(), any());
        verify(addProjectContributionUseCase, never()).execute(any(), any(), any());
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
