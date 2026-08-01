package com.example.smartmoney.application.usecases.chat;

import com.example.smartmoney.application.dtos.chat.PendingChatAction;
import com.example.smartmoney.application.dtos.requests.chat.ConfirmChatActionRequest;
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
import com.example.smartmoney.application.usecases.transaction.CreateTransactionUseCase;
import com.example.smartmoney.application.usecases.transaction.DeleteTransactionUseCase;
import com.example.smartmoney.application.usecases.transaction.UpdateTransactionUseCase;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.application.usecases.group.CreateGroupProjectUseCase;
import com.example.smartmoney.application.usecases.group.CreateGroupUseCase;
import com.example.smartmoney.application.usecases.group.CreateSubPersonalProjectUseCase;
import com.example.smartmoney.application.usecases.group.InviteGroupMemberUseCase;
import com.example.smartmoney.application.usecases.group.LockGroupUseCase;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * §5 last-line guard tests: {@link ConfirmChatActionUseCase#execute} must never mutate a
 * project whose {@code groupProjectId != null} (a sub-personal project — a member's share of a
 * group commitment). Even if the orchestrator's strip filter failed to remove the pending
 * action, the guard here catches it. IllegalStateException is used as a signal for "blocked
 * because of the group rule" (as opposed to generic mutation failure), and the outer catch
 * surfaces the guard's bilingual message directly instead of prefixing it.
 */
@DisplayName("ConfirmChatActionUseCase §5 group-linked guard")
class ConfirmChatActionUseCaseGroupGuardTest {

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
    private final CreateTransactionUseCase createTransactionUseCase = mock(CreateTransactionUseCase.class);
    private final UpdateTransactionUseCase updateTransactionUseCase = mock(UpdateTransactionUseCase.class);
    private final DeleteTransactionUseCase deleteTransactionUseCase = mock(DeleteTransactionUseCase.class);
    private final CreateGroupUseCase createGroupUseCase = mock(CreateGroupUseCase.class);
    private final InviteGroupMemberUseCase inviteGroupMemberUseCase = mock(InviteGroupMemberUseCase.class);
    private final LockGroupUseCase lockGroupUseCase = mock(LockGroupUseCase.class);
    private final CreateGroupProjectUseCase createGroupProjectUseCase = mock(CreateGroupProjectUseCase.class);
    private final CreateSubPersonalProjectUseCase createSubPersonalProjectUseCase =
            mock(CreateSubPersonalProjectUseCase.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);

    private final ConfirmChatActionUseCase useCase = new ConfirmChatActionUseCase(
            pendingActionPort, createProjectUseCase, updateProjectUseCase, deleteProjectUseCase,
            updateProjectTrackingUseCase, addProjectContributionUseCase,
            updateBudgetUseCase, upsertBulkBudgetsUseCase, computeBudgetAllocationUseCase,
            createTransactionUseCase, updateTransactionUseCase, deleteTransactionUseCase,
            createGroupUseCase, inviteGroupMemberUseCase, lockGroupUseCase,
            createGroupProjectUseCase, createSubPersonalProjectUseCase,
            projectRepository);

    private final UUID userId = UUID.randomUUID();
    private final String actionId = "action-1";

    @BeforeEach
    void stubRecompute() {
        when(computeBudgetAllocationUseCase.execute(any())).thenReturn(BudgetAllocationPlanResponse.builder()
                .safeSpending(BigDecimal.valueOf(5_000_000))
                .envelope(BigDecimal.valueOf(3_000_000))
                .build());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Project projectWith(UUID projectUuid, boolean subPersonal) {
        ProjectId projectId = new ProjectId(projectUuid);
        ProjectTracking tracking = new ProjectTracking(
                UUID.randomUUID(), projectId,
                BigDecimal.valueOf(1_000_000), 1,
                BigDecimal.valueOf(500_000), 12,
                BigDecimal.ZERO, 0,
                Instant.now(), Instant.now());
        UUID groupProjectId = subPersonal ? UUID.randomUUID() : null;
        return Project.rehydrate(
                projectId, UserId.of(userId.toString()), ProjectType.PERSONAL,
                ProjectStatus.ACTIVE, ProjectPriority.HIGH, "test", "desc",
                new Money(BigDecimal.valueOf(20_000_000), "VND"),
                LocalDate.now().plusMonths(12), groupProjectId, List.of(), tracking, Instant.now());
    }

    /** A group-linked project belonging to a DIFFERENT member — used to prove the §5 (A)
     *  contribution exception is scoped to the confirming user's own share. */
    private Project projectOwnedByOther(UUID projectUuid) {
        ProjectId projectId = new ProjectId(projectUuid);
        return Project.rehydrate(
                projectId, UserId.of(UUID.randomUUID().toString()), ProjectType.PERSONAL,
                ProjectStatus.ACTIVE, ProjectPriority.HIGH, "someone else's share", "desc",
                new Money(BigDecimal.valueOf(20_000_000), "VND"),
                LocalDate.now().plusMonths(12), UUID.randomUUID(), List.of(), null, Instant.now());
    }

    // ── Guard behaviour ──────────────────────────────────────────────────────

    @Test
    @DisplayName("PROJECT_UPDATE_DEADLINE on a group-linked project is blocked and never calls the update use case")
    void projectUpdateDeadlineOnGroupLinkedBlocked() {
        UUID projectUuid = UUID.randomUUID();
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_UPDATE_DEADLINE)
                .projectId(projectUuid.toString())
                .newDeadline(LocalDate.now().plusMonths(6))
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));
        when(projectRepository.findById(any())).thenReturn(Optional.of(projectWith(projectUuid, true)));

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        // Blocked: no downstream mutation, response.success == false, reply is the guard's own
        // bilingual message (not the generic "Could not apply the change" prefix — that would
        // double up on "we couldn't do X because Y").
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getReply()).contains("dự án chung với nhóm");
        verify(updateProjectUseCase, never()).execute(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("PROJECT_DELETE on a group-linked project is blocked (no exception to the caller)")
    void projectDeleteOnGroupLinkedBlocked() {
        UUID projectUuid = UUID.randomUUID();
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_DELETE)
                .projectId(projectUuid.toString())
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));
        when(projectRepository.findById(any())).thenReturn(Optional.of(projectWith(projectUuid, true)));

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getReply()).contains("dự án chung với nhóm");
        verify(deleteProjectUseCase, never()).execute(anyString(), anyString());
    }

    @Test
    @DisplayName("PROJECT_TRACKING_UPDATE on a group-linked project is blocked")
    void projectTrackingUpdateOnGroupLinkedBlocked() {
        UUID projectUuid = UUID.randomUUID();
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_TRACKING_UPDATE)
                .projectId(projectUuid.toString())
                .trackingMoneySaved(BigDecimal.valueOf(3_000_000))
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));
        when(projectRepository.findById(any())).thenReturn(Optional.of(projectWith(projectUuid, true)));

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getReply()).contains("dự án chung với nhóm");
        verify(updateProjectTrackingUseCase, never()).execute(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("PROJECT_CONTRIBUTION_ADD on the user's OWN group-linked project is ALLOWED — §5 (A)")
    void projectContributionAddOnOwnGroupLinkedAllowed() {
        // Paying your own share into a group fund only ever raises the group's progress and only
        // spends the payer's money — no other member's agreement is needed. Blanket-blocking it
        // (the previous behaviour) dead-ended the group flow in chat.
        UUID projectUuid = UUID.randomUUID();
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_CONTRIBUTION_ADD)
                .projectId(projectUuid.toString())
                .contributionAmount(BigDecimal.valueOf(2_000_000))
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));
        when(projectRepository.findById(any())).thenReturn(Optional.of(projectWith(projectUuid, true)));

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        assertThat(response.isSuccess()).isTrue();
        verify(addProjectContributionUseCase)
                .execute(eq(userId.toString()), eq(projectUuid.toString()), any());
    }

    @Test
    @DisplayName("PROJECT_CONTRIBUTION_ADD on ANOTHER member's group-linked project is blocked")
    void projectContributionAddOnOtherMembersShareBlocked() {
        // The §5 (A) exception is scoped to the confirming user's OWN share. A pending action can
        // outlive the turn that produced it, so the id in it isn't guaranteed to still be theirs.
        UUID projectUuid = UUID.randomUUID();
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_CONTRIBUTION_ADD)
                .projectId(projectUuid.toString())
                .contributionAmount(BigDecimal.valueOf(2_000_000))
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));
        when(projectRepository.findById(any()))
                .thenReturn(Optional.of(projectOwnedByOther(projectUuid)));

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getReply()).contains("dự án chung với nhóm");
        verify(addProjectContributionUseCase, never()).execute(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("english=true action gets an English guard message")
    void englishActionGetsEnglishMessage() {
        UUID projectUuid = UUID.randomUUID();
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_UPDATE_DEADLINE)
                .projectId(projectUuid.toString())
                .newDeadline(LocalDate.now().plusMonths(6))
                .english(true)
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));
        when(projectRepository.findById(any())).thenReturn(Optional.of(projectWith(projectUuid, true)));

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        assertThat(response.getReply()).contains("group project");
        assertThat(response.isEnglish()).isTrue();
    }

    // ── Cases the guard MUST let through ────────────────────────────────────

    @Test
    @DisplayName("PROJECT_UPDATE_DEADLINE on a standalone (personal) project proceeds normally")
    void standaloneProjectProceeds() {
        UUID projectUuid = UUID.randomUUID();
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_UPDATE_DEADLINE)
                .projectId(projectUuid.toString())
                .newDeadline(LocalDate.now().plusMonths(6))
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));
        when(projectRepository.findById(any())).thenReturn(Optional.of(projectWith(projectUuid, false)));

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        assertThat(response.isSuccess()).isTrue();
        verify(updateProjectUseCase).execute(eq(userId.toString()), eq(projectUuid.toString()), any());
    }

    @Test
    @DisplayName("PROJECT_CREATE has no projectId — guard is bypassed, create runs")
    void projectCreateBypassesGuard() {
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_CREATE)
                // No projectId — a brand-new project can't be group-linked at creation time.
                .name("Mua nhà")
                .targetAmount(BigDecimal.valueOf(500_000_000))
                .newDeadline(LocalDate.now().plusYears(2))
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        assertThat(response.isSuccess()).isTrue();
        verify(createProjectUseCase).execute(eq(userId.toString()), any());
        // projectRepository must NOT even be consulted — the guard shortcuts on null projectId.
        verify(projectRepository, never()).findById(any());
    }

    @Test
    @DisplayName("UPDATE_BUDGET_LIMIT has no project involvement — guard is bypassed")
    void budgetUpdateBypassesGuard() {
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.UPDATE_BUDGET_LIMIT)
                .budgetId(UUID.randomUUID().toString())
                .newAmountLimit(BigDecimal.valueOf(3_000_000))
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        assertThat(response.isSuccess()).isTrue();
        verify(updateBudgetUseCase).execute(any(UUID.class), any(UUID.class), any());
        verify(projectRepository, never()).findById(any());
    }

    // ── Guard fallback behaviour ────────────────────────────────────────────

    @Test
    @DisplayName("guard is fail-open when target project no longer exists (rare race — downstream use case will handle)")
    void projectNotFoundFallsOpen() {
        UUID projectUuid = UUID.randomUUID();
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_UPDATE_DEADLINE)
                .projectId(projectUuid.toString())
                .newDeadline(LocalDate.now().plusMonths(6))
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));
        when(projectRepository.findById(any())).thenReturn(Optional.empty());

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        // Guard doesn't block — the downstream UpdateProjectUseCase handles "project not found"
        // per its own contract. Whatever it does, it's the same as before the guard existed.
        verify(updateProjectUseCase).execute(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("malformed projectId string is fail-open (guard doesn't hijack decoding errors from the real use case)")
    void malformedProjectIdFallsOpen() {
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_UPDATE_DEADLINE)
                .projectId("not-a-uuid")
                .newDeadline(LocalDate.now().plusMonths(6))
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));

        useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(true).build());

        // The malformed id will crash inside UpdateProjectUseCase, which the outer catch handles
        // — but the guard itself must not synthesize a bogus "blocked because group" message
        // when the id is just malformed. So we assert projectRepository was NOT queried; the
        // downstream use case sees the raw string and errors on its own terms.
        verify(projectRepository, never()).findById(any());
    }

    @Test
    @DisplayName("confirm=false skips both the guard and the mutation (deny is always allowed)")
    void denyBypassesGuardEntirely() {
        UUID projectUuid = UUID.randomUUID();
        PendingChatAction action = PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_UPDATE_DEADLINE)
                .projectId(projectUuid.toString())
                .newDeadline(LocalDate.now().plusMonths(6))
                .build();
        when(pendingActionPort.load(userId, actionId)).thenReturn(Optional.of(action));

        ChatActionConfirmResponse response = useCase.execute(userId,
                ConfirmChatActionRequest.builder().actionId(actionId).confirm(false).build());

        // Even if the pending action targeted a group project, denying it is always OK — no
        // mutation runs, no need to consult the project repository for the guard check.
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getReply()).contains("Đã hủy");
        verify(updateProjectUseCase, never()).execute(anyString(), anyString(), any());
        verify(projectRepository, never()).findById(any());
    }
}