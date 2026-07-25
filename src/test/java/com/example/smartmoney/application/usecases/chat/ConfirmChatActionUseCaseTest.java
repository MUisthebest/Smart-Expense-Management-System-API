package com.example.smartmoney.application.usecases.chat;

import com.example.smartmoney.application.dtos.chat.PendingChatAction;
import com.example.smartmoney.application.dtos.requests.budgets.UpdateBudgetRequest;
import com.example.smartmoney.application.dtos.requests.chat.ConfirmChatActionRequest;
import com.example.smartmoney.application.dtos.requests.project.AddProjectContributionRequest;
import com.example.smartmoney.application.dtos.requests.project.UpdateProjectRequest;
import com.example.smartmoney.application.dtos.responses.ChatActionConfirmResponse;
import com.example.smartmoney.application.dtos.responses.budgets.BudgetAllocationPlanResponse;
import com.example.smartmoney.application.ports.chat.ChatPendingActionPort;
import com.example.smartmoney.application.usecases.budget.ComputeBudgetAllocationUseCase;
import com.example.smartmoney.application.usecases.budget.UpdateBudgetUseCase;
import com.example.smartmoney.application.usecases.budget.UpsertBulkBudgetsUseCase;
import com.example.smartmoney.application.usecases.project.AddProjectContributionUseCase;
import com.example.smartmoney.application.usecases.project.CreateProjectUseCase;
import com.example.smartmoney.application.usecases.project.DeleteProjectUseCase;
import com.example.smartmoney.application.usecases.project.UpdateProjectTrackingUseCase;
import com.example.smartmoney.application.usecases.project.UpdateProjectUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ConfirmChatActionUseCase")
class ConfirmChatActionUseCaseTest {

    private final ChatPendingActionPort pendingActionPort = mock(ChatPendingActionPort.class);
    private final CreateProjectUseCase createProjectUseCase = mock(CreateProjectUseCase.class);
    private final UpdateProjectUseCase updateProjectUseCase = mock(UpdateProjectUseCase.class);
    private final DeleteProjectUseCase deleteProjectUseCase = mock(DeleteProjectUseCase.class);
    private final UpdateProjectTrackingUseCase updateProjectTrackingUseCase = mock(UpdateProjectTrackingUseCase.class);
    private final AddProjectContributionUseCase addProjectContributionUseCase = mock(AddProjectContributionUseCase.class);
    private final UpdateBudgetUseCase updateBudgetUseCase = mock(UpdateBudgetUseCase.class);
    private final UpsertBulkBudgetsUseCase upsertBulkBudgetsUseCase = mock(UpsertBulkBudgetsUseCase.class);
    private final ComputeBudgetAllocationUseCase computeBudgetAllocationUseCase =
            mock(ComputeBudgetAllocationUseCase.class);

    private final ConfirmChatActionUseCase useCase = new ConfirmChatActionUseCase(
            pendingActionPort, createProjectUseCase, updateProjectUseCase, deleteProjectUseCase,
            updateProjectTrackingUseCase, addProjectContributionUseCase, updateBudgetUseCase,
            upsertBulkBudgetsUseCase, computeBudgetAllocationUseCase);

    private final UUID userId = UUID.randomUUID();
    private final String actionId = "action-1";

    @BeforeEach
    void stubRecompute() {
        when(computeBudgetAllocationUseCase.execute(any())).thenReturn(BudgetAllocationPlanResponse.builder()
                .safeSpending(BigDecimal.valueOf(5_000_000))
                .envelope(BigDecimal.valueOf(3_000_000))
                .build());
    }

    @Test
    @DisplayName("confirm=true on a project deadline change executes UpdateProjectUseCase then recomputes (Step 2)")
    void confirmExecutesProjectDeadlineChange() {
        String projectId = UUID.randomUUID().toString();
        LocalDate newDeadline = LocalDate.now().plusMonths(6);
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_UPDATE_DEADLINE)
                .projectId(projectId)
                .newDeadline(newDeadline)
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        verify(updateProjectUseCase).execute(eq(userId.toString()), eq(projectId), any(UpdateProjectRequest.class));
        verify(computeBudgetAllocationUseCase).execute(userId);
        verify(pendingActionPort).delete(userId, actionId);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getSafeSpendingAfter()).isEqualTo(5_000_000L);
        assertThat(response.getEnvelopeAfter()).isEqualTo(3_000_000L);
    }

    @Test
    @DisplayName("confirm=true on a project delete skips Step 2 (recompute)")
    void confirmDeleteSkipsRecompute() {
        String projectId = UUID.randomUUID().toString();
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_DELETE)
                .projectId(projectId)
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        verify(deleteProjectUseCase).execute(userId.toString(), projectId);
        verify(computeBudgetAllocationUseCase, never()).execute(any());
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getSafeSpendingAfter()).isNull();
    }

    @Test
    @DisplayName("confirm=true on a project-tracking update calls UpdateProjectTrackingUseCase with only the set fields")
    void confirmProjectTrackingUpdateAppliesOnlySetFields() {
        String projectId = UUID.randomUUID().toString();
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_TRACKING_UPDATE)
                .projectId(projectId)
                .trackingMoneySaved(BigDecimal.valueOf(7_000_000))
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));

        useCase.execute(userId, ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.example.smartmoney.application.dtos.requests.project.UpdateProjectTrackingRequest.class);
        verify(updateProjectTrackingUseCase).execute(eq(userId.toString()), eq(projectId), captor.capture());
        assertThat(captor.getValue().getMoneySaved()).isEqualByComparingTo(BigDecimal.valueOf(7_000_000));
        assertThat(captor.getValue().getMonthLeft()).isNull();
        assertThat(captor.getValue().getMoneyOwed()).isNull();
    }

    @Test
    @DisplayName("confirm=true on a project contribution add calls AddProjectContributionUseCase — " +
            "the same real use case the app's own \"Contribute\" button uses, so the project's " +
            "visible progress (which reads Project.contributions, not ProjectTracking) actually moves")
    void confirmProjectContributionAddCallsAddProjectContributionUseCase() {
        String projectId = UUID.randomUUID().toString();
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_CONTRIBUTION_ADD)
                .projectId(projectId)
                .contributionAmount(BigDecimal.valueOf(3_000_000))
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));

        useCase.execute(userId, ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        var captor = org.mockito.ArgumentCaptor.forClass(AddProjectContributionRequest.class);
        verify(addProjectContributionUseCase).execute(eq(userId.toString()), eq(projectId), captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo(BigDecimal.valueOf(3_000_000));
        verifyNoInteractions(updateProjectTrackingUseCase);
    }

    @Test
    @DisplayName("confirm=true on a next-month budget plan calls the upsert path (create-or-update, never a hard conflict)")
    void confirmNextMonthBudgetPlanUpsertsAllCategories() {
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.APPLY_NEXT_MONTH_BUDGET_PLAN)
                .targetMonth(8)
                .targetYear(2026)
                .categoryAmounts(List.of(
                        PendingChatAction.CategoryAmount.builder()
                                .category("FOOD").amountLimit(BigDecimal.valueOf(2_500_000)).build(),
                        PendingChatAction.CategoryAmount.builder()
                                .category("ENTERTAINMENT").amountLimit(BigDecimal.valueOf(800_000)).build()))
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.example.smartmoney.application.dtos.requests.budgets.CreateBulkBudgetsRequest.class);
        verify(upsertBulkBudgetsUseCase).execute(eq(userId), captor.capture());
        assertThat(captor.getValue().getMonth()).isEqualTo(8);
        assertThat(captor.getValue().getYear()).isEqualTo(2026);
        assertThat(captor.getValue().getBudgets()).hasSize(2);
        verify(computeBudgetAllocationUseCase).execute(userId);
        assertThat(response.isSuccess()).isTrue();
    }

    @Test
    @DisplayName("confirm=true on a BATCH executes every sub-action")
    void confirmBatchExecutesEverySubAction() {
        String projectA = UUID.randomUUID().toString();
        String projectB = UUID.randomUUID().toString();
        PendingChatAction batch = PendingChatAction.builder()
                .type(PendingChatAction.Type.BATCH)
                .batchActions(List.of(
                        PendingChatAction.builder().type(PendingChatAction.Type.PROJECT_UPDATE_DEADLINE)
                                .projectId(projectA).newDeadline(LocalDate.now().plusMonths(3)).build(),
                        PendingChatAction.builder().type(PendingChatAction.Type.PROJECT_UPDATE_DEADLINE)
                                .projectId(projectB).newDeadline(LocalDate.now().plusMonths(9)).build()))
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(batch));

        useCase.execute(userId, ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        verify(updateProjectUseCase).execute(eq(userId.toString()), eq(projectA), any());
        verify(updateProjectUseCase).execute(eq(userId.toString()), eq(projectB), any());
        verify(updateProjectUseCase, times(2)).execute(eq(userId.toString()), anyString(), any());
    }

    @Test
    @DisplayName("confirm=true on a BATCH where one sub-action fails still applies the others — " +
            "does NOT abort the rest and does NOT hide that some changes succeeded")
    void confirmBatchDeletePartiallyFailsButAppliesSuccessfulDeletes() {
        String projectA = UUID.randomUUID().toString();
        String projectB = UUID.randomUUID().toString();
        PendingChatAction batch = PendingChatAction.builder()
                .type(PendingChatAction.Type.BATCH)
                .batchActions(List.of(
                        PendingChatAction.builder().type(PendingChatAction.Type.PROJECT_DELETE)
                                .projectId(projectA).name("Đi chơi").build(),
                        PendingChatAction.builder().type(PendingChatAction.Type.PROJECT_DELETE)
                                .projectId(projectB).name("Mua đất").build()))
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(batch));
        // Project A deletes fine; Project B hits some failure (constraint, already-abandoned
        // group sub-project, whatever) — the old behavior aborted the loop on the FIRST failure,
        // which could silently skip A too depending on batch order.
        doThrow(new RuntimeException("Project already abandoned"))
                .when(deleteProjectUseCase).execute(userId.toString(), projectB);

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        // Both sub-actions were attempted — B's failure didn't stop A from being deleted.
        verify(deleteProjectUseCase).execute(userId.toString(), projectA);
        verify(deleteProjectUseCase).execute(userId.toString(), projectB);
        // Reported as a partial success, not a flat failure that would hide A's deletion.
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getReply()).contains("1/2");
        assertThat(response.getReply()).contains("Mua đất");
    }

    @Test
    @DisplayName("confirm=true on a BATCH where every sub-action fails reports success=false")
    void confirmBatchAllSubActionsFailReportsFailure() {
        String projectA = UUID.randomUUID().toString();
        String projectB = UUID.randomUUID().toString();
        PendingChatAction batch = PendingChatAction.builder()
                .type(PendingChatAction.Type.BATCH)
                .batchActions(List.of(
                        PendingChatAction.builder().type(PendingChatAction.Type.PROJECT_DELETE)
                                .projectId(projectA).name("Đi chơi").build(),
                        PendingChatAction.builder().type(PendingChatAction.Type.PROJECT_DELETE)
                                .projectId(projectB).name("Mua đất").build()))
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(batch));
        doThrow(new RuntimeException("Project not found")).when(deleteProjectUseCase)
                .execute(eq(userId.toString()), anyString());

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getReply()).contains("0/2");
    }

    @Test
    @DisplayName("confirm=true on a budget-limit swap executes UpdateBudgetUseCase for every entry")
    void confirmBudgetSwapExecutesEveryEntry() {
        String budgetA = UUID.randomUUID().toString();
        String budgetB = UUID.randomUUID().toString();
        PendingChatAction swap = PendingChatAction.builder()
                .type(PendingChatAction.Type.UPDATE_BUDGET_LIMIT_SWAP)
                .batchActions(List.of(
                        PendingChatAction.builder().type(PendingChatAction.Type.UPDATE_BUDGET_LIMIT)
                                .budgetId(budgetA).newAmountLimit(BigDecimal.valueOf(4_000_000)).build(),
                        PendingChatAction.builder().type(PendingChatAction.Type.UPDATE_BUDGET_LIMIT)
                                .budgetId(budgetB).newAmountLimit(BigDecimal.valueOf(500_000)).build()))
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(swap));

        useCase.execute(userId, ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        verify(updateBudgetUseCase).execute(eq(UUID.fromString(budgetA)), eq(userId), any(UpdateBudgetRequest.class));
        verify(updateBudgetUseCase).execute(eq(UUID.fromString(budgetB)), eq(userId), any(UpdateBudgetRequest.class));
    }

    @Test
    @DisplayName("confirm=false deletes the pending action without executing anything")
    void denyDeletesWithoutExecuting() {
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_DELETE)
                .projectId(UUID.randomUUID().toString())
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(false).build());

        verify(pendingActionPort).delete(userId, actionId);
        verifyNoInteractions(deleteProjectUseCase, updateProjectUseCase, createProjectUseCase,
                updateBudgetUseCase, computeBudgetAllocationUseCase);
        assertThat(response.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("missing or expired action is a clean no-op, not an exception")
    void missingActionIsCleanNoOp() {
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.empty());

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        assertThat(response.isSuccess()).isFalse();
        verifyNoInteractions(deleteProjectUseCase, updateProjectUseCase, createProjectUseCase,
                updateBudgetUseCase, computeBudgetAllocationUseCase);
    }

    @Test
    @DisplayName("reply language follows the action's stored language, not always Vietnamese — " +
            "needed since a bare confirm/deny carries no prose of its own to detect language from")
    void replyLanguageFollowsActionsStoredLanguage() {
        String projectId = UUID.randomUUID().toString();
        PendingChatAction englishAction = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_DELETE)
                .projectId(projectId)
                .english(true)
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(englishAction));

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        assertThat(response.getReply()).isEqualTo("Project deleted successfully.");
        assertThat(response.isEnglish()).isTrue();
    }

    @Test
    @DisplayName("reply stays Vietnamese by default when the action wasn't marked English")
    void replyDefaultsToVietnamese() {
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_DELETE)
                .projectId(UUID.randomUUID().toString())
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        assertThat(response.getReply()).isEqualTo("Đã xóa dự án thành công.");
    }

    @Test
    @DisplayName("clears the \"latest pending action\" pointer when it points at the action being resolved — " +
            "so a later bare yes/no isn't resolved against an id already consumed via this call")
    void clearsLatestPointerWhenItMatchesTheResolvedAction() {
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_DELETE)
                .projectId(UUID.randomUUID().toString())
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));
        when(pendingActionPort.loadLatestId(userId)).thenReturn(Optional.of(actionId));

        useCase.execute(userId, ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        verify(pendingActionPort).clearLatestId(userId);
    }

    @Test
    @DisplayName("does NOT clear the \"latest\" pointer when it points at a different, newer action")
    void doesNotClearLatestPointerWhenItPointsElsewhere() {
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_DELETE)
                .projectId(UUID.randomUUID().toString())
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));
        when(pendingActionPort.loadLatestId(userId)).thenReturn(Optional.of("some-newer-action-id"));

        useCase.execute(userId, ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        verify(pendingActionPort, never()).clearLatestId(any());
    }
}
