package com.example.smartmoney.application.dtos.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A chat-turn suggestion the backend can execute for real, persisted in Redis under a fresh id
 * and returned to the client as {@code ChatResponse.pendingActionId}. {@code POST
 * /api/v1/ai/chat/confirm} loads it by id, and — only on explicit confirm — {@code
 * ConfirmChatActionUseCase} calls the matching real use case. Single-use: deleted from Redis
 * after being confirmed or denied.
 *
 * <p>Mutable POJO (not a sealed hierarchy) so it round-trips through Jackson/Redis the same way
 * as {@code ChatMessageDto} — one type with nullable per-{@link Type} fields rather than a
 * polymorphic hierarchy, matching this codebase's existing DTO convention (e.g.
 * {@code SavingsPlanSuggestion}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingChatAction {

    public enum Type {
        UPDATE_BUDGET_LIMIT,
        UPDATE_BUDGET_LIMIT_SWAP,
        APPLY_NEXT_MONTH_BUDGET_PLAN,
        PROJECT_CREATE,
        PROJECT_UPDATE_DEADLINE,
        /** Edit an existing project's name / description / target / currency / deadline /
         *  priority in one go — {@code UpdateProjectUseCase}, the same use case {@code PUT
         *  /api/v1/projects/{id}} calls. Only the fields the user asked to change are set; the
         *  rest stay null and {@code UpdateProjectRequest} leaves them untouched. Distinct from
         *  {@code PROJECT_UPDATE_DEADLINE}, which the older regex-driven shorten/extend path
         *  still emits and which only ever carries a deadline. */
        PROJECT_UPDATE_DETAILS,
        PROJECT_DELETE,
        PROJECT_TRACKING_UPDATE,
        /** A positive amount actually being added TO a project (windfall boost, an explicit
         *  "tôi vừa góp thêm X" contribution, ...) — routes through {@code
         *  AddProjectContributionUseCase} so it creates a real {@code ProjectContribution} +
         *  ledger {@code Transaction}, the same source of truth {@code Project.totalContributed()}
         *  (and every progress/completion display) reads from. {@code PROJECT_TRACKING_UPDATE}'s
         *  {@code trackingMoneySaved} only touches the separate pace-tracking row, which no longer
         *  feeds visible progress — confirming that one alone looks like nothing happened. */
        PROJECT_CONTRIBUTION_ADD,
        /** Record a brand-new transaction — {@code CreateTransactionUseCase}, the same use case
         *  {@code POST /api/v1/transactions} calls, so an EXPENSE also rolls into the matching
         *  month's Budget {@code spent} exactly as a manually-entered one would. */
        TRANSACTION_CREATE,
        /** Edit an existing transaction — {@code UpdateTransactionUseCase}. Only the fields the
         *  user asked to change are set; the rest stay null and are left untouched. */
        TRANSACTION_UPDATE,
        /** Remove an existing transaction — {@code DeleteTransactionUseCase}. */
        TRANSACTION_DELETE,

        // ── Group / group-project flows ───────────────────────────────────────
        // These used to have no action type at all, so the chat could only ever describe the
        // screens to tap. They now run the same use cases the REST controllers call, which is
        // what makes "tạo nhóm và mời An qua email" a real outcome rather than a walkthrough.
        /** Create a savings group — {@code CreateGroupUseCase} ({@code POST /api/v1/groups}).
         *  The actor becomes its admin. */
        GROUP_CREATE,
        /** Invite one member by email — {@code InviteGroupMemberUseCase} ({@code POST
         *  /api/v1/groups/{id}/members/invite}). Admin-only, and the invitee must already have a
         *  SmartMoney account; the use case sends the invite email itself. */
        GROUP_INVITE_MEMBER,
        /** Lock a group's membership — {@code LockGroupUseCase}. Required before a group project
         *  can be created. */
        GROUP_LOCK,
        /** Create the group's project — {@code CreateGroupProjectUseCase}. Admin-only, group must
         *  be LOCKED, one project per group. */
        GROUP_PROJECT_CREATE,
        /** Join a group project by opening one's own sub-personal project —
         *  {@code CreateSubPersonalProjectUseCase}. */
        GROUP_PROJECT_JOIN,
        BATCH
    }

    private Type type;

    /** Language of the message that produced this suggestion (from {@code MessageLanguageDetector}) —
     *  stored on the action itself since {@code ConfirmChatActionUseCase} has no message text of its
     *  own to detect language from (a button tap / plain "yes" carries no prose). Defaults to false
     *  (Vietnamese), matching {@code MessageLanguageDetector}'s "default to Vietnamese when
     *  inconclusive" convention. */
    private boolean english;

    // ── UPDATE_BUDGET_LIMIT (also each entry inside an UPDATE_BUDGET_LIMIT_SWAP batch) ──
    private String budgetId;
    private BigDecimal newAmountLimit;

    // ── PROJECT_UPDATE_DEADLINE / PROJECT_DELETE / PROJECT_TRACKING_UPDATE ───
    private String projectId;
    private LocalDate newDeadline;

    // ── PROJECT_TRACKING_UPDATE — PUT /api/v1/projects/{id}/tracking (moneySaved
    // contribution/windfall boost, monthLeft deferral, or moneyOwed debt change). Each field is
    // independently nullable, same as UpdateProjectTrackingRequest — only the ones relevant to
    // the originating suggestion are set.
    private BigDecimal trackingMoneySaved;
    private Integer trackingMonthLeft;
    private BigDecimal trackingMoneyOwed;

    // ── PROJECT_CONTRIBUTION_ADD — POST /api/v1/projects/{id}/contributions ──────
    private BigDecimal contributionAmount;
    private String contributionNote;

    // ── PROJECT_CREATE ────────────────────────────────────────────────────────
    private String name;
    private String description;
    private String projectType;   // ProjectType enum name
    private String priority;      // ProjectPriority enum name
    private BigDecimal targetAmount;
    private String currency;

    // ── TRANSACTION_CREATE / TRANSACTION_UPDATE / TRANSACTION_DELETE ──────────
    // Mirrors CreateTransactionRequest/UpdateTransactionRequest field-for-field. On CREATE every
    // field except description is required (validated in TransactionChangeService before the
    // action is ever stored); on UPDATE each is independently nullable — only the ones the user
    // actually asked to change are set, matching UpdateTransactionRequest's partial-update
    // semantics. On DELETE only transactionId is used.
    private String transactionId;
    private BigDecimal transactionAmount;
    private String transactionType;        // TransactionType enum name
    private String transactionCategory;    // Category enum name
    private String transactionDescription;
    private String transactionDate;        // "dd/MM/yyyy HH:mm", same format the DTOs validate

    // ── GROUP_* / GROUP_PROJECT_* ─────────────────────────────────────────────
    // Mirrors CreateGroupRequest / InviteGroupMemberRequest / CreateGroupProjectRequest /
    // CreateSubPersonalProjectRequest field-for-field. Validated in GroupChangeService (group
    // exists, actor is its admin, group in the right state, email well-formed) before the action
    // is ever stored, so confirm only has to map fields onto the request DTO.
    private String groupId;
    private String groupName;
    private String groupDescription;
    /** GROUP_INVITE_MEMBER — the invitee's registered SmartMoney email address. */
    private String inviteEmail;
    /** GROUP_PROJECT_JOIN — which group project to open a sub-personal project against. */
    private String groupProjectId;
    /** GROUP_PROJECT_CREATE — 1..60, the group project's saving horizon. */
    private Integer totalMonths;

    // ── APPLY_NEXT_MONTH_BUDGET_PLAN ──────────────────────────────────────────
    private Integer targetMonth;
    private Integer targetYear;
    private List<CategoryAmount> categoryAmounts;

    // ── BATCH / UPDATE_BUDGET_LIMIT_SWAP ─────────────────────────────────────
    private List<PendingChatAction> batchActions;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryAmount {
        private String category;
        private BigDecimal amountLimit;
    }
}
