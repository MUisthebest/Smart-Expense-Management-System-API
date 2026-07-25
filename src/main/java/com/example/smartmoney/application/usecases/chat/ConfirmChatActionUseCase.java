package com.example.smartmoney.application.usecases.chat;

import com.example.smartmoney.application.dtos.chat.PendingChatAction;
import com.example.smartmoney.application.dtos.requests.budgets.CreateBulkBudgetsRequest;
import com.example.smartmoney.application.dtos.requests.budgets.UpdateBudgetRequest;
import com.example.smartmoney.application.dtos.requests.chat.ConfirmChatActionRequest;
import com.example.smartmoney.application.dtos.requests.project.AddProjectContributionRequest;
import com.example.smartmoney.application.dtos.requests.project.CreateProjectRequest;
import com.example.smartmoney.application.dtos.requests.project.UpdateProjectRequest;
import com.example.smartmoney.application.dtos.requests.project.UpdateProjectTrackingRequest;
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
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.transaction.enums.Category;
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

    public ChatActionConfirmResponse execute(UUID userId, ConfirmChatActionRequest request) {
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

        // Step 2 — recompute (read-only, never applies itself). Best-effort: the mutation already
        // succeeded, so a failure here still reports success without the fresh numbers.
        try {
            BudgetAllocationPlanResponse plan = computeBudgetAllocationUseCase.execute(userId);
            return ChatActionConfirmResponse.builder()
                    .success(true)
                    .reply(english ? "Update applied successfully." : "Đã cập nhật thành công.")
                    .safeSpendingAfter(plan.getSafeSpending() != null ? plan.getSafeSpending().longValue() : null)
                    .envelopeAfter(plan.getEnvelope() != null ? plan.getEnvelope().longValue() : null)
                    .english(english)
                    .build();
        } catch (Exception e) {
            log.warn("ConfirmChatActionUseCase: budget recompute failed after action for user {}: {}",
                    userId, e.getMessage());
            return ChatActionConfirmResponse.builder()
                    .success(true)
                    .reply(english ? "Update applied successfully." : "Đã cập nhật thành công.")
                    .english(english)
                    .build();
        }
    }

    private void clearLatestIfMatches(UUID userId, String actionId) {
        pendingActionPort.loadLatestId(userId)
                .filter(actionId::equals)
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
}
