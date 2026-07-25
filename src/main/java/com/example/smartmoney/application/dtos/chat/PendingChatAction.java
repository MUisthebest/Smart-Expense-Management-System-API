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
