package com.example.smartmoney.application.services;

import com.example.smartmoney.application.dtos.chat.PendingChatAction;
import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.guardrails.JsonSchemaGuardrail;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Turns the assistant's step-by-step transaction draft into a confirmable change.
 *
 * <p>Read-only preview service, mirroring {@link ProjectHypothesisService}'s role for projects:
 * it never mutates anything. It answers one question — "is this draft complete and legal enough
 * to offer the user a confirm card?" — and, when the answer is yes, returns both the card payload
 * ({@link ChatResponse.TransactionChangeSuggestion}) and the real mutation
 * ({@link PendingChatAction}) that {@code ConfirmChatActionUseCase} will run if the user agrees.
 *
 * <p><b>Why the LLM does the slot-filling and this class does the validating.</b> Collecting
 * "amount → type → category → date" over several turns needs conversational memory, which the
 * model already has (the history block is in every prompt) and which a Java regex parser would
 * have to reimplement badly across two languages. So the model accumulates the fields and claims
 * {@code complete} when it thinks nothing is missing. That claim is treated as a hint, never as
 * permission: everything below is re-derived from the draft and the database. A draft that is
 * incomplete, names a category that doesn't exist, uses a category that's illegal for its type,
 * carries an unparseable date, or points at a transaction the user doesn't own yields
 * {@link Optional#empty()} — no card, no pending action — and the assistant simply keeps asking.
 *
 * <p><b>Project-linked transactions are off limits.</b> {@code PROJECT_CONTRIBUTION} and
 * {@code PROJECT_REFUND} rows are written by the project machinery
 * ({@code AddProjectContributionUseCase} and the abandon-project refund path) and are the ledger
 * that {@code Project.totalContributed()} reads. Editing or deleting one through the generic
 * transaction use cases would move the ledger without moving the project, silently desyncing a
 * project's visible progress from its contributions — so this service refuses to target them at
 * all, and refuses to create new ones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionChangeService {

    /** Same format {@code CreateTransactionRequest}/{@code UpdateTransactionRequest} validate and
     *  the transaction use cases parse — kept identical so a draft that passes here can't fail
     *  later at execution time on formatting alone. */
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final int MAX_DESCRIPTION_LENGTH = 500;

    /** Categories a user may legitimately put on a manually-entered EXPENSE. */
    private static final Set<Category> EXPENSE_CATEGORIES = EnumSet.of(
            Category.FOOD, Category.TRANSPORTATION, Category.CLOTHING, Category.UTILITIES,
            Category.ENTERTAINMENT, Category.HEALTH, Category.EDUCATION, Category.SHOPPING,
            Category.OTHER);

    /** Categories a user may legitimately put on a manually-entered INCOME. */
    private static final Set<Category> INCOME_CATEGORIES = EnumSet.of(
            Category.SALARY, Category.BONUS, Category.INVESTMENT, Category.GIFT, Category.OTHER);

    /** System-generated categories the chat may never create, edit, or delete — see class doc. */
    private static final Set<Category> SYSTEM_CATEGORIES = EnumSet.of(
            Category.PROJECT_CONTRIBUTION, Category.PROJECT_REFUND);

    private final TransactionRepository transactionRepository;
    private final Clock clock;

    /** A validated, ready-to-confirm transaction change: what to show, and what to run on "yes". */
    public record Preview(ChatResponse.TransactionChangeSuggestion suggestion,
                          PendingChatAction action) {}

    /**
     * Validates a draft and builds its preview.
     *
     * @param draft     the assistant's accumulated draft; null/incomplete yields empty
     * @param messageLow the lowercased user message, used only to pick the reason's language
     * @return the preview, or empty when the draft isn't yet complete/legal enough to confirm
     */
    public Optional<Preview> preview(UUID userId, JsonSchemaGuardrail.TransactionDraft draft,
                                     String messageLow) {
        if (draft == null || !draft.complete()) return Optional.empty();

        ChatResponse.TransactionChangeOperation operation = parseOperation(draft.operation());
        if (operation == null) return Optional.empty();

        boolean english = MessageLanguageDetector.isEnglish(messageLow);
        try {
            return switch (operation) {
                case CREATE -> previewCreate(draft, english);
                case UPDATE -> previewUpdate(userId, draft, english);
                case DELETE -> previewDelete(userId, draft, english);
            };
        } catch (Exception e) {
            // Any lookup/parse failure means "not confirmable yet", never a failed chat turn —
            // the reply itself is already in hand by the time this runs.
            log.warn("TransactionChangeService: could not preview {} for user {}: {}",
                    operation, userId, e.getMessage());
            return Optional.empty();
        }
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    private Optional<Preview> previewCreate(JsonSchemaGuardrail.TransactionDraft draft, boolean english) {
        BigDecimal amount = draft.amount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        TransactionType type = parseEnum(TransactionType.class, draft.type());
        if (type == null) return Optional.empty();

        Category category = parseEnum(Category.class, draft.category());
        if (!isCategoryLegal(category, type)) return Optional.empty();

        // Date is the one field allowed to default: an omitted date on a "record this spend"
        // request overwhelmingly means "just now", and asking for it would add a step to every
        // single entry. An explicitly-given but unparseable date is still a hard failure —
        // guessing at a date the user did state would put money in the wrong month.
        LocalDateTime date;
        if (draft.date() == null) {
            date = LocalDateTime.now(clock);
        } else {
            date = parseDate(draft.date());
            if (date == null) return Optional.empty();
        }
        String formattedDate = DATE_FORMATTER.format(date);
        String description = trimDescription(draft.description());

        var suggestion = ChatResponse.TransactionChangeSuggestion.builder()
                .operation(ChatResponse.TransactionChangeOperation.CREATE)
                .newAmount(amount.longValue())
                .newType(type.name())
                .newCategory(category.name())
                .newDescription(description)
                .newDate(formattedDate)
                .reason(english
                        ? String.format("Record a new %s of %,d VND in %s on %s.",
                        type == TransactionType.EXPENSE ? "expense" : "income",
                        amount.longValue(), category.name(), formattedDate)
                        : String.format("Ghi nhận khoản %s %,d VND thuộc danh mục %s vào %s.",
                        type == TransactionType.EXPENSE ? "chi" : "thu",
                        amount.longValue(), category.name(), formattedDate))
                .build();

        var action = PendingChatAction.builder()
                .type(PendingChatAction.Type.TRANSACTION_CREATE)
                .transactionAmount(amount)
                .transactionType(type.name())
                .transactionCategory(category.name())
                .transactionDescription(description)
                .transactionDate(formattedDate)
                .build();

        return Optional.of(new Preview(suggestion, action));
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    private Optional<Preview> previewUpdate(UUID userId, JsonSchemaGuardrail.TransactionDraft draft,
                                            boolean english) {
        Transaction existing = loadEditableTransaction(userId, draft.transactionId());
        if (existing == null) return Optional.empty();

        BigDecimal newAmount = draft.amount();
        if (newAmount != null && newAmount.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        TransactionType newType = parseEnum(TransactionType.class, draft.type());
        if (draft.type() != null && newType == null) return Optional.empty();

        Category newCategory = parseEnum(Category.class, draft.category());
        if (draft.category() != null && newCategory == null) return Optional.empty();

        LocalDateTime newDate = null;
        if (draft.date() != null) {
            newDate = parseDate(draft.date());
            if (newDate == null) return Optional.empty();
        }
        String newDescription = trimDescription(draft.description());

        // Nothing actually changing — an "update" that updates nothing is a draft the assistant
        // hasn't finished collecting, not a confirmable action.
        if (newAmount == null && newType == null && newCategory == null
                && newDescription == null && newDate == null) {
            return Optional.empty();
        }

        // Validate the POST-change combination, not just the supplied fields: changing only the
        // type (EXPENSE → INCOME) while keeping an expense-only category, or changing only the
        // category to an income one while the row stays an EXPENSE, would both leave the ledger
        // in a state the create path would have rejected outright.
        TransactionType effectiveType = newType != null ? newType : existing.getType();
        Category effectiveCategory = newCategory != null ? newCategory : existing.getCategory();
        if (!isCategoryLegal(effectiveCategory, effectiveType)) return Optional.empty();

        String currentDate = existing.getDate() != null ? DATE_FORMATTER.format(existing.getDate()) : null;
        String formattedNewDate = newDate != null ? DATE_FORMATTER.format(newDate) : null;
        Long currentAmount = existing.getAmount() != null && existing.getAmount().getValue() != null
                ? existing.getAmount().getValue().longValue() : null;

        var suggestion = ChatResponse.TransactionChangeSuggestion.builder()
                .transactionId(existing.getId().toString())
                .operation(ChatResponse.TransactionChangeOperation.UPDATE)
                .currentAmount(currentAmount)
                .newAmount(newAmount != null ? newAmount.longValue() : null)
                .currentType(existing.getType() != null ? existing.getType().name() : null)
                .newType(newType != null ? newType.name() : null)
                .currentCategory(existing.getCategory() != null ? existing.getCategory().name() : null)
                .newCategory(newCategory != null ? newCategory.name() : null)
                .currentDescription(existing.getDescription())
                .newDescription(newDescription)
                .currentDate(currentDate)
                .newDate(formattedNewDate)
                .reason(english
                        ? String.format("Update the %s of %,d VND recorded on %s.",
                        describeType(existing.getType(), true), currentAmount == null ? 0L : currentAmount,
                        currentDate)
                        : String.format("Cập nhật khoản %s %,d VND ghi ngày %s.",
                        describeType(existing.getType(), false), currentAmount == null ? 0L : currentAmount,
                        currentDate))
                .build();

        var action = PendingChatAction.builder()
                .type(PendingChatAction.Type.TRANSACTION_UPDATE)
                .transactionId(existing.getId().toString())
                .transactionAmount(newAmount)
                .transactionType(newType != null ? newType.name() : null)
                .transactionCategory(newCategory != null ? newCategory.name() : null)
                .transactionDescription(newDescription)
                .transactionDate(formattedNewDate)
                .build();

        return Optional.of(new Preview(suggestion, action));
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    private Optional<Preview> previewDelete(UUID userId, JsonSchemaGuardrail.TransactionDraft draft,
                                            boolean english) {
        Transaction existing = loadEditableTransaction(userId, draft.transactionId());
        if (existing == null) return Optional.empty();

        String currentDate = existing.getDate() != null ? DATE_FORMATTER.format(existing.getDate()) : null;
        Long currentAmount = existing.getAmount() != null && existing.getAmount().getValue() != null
                ? existing.getAmount().getValue().longValue() : null;

        var suggestion = ChatResponse.TransactionChangeSuggestion.builder()
                .transactionId(existing.getId().toString())
                .operation(ChatResponse.TransactionChangeOperation.DELETE)
                .currentAmount(currentAmount)
                .currentType(existing.getType() != null ? existing.getType().name() : null)
                .currentCategory(existing.getCategory() != null ? existing.getCategory().name() : null)
                .currentDescription(existing.getDescription())
                .currentDate(currentDate)
                .reason(english
                        ? String.format("Delete the %s of %,d VND in %s recorded on %s.",
                        describeType(existing.getType(), true), currentAmount == null ? 0L : currentAmount,
                        existing.getCategory(), currentDate)
                        : String.format("Xóa khoản %s %,d VND thuộc danh mục %s ghi ngày %s.",
                        describeType(existing.getType(), false), currentAmount == null ? 0L : currentAmount,
                        existing.getCategory(), currentDate))
                .build();

        var action = PendingChatAction.builder()
                .type(PendingChatAction.Type.TRANSACTION_DELETE)
                .transactionId(existing.getId().toString())
                .build();

        return Optional.of(new Preview(suggestion, action));
    }

    // ── Shared validation ─────────────────────────────────────────────────────

    /**
     * Loads a transaction the chat is allowed to edit or delete: it must exist, belong to this
     * user, and not be part of the project ledger (see the class doc). Ownership is re-checked
     * here even though the downstream use cases check it too — the point is to never OFFER a card
     * for someone else's row, not just to fail when confirming one.
     *
     * @return the transaction, or null when it can't be found or mustn't be touched
     */
    private Transaction loadEditableTransaction(UUID userId, String transactionId) {
        if (transactionId == null) return null;
        UUID id;
        try {
            id = UUID.fromString(transactionId);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
        Transaction transaction = transactionRepository.findById(id).orElse(null);
        if (transaction == null) return null;
        if (!userId.equals(transaction.getUserId())) return null;
        if (transaction.getProjectId() != null) return null;
        if (transaction.getCategory() != null && SYSTEM_CATEGORIES.contains(transaction.getCategory())) return null;
        return transaction;
    }

    /** A category is legal when it exists, isn't system-generated, and matches the row's type. */
    private boolean isCategoryLegal(Category category, TransactionType type) {
        if (category == null || type == null) return false;
        if (SYSTEM_CATEGORIES.contains(category)) return false;
        return type == TransactionType.EXPENSE
                ? EXPENSE_CATEGORIES.contains(category)
                : INCOME_CATEGORIES.contains(category);
    }

    private ChatResponse.TransactionChangeOperation parseOperation(String raw) {
        return parseEnum(ChatResponse.TransactionChangeOperation.class, raw);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String raw) {
        if (raw == null) return null;
        try {
            return Enum.valueOf(enumType, raw.trim().toUpperCase());
        } catch (IllegalArgumentException unknownValue) {
            return null;
        }
    }

    private LocalDateTime parseDate(String raw) {
        try {
            return LocalDateTime.parse(raw.trim(), DATE_FORMATTER);
        } catch (Exception e) {
            return null;
        }
    }

    private String trimDescription(String description) {
        if (description == null) return null;
        String trimmed = description.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() <= MAX_DESCRIPTION_LENGTH
                ? trimmed : trimmed.substring(0, MAX_DESCRIPTION_LENGTH);
    }

    private String describeType(TransactionType type, boolean english) {
        if (english) return type == TransactionType.INCOME ? "income" : "expense";
        return type == TransactionType.INCOME ? "thu" : "chi";
    }
}
