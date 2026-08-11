package com.example.smartmoney.application.services;

import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;

/**
 * Turns internal identifiers into the words a person would actually use.
 *
 * <h2>Why this exists</h2>
 *
 * Everything the chat turn shows the user — the LLM's {@code reply}, the notes Java appends to it
 * ({@code checkNearLimit}, {@code abnormalIncreaseWarning}, ...), and the {@code reason} on every
 * suggestion card — used to leak raw application identifiers: {@code FOOD}, {@code EXCEEDED},
 * {@code DEFICIT}, {@code safe_spending}, {@code monthlySaving}. Those are database/enum tokens,
 * not Vietnamese (or English) words, and a user reading "Bạn đã dùng 92% ngân sách FOOD" is being
 * shown a piece of the schema.
 *
 * <p>This class is the one place that maps each token to a human phrase in both languages, so a
 * new label never has to be re-invented (and re-translated slightly differently) at each call
 * site. The LLM side of the same rule lives in {@code ChatbotPromptBuilder} §10, which forbids raw
 * identifiers in {@code reply} and publishes the same vocabulary — the two must stay in step: if a
 * label changes here, change the §10 table too, otherwise the Java-appended paragraph and the LLM
 * paragraph of the SAME reply will call one thing by two names.
 *
 * <p>Machine tokens are deliberately still emitted inside the prompt's financial-context block
 * (they are what {@code transactionDraft.category} / {@code budgetId} must be filled with) — but
 * there they are always paired with the label from this class, so the model has the human wording
 * ready to hand and never has to fall back on the token.
 */
public final class FinancialTermFormatter {

    private FinancialTermFormatter() {
    }

    // ── Transaction / budget categories ──────────────────────────────────────

    /** Human label for a spending/income category, e.g. {@code FOOD} → "ăn uống" / "food". */
    public static String category(Category category, boolean english) {
        if (category == null) return english ? "uncategorised" : "chưa phân loại";
        return switch (category) {
            case FOOD -> english ? "food & drink" : "ăn uống";
            case TRANSPORTATION -> english ? "transport" : "đi lại";
            case CLOTHING -> english ? "clothing" : "quần áo";
            case UTILITIES -> english ? "utilities" : "tiện ích";
            case ENTERTAINMENT -> english ? "entertainment" : "giải trí";
            case HEALTH -> english ? "health" : "sức khoẻ";
            case EDUCATION -> english ? "education" : "giáo dục";
            case SHOPPING -> english ? "shopping" : "mua sắm";
            case OTHER -> english ? "other" : "khác";
            case SALARY -> english ? "salary" : "lương";
            case BONUS -> english ? "bonus" : "thưởng";
            case INVESTMENT -> english ? "investment" : "đầu tư";
            case GIFT -> english ? "gift" : "quà tặng";
            case PROJECT_REFUND -> english ? "project refund" : "hoàn tiền dự án";
            case PROJECT_CONTRIBUTION -> english ? "project contribution" : "góp vào dự án";
        };
    }

    /**
     * Same as {@link #category(Category, boolean)} but tolerant of a raw name string — the
     * suggestion DTOs carry {@code category} as a {@code String}, so callers holding one of those
     * don't have to parse it back into the enum just to print it.
     */
    public static String category(String categoryName, boolean english) {
        if (categoryName == null || categoryName.isBlank()) {
            return english ? "uncategorised" : "chưa phân loại";
        }
        try {
            return category(Category.valueOf(categoryName.trim().toUpperCase()), english);
        } catch (IllegalArgumentException e) {
            // Unknown token (older row, hand-written value): lower-casing it at least stops it
            // from reading like a constant shouted at the user.
            return categoryName.toLowerCase();
        }
    }

    // ── Budget alert level ───────────────────────────────────────────────────

    /** Human label for {@code Budget.getAlertLevel()}, e.g. {@code EXCEEDED} → "đã vượt hạn mức". */
    public static String alertLevel(Budget.AlertLevel level, boolean english) {
        if (level == null) return english ? "unknown" : "chưa xác định";
        return switch (level) {
            case SAFE -> english ? "safe" : "an toàn";
            case CAUTION -> english ? "worth watching" : "cần chú ý";
            case WARNING -> english ? "close to the limit" : "sắp chạm hạn mức";
            case EXCEEDED -> english ? "over the limit" : "đã vượt hạn mức";
        };
    }

    // ── Project status ───────────────────────────────────────────────────────

    /** Human label for a project status, e.g. {@code EXPIRED} → "đã hết hạn". */
    public static String projectStatus(ProjectStatus status, boolean english) {
        if (status == null) return english ? "unknown" : "chưa xác định";
        return switch (status) {
            case ACTIVE -> english ? "running" : "đang chạy";
            case COMPLETED -> english ? "completed" : "đã hoàn tất";
            case FROZEN -> english ? "paused" : "đang tạm dừng";
            case ABANDONED, CANCELLED -> english ? "cancelled" : "đã huỷ";
            case EXPIRED -> english ? "expired" : "đã hết hạn";
        };
    }

    /** Human label for a project's priority, e.g. {@code HIGH} → "ưu tiên cao". */
    public static String projectPriority(ProjectPriority priority, boolean english) {
        if (priority == null) return english ? "unset priority" : "chưa đặt mức ưu tiên";
        return switch (priority) {
            case HIGH -> english ? "high priority" : "ưu tiên cao";
            case MEDIUM -> english ? "medium priority" : "ưu tiên trung bình";
            case LOW -> english ? "low priority" : "ưu tiên thấp";
        };
    }

    // ── Cash-flow status ─────────────────────────────────────────────────────

    /** Human label for the month's cash-flow classification. */
    public static String cashFlow(UserFinancialDataService.CashFlowStatus status, boolean english) {
        if (status == null) return english ? "unknown" : "chưa xác định";
        return switch (status) {
            case SURPLUS -> english ? "in surplus" : "đang dư";
            case BALANCED -> english ? "balanced" : "cân bằng";
            case DEFICIT -> english ? "in deficit" : "đang thâm hụt";
            case UNKNOWN -> english ? "not yet determinable" : "chưa xác định được";
        };
    }

    // ── Transaction type ─────────────────────────────────────────────────────

    /** Human label for INCOME / EXPENSE. */
    public static String transactionType(TransactionType type, boolean english) {
        if (type == null) return english ? "transaction" : "giao dịch";
        return type == TransactionType.INCOME
                ? (english ? "income" : "khoản thu")
                : (english ? "expense" : "khoản chi");
    }
}
