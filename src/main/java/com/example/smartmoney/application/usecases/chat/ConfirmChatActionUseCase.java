package com.example.smartmoney.application.usecases.chat;

import com.example.smartmoney.application.dtos.chat.PendingChatAction;
import com.example.smartmoney.application.dtos.requests.budgets.CreateBulkBudgetsRequest;
import com.example.smartmoney.application.dtos.requests.budgets.UpdateBudgetRequest;
import com.example.smartmoney.application.dtos.requests.chat.ConfirmChatActionRequest;
import com.example.smartmoney.application.dtos.requests.project.AddProjectContributionRequest;
import com.example.smartmoney.application.dtos.requests.project.CreateProjectRequest;
import com.example.smartmoney.application.dtos.requests.project.UpdateProjectRequest;
import com.example.smartmoney.application.dtos.requests.project.UpdateProjectTrackingRequest;
import com.example.smartmoney.application.dtos.requests.transactions.CreateTransactionRequest;
import com.example.smartmoney.application.dtos.requests.transactions.UpdateTransactionRequest;
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
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * STEP 1 / STEP 2 / STEP 3 confirm-then-execute workflow for a {@link PendingChatAction}: loads
 * the previewed action, executes it for real via the same use cases the frontend already calls
 * directly ({@link CreateProjectUseCase} / {@link UpdateProjectUseCase} /
 * {@link DeleteProjectUseCase} / {@link UpdateBudgetUseCase}), recomputes the budget allocation
 * (Step 2) unless the action is a pure project delete, then reports the result (Step 3).
 * Single-use — the pending action is deleted from Redis whether confirmed or denied.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfirmChatActionUseCase {

    private final ChatPendingActionPort pendingActionPort;
    private final CreateProjectUseCase createProjectUseCase;
    private final UpdateProjectUseCase updateProjectUseCase;
    private final DeleteProjectUseCase deleteProjectUseCase;
    private final UpdateProjectTrackingUseCase updateProjectTrackingUseCase;
    private final AddProjectContributionUseCase addProjectContributionUseCase;
    private final UpdateBudgetUseCase updateBudgetUseCase;
    private final UpsertBulkBudgetsUseCase upsertBulkBudgetsUseCase;
    private final ComputeBudgetAllocationUseCase computeBudgetAllocationUseCase;
    // Transaction CRUD goes through the very same use cases POST/PUT/DELETE /api/v1/transactions
    // call, so a chat-entered expense rolls into the month's Budget.spent and fires the same
    // anomaly/suggestion notifications as one typed into the app's own form.
    private final CreateTransactionUseCase createTransactionUseCase;
    private final UpdateTransactionUseCase updateTransactionUseCase;
    private final DeleteTransactionUseCase deleteTransactionUseCase;
    // §5 defense-in-depth (last line): even when the orchestrator's filter and the LLM's prompt
    // both fail, no confirm here is allowed to mutate a project with groupProjectId != null. A
    // pending action can outlive the turn that produced it (Redis TTL), so a fix that only
    // scrubs today's suggestions doesn't protect against an already-stored group-linked action
    // from a previous version of this code. Reject at execution time by loading the target
    // project fresh and checking isSubPersonal().
    private final ProjectRepository projectRepository;

    public ChatActionConfirmResponse execute(UUID userId, ConfirmChatActionRequest request) {
        // A missing/blank actionId is a client contract error, not a server crash: without this
        // guard clearLatestIfMatches below NPEs on `actionId::equals` (method reference on a null
        // receiver), which AIController's catch-all turns into an opaque 500. Say what's wrong
        // instead. See ConfirmChatActionRequest.actionId for how a client ends up here.
        if (request.getActionId() == null || request.getActionId().isBlank()) {
            log.warn("ConfirmChatActionUseCase: confirm called without an actionId for user {}", userId);
            return ChatActionConfirmResponse.builder()
                    .success(false)
                    .reply("Thiếu mã thao tác cần xác nhận (actionId). Bạn có thể hỏi lại.")
                    .build();
        }

        // Whichever entry point resolves an actionId (the deterministic plain yes/no gate in
        // ChatWithAIUseCase, or this endpoint called directly), clear the "latest pending action"
        // pointer if it still points here — otherwise a later bare "yes"/"no" could resolve against
        // an id that was already consumed through a different path, and get a confusing "expired"
        // reply instead of being treated as an ordinary message.
        clearLatestIfMatches(userId, request.getActionId());

        Optional<PendingChatAction> maybeAction = pendingActionPort.load(userId, request.getActionId());
        if (maybeAction.isEmpty()) {
            return ChatActionConfirmResponse.builder()
                    .success(false)
                    .reply("Yêu cầu này đã hết hạn hoặc không tồn tại. Bạn có thể hỏi lại.")
                    .build();
        }

        PendingChatAction action = maybeAction.get();
        pendingActionPort.delete(userId, request.getActionId());
        boolean english = action.isEnglish();

        if (!request.isConfirm()) {
            return ChatActionConfirmResponse.builder()
                    .success(false)
                    .reply(english ? "Cancelled — no changes made." : "Đã hủy thao tác.")
                    .english(english)
                    .build();
        }

        ActionOutcome outcome;
        try {
            outcome = executeAction(userId, action);
        } catch (IllegalStateException blocked) {
            // §5 group-linked guard threw with an already-user-facing bilingual message; surface
            // as-is instead of prefixing "Could not apply the change:", which would double up
            // the "we couldn't do X because Y" framing the guard's message already carries.
            log.info("ConfirmChatActionUseCase: action {} blocked for user {}: {}",
                    action.getType(), userId, blocked.getMessage());
            return ChatActionConfirmResponse.builder()
                    .success(false)
                    .reply(blocked.getMessage())
                    .english(english)
                    .build();
        } catch (Exception e) {
            // A single (non-batch) action threw directly — nothing was applied.
            log.warn("ConfirmChatActionUseCase: failed to execute action {} for user {}: {}",
                    action.getType(), userId, e.getMessage());
            return ChatActionConfirmResponse.builder()
                    .success(false)
                    .reply((english ? "Could not apply the change: " : "Không thể thực hiện thay đổi: ")
                            + e.getMessage())
                    .english(english)
                    .build();
        }

        if (!outcome.allSucceeded()) {
            // A BATCH partially failed — e.g. one of several project deletions hit an error.
            // Each sub-action's real use case is independently @Transactional, so the ones that
            // succeeded already committed; reporting a flat "failed" here (the old behavior)
            // would hide that and leave the user thinking NOTHING happened.
            return ChatActionConfirmResponse.builder()
                    .success(outcome.anySucceeded())
                    .reply(buildPartialOutcomeReply(outcome, english))
                    .english(english)
                    .build();
        }

        if (action.getType() == PendingChatAction.Type.PROJECT_DELETE) {
            return ChatActionConfirmResponse.builder()
                    .success(true)
                    .reply(english ? "Project deleted successfully." : "Đã xóa dự án thành công.")
                    .english(english)
                    .build();
        }

        String successReply = successReply(action.getType(), english);

        // Step 2 — recompute (read-only, never applies itself). Best-effort: the mutation already
        // succeeded, so a failure here still reports success without the fresh numbers.
        try {
            BudgetAllocationPlanResponse plan = computeBudgetAllocationUseCase.execute(userId);
            return ChatActionConfirmResponse.builder()
                    .success(true)
                    .reply(successReply)
                    .safeSpendingAfter(plan.getSafeSpending() != null ? plan.getSafeSpending().longValue() : null)
                    .envelopeAfter(plan.getEnvelope() != null ? plan.getEnvelope().longValue() : null)
                    .english(english)
                    .build();
        } catch (Exception e) {
            log.warn("ConfirmChatActionUseCase: budget recompute failed after action for user {}: {}",
                    userId, e.getMessage());
            return ChatActionConfirmResponse.builder()
                    .success(true)
                    .reply(successReply)
                    .english(english)
                    .build();
        }
    }

    /** Names what actually happened rather than a blanket "updated" — a user who just confirmed
     *  recording a transaction shouldn't have to guess whether the ledger or a budget moved. */
    private String successReply(PendingChatAction.Type type, boolean english) {
        return switch (type) {
            case TRANSACTION_CREATE -> english
                    ? "Transaction recorded successfully." : "Đã ghi nhận giao dịch thành công.";
            case TRANSACTION_UPDATE -> english
                    ? "Transaction updated successfully." : "Đã cập nhật giao dịch thành công.";
            case TRANSACTION_DELETE -> english
                    ? "Transaction deleted successfully." : "Đã xóa giao dịch thành công.";
            default -> english ? "Update applied successfully." : "Đã cập nhật thành công.";
        };
    }

    private void clearLatestIfMatches(UUID userId, String actionId) {
        pendingActionPort.loadLatestId(userId)
                .filter(id -> id.equals(actionId))
                .ifPresent(id -> pendingActionPort.clearLatestId(userId));
    }

    /** Result of executing one action. For a non-batch action, always 1/1 (or an exception
     *  propagates directly instead). For a BATCH/SWAP, reflects how many of its sub-actions
     *  actually succeeded — see {@link #executeBatch}. */
    private record ActionOutcome(int succeeded, int total, List<String> failures) {
        private static final ActionOutcome SINGLE_SUCCESS = new ActionOutcome(1, 1, List.of());
        boolean allSucceeded() { return failures.isEmpty(); }
        boolean anySucceeded() { return succeeded > 0; }
    }

    private ActionOutcome executeAction(UUID userId, PendingChatAction action) {
        String actorId = userId.toString();
        // §5 last-line guard — any PROJECT_* action whose target project is a sub-personal
        // (group-linked) project is rejected here. See the projectRepository field's doc; this
        // protects both against a stray pending action from an older code version and against
        // a bug in the orchestrator's filter. Guard runs BEFORE the switch so PROJECT_CREATE
        // (which has no projectId) naturally bypasses it; no personal project is group-linked
        // at creation time.
        rejectIfGroupLinkedTarget(userId, action);
        switch (action.getType()) {
            case UPDATE_BUDGET_LIMIT -> applyBudgetLimit(userId, action);
            case APPLY_NEXT_MONTH_BUDGET_PLAN -> applyNextMonthBudgetPlan(userId, action);
            case UPDATE_BUDGET_LIMIT_SWAP, BATCH -> {
                return executeBatch(userId, action);
            }
            case PROJECT_CREATE -> {
                CreateProjectRequest req = new CreateProjectRequest();
                req.setName(action.getName());
                req.setDescription(action.getDescription());
                req.setType(action.getProjectType() != null
                        ? ProjectType.valueOf(action.getProjectType()) : ProjectType.PERSONAL);
                req.setPriority(action.getPriority() != null
                        ? ProjectPriority.valueOf(action.getPriority()) : ProjectPriority.MEDIUM);
                req.setTargetAmount(action.getTargetAmount());
                req.setCurrency(action.getCurrency() != null ? action.getCurrency() : "VND");
                req.setDeadline(action.getNewDeadline());
                createProjectUseCase.execute(actorId, req);
            }
            case PROJECT_UPDATE_DEADLINE -> {
                UpdateProjectRequest req = new UpdateProjectRequest();
                req.setDeadline(action.getNewDeadline());
                updateProjectUseCase.execute(actorId, action.getProjectId(), req);
            }
            case PROJECT_DELETE -> deleteProjectUseCase.execute(actorId, action.getProjectId());
            case PROJECT_TRACKING_UPDATE -> {
                UpdateProjectTrackingRequest req = new UpdateProjectTrackingRequest();
                req.setMoneySaved(action.getTrackingMoneySaved());
                req.setMonthLeft(action.getTrackingMonthLeft());
                req.setMoneyOwed(action.getTrackingMoneyOwed());
                updateProjectTrackingUseCase.execute(actorId, action.getProjectId(), req);
            }
            case TRANSACTION_CREATE -> {
                CreateTransactionRequest req = new CreateTransactionRequest();
                req.setAmount(action.getTransactionAmount());
                req.setType(TransactionType.valueOf(action.getTransactionType()));
                req.setCategory(Category.valueOf(action.getTransactionCategory()));
                req.setDescription(action.getTransactionDescription());
                req.setDate(action.getTransactionDate());
                createTransactionUseCase.execute(userId, req);
            }
            case TRANSACTION_UPDATE -> {
                // Partial update: only the fields the user actually asked to change were stored
                // on the action, and UpdateTransactionUseCase leaves every null field untouched.
                UpdateTransactionRequest req = new UpdateTransactionRequest();
                req.setAmount(action.getTransactionAmount());
                req.setType(action.getTransactionType() != null
                        ? TransactionType.valueOf(action.getTransactionType()) : null);
                req.setCategory(action.getTransactionCategory() != null
                        ? Category.valueOf(action.getTransactionCategory()) : null);
                req.setDescription(action.getTransactionDescription());
                req.setDate(action.getTransactionDate());
                updateTransactionUseCase.execute(
                        userId, UUID.fromString(action.getTransactionId()), req);
            }
            case TRANSACTION_DELETE ->
                    deleteTransactionUseCase.execute(userId, UUID.fromString(action.getTransactionId()));
            case PROJECT_CONTRIBUTION_ADD -> {
                // Real money moving into the project — goes through the same use case the app's
                // own "Contribute" button calls, so it creates a ProjectContribution + ledger
                // Transaction and actually moves the visible progress bar (see the Type enum doc).
                AddProjectContributionRequest req = new AddProjectContributionRequest();
                req.setAmount(action.getContributionAmount());
                req.setNote(action.getContributionNote() != null ? action.getContributionNote()
                        : (action.isEnglish() ? "Contribution via chat assistant" : "Góp từ trợ lý trò chuyện"));
                addProjectContributionUseCase.execute(actorId, action.getProjectId(), req);
            }
        }
        return ActionOutcome.SINGLE_SUCCESS;
    }

    /**
     * Sub-actions inside a BATCH/SWAP are independent mutations (different projects/budget
     * categories) — one failing (e.g. deleting a project that hit a constraint, or an
     * already-abandoned group sub-project) must NOT silently abort the rest and leave the user
     * thinking nothing happened when some actually did succeed. Attempts every sub-action
     * regardless of earlier failures; each real use case is independently {@code @Transactional},
     * so the ones that succeed here have already committed by the time this returns.
     */
    private ActionOutcome executeBatch(UUID userId, PendingChatAction action) {
        List<PendingChatAction> subActions = action.getBatchActions() != null
                ? action.getBatchActions() : List.of();
        int succeeded = 0;
        List<String> failures = new ArrayList<>();
        for (PendingChatAction sub : subActions) {
            try {
                executeAction(userId, sub);
                succeeded++;
            } catch (Exception e) {
                failures.add(describeSubAction(sub) + ": " + e.getMessage());
                log.warn("ConfirmChatActionUseCase: batch sub-action {} failed for user {}: {}",
                        sub.getType(), userId, e.getMessage());
            }
        }
        return new ActionOutcome(succeeded, subActions.size(), failures);
    }

    private String describeSubAction(PendingChatAction sub) {
        if (sub.getName() != null) return sub.getName();
        if (sub.getProjectId() != null) return sub.getProjectId();
        if (sub.getBudgetId() != null) return sub.getBudgetId();
        return sub.getType().name();
    }

    private String buildPartialOutcomeReply(ActionOutcome outcome, boolean english) {
        String countLine = english
                ? String.format("%d of %d changes applied successfully.", outcome.succeeded(), outcome.total())
                : String.format("Đã áp dụng thành công %d/%d thay đổi.", outcome.succeeded(), outcome.total());
        String failureLine = english
                ? "Failed: " + String.join("; ", outcome.failures())
                : "Không thực hiện được: " + String.join("; ", outcome.failures());
        return countLine + "\n\n" + failureLine;
    }

    private void applyBudgetLimit(UUID userId, PendingChatAction action) {
        UpdateBudgetRequest req = new UpdateBudgetRequest();
        req.setAmountLimit(action.getNewAmountLimit());
        updateBudgetUseCase.execute(UUID.fromString(action.getBudgetId()), userId, req);
    }

    /**
     * Applies a next-month per-category plan via the upsert path (create-or-update, never a hard
     * conflict) — safe to confirm even if some of next month's categories already have a budget
     * row (e.g. from an earlier confirm, or set up manually).
     */
    private void applyNextMonthBudgetPlan(UUID userId, PendingChatAction action) {
        CreateBulkBudgetsRequest req = new CreateBulkBudgetsRequest();
        req.setMonth(action.getTargetMonth());
        req.setYear(action.getTargetYear());
        req.setBudgets(action.getCategoryAmounts().stream()
                .map(ca -> {
                    CreateBulkBudgetsRequest.BudgetItem item = new CreateBulkBudgetsRequest.BudgetItem();
                    item.setCategory(Category.valueOf(ca.getCategory()));
                    item.setAmountLimit(ca.getAmountLimit());
                    return item;
                })
                .toList());
        upsertBulkBudgetsUseCase.execute(userId, req);
    }

    /**
     * §5 last-line guard for a single (non-batch) pending action about to be executed. If the
     * action targets a project that turns out to be sub-personal (Project.isSubPersonal() true,
     * i.e. groupProjectId != null — half of a group commitment), reject before any mutation
     * runs. Runs on every {@link #executeAction} invocation, including inside {@link #executeBatch}
     * so a mixed-batch containing one group-linked entry rejects only that entry — the outcome
     * bookkeeping in executeBatch already treats a per-sub throw as a per-sub failure without
     * aborting the rest.
     *
     * <p>ONE exception, and only one: {@link PendingChatAction.Type#PROJECT_CONTRIBUTION_ADD} on a
     * sub-personal project the confirming user OWNS. Paying your own share into a group fund is
     * exactly what a sub-personal project exists for — it moves only the payer's own money and
     * only ever increases the group's progress, so it needs no one else's agreement. What §5
     * actually protects against is a chat turn silently changing the SHAPE of a shared commitment
     * (deadline, target, tracking row, deletion) or touching another member's share; those all
     * stay blocked. Blanket-rejecting contributions too made the group flow dead-ended in chat:
     * the assistant could describe the shortfall but the user could do nothing about it here.
     *
     * <p>The ownership re-check matters even though {@code Project.addContribution} enforces it
     * downstream: a pending action can outlive the turn that produced it, so the id in it is not
     * guaranteed to still be one of this user's projects. Failing ownership yields the group-
     * consensus message (this is someone else's share) rather than a raw permission error.
     *
     * <p>Fail-open when we can't tell (project not found, repo error): don't block a legitimate
     * confirm on infrastructure noise. A group-linked project that's been deleted between prompt
     * and confirm is a rare edge case and worst-case behaviour is a normal execute-then-notfound
     * error from the downstream use case — same as before this guard existed.
     *
     * <p>Actions without a projectId (PROJECT_CREATE, UPDATE_BUDGET_LIMIT, APPLY_NEXT_MONTH_
     * BUDGET_PLAN, UPDATE_BUDGET_LIMIT_SWAP, BATCH) skip the check naturally — they can't be
     * targeting a group-linked project because they don't reference one.
     */
    private void rejectIfGroupLinkedTarget(UUID userId, PendingChatAction action) {
        String projectId = action.getProjectId();
        if (projectId == null || projectId.isBlank()) return;
        try {
            Optional<Project> maybeProject =
                    projectRepository.findById(new ProjectId(java.util.UUID.fromString(projectId)));
            if (maybeProject.isEmpty()) return;
            Project project = maybeProject.get();
            if (!project.isSubPersonal()) return;
            if (action.getType() == PendingChatAction.Type.PROJECT_CONTRIBUTION_ADD
                    && isOwnedBy(project, userId)) {
                return;
            }
        } catch (IllegalArgumentException badUuid) {
            // Malformed projectId — let the downstream use case handle it; not a §5 case.
            return;
        } catch (Exception e) {
            log.warn("ConfirmChatActionUseCase: could not check group-linked status for project {}: {}",
                    projectId, e.getMessage());
            return;
        }
        String msg = action.isEnglish()
                ? "This is a group project — changing it (or another member's share) needs the group's " +
                  "agreement. You can still pay your own share into it; for anything else, please open " +
                  "the Group screen and discuss it with the other members first."
                : "Đây là dự án chung với nhóm — việc thay đổi dự án nhóm (hoặc phần đóng góp của thành " +
                  "viên khác) cần được cả nhóm đồng thuận. Bạn vẫn có thể đóng quỹ phần của mình; với các " +
                  "thao tác khác, vui lòng mở màn hình Nhóm để thảo luận với các thành viên trước.";
        throw new IllegalStateException(msg);
    }

    private boolean isOwnedBy(Project project, UUID userId) {
        return project.getOwnerId() != null
                && project.getOwnerId().equals(UserId.of(userId.toString()));
    }
}