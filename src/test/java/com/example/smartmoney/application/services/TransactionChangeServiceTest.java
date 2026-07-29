package com.example.smartmoney.application.services;

import com.example.smartmoney.application.dtos.chat.PendingChatAction;
import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.guardrails.JsonSchemaGuardrail.TransactionDraft;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The backend half of the step-by-step transaction flow. The LLM accumulates fields and claims
 * {@code complete}; every test here is about what happens when that claim is wrong, malicious, or
 * merely premature — the service must produce no confirm card rather than a bad mutation.
 */
@DisplayName("TransactionChangeService")
class TransactionChangeServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 7, 29, 14, 30).atZone(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault());

    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final TransactionChangeService service =
            new TransactionChangeService(transactionRepository, FIXED_CLOCK);

    private final UUID userId = UUID.randomUUID();
    private final String message = "ghi chi 50000 ăn uống";

    // ── Helpers ──────────────────────────────────────────────────────────────

    private TransactionDraft draft(String operation, String txId, BigDecimal amount, String type,
                                   String category, String description, String date, boolean complete) {
        return new TransactionDraft(operation, txId, amount, type, category, description, date, complete);
    }

    private Transaction existingExpense(UUID id, UUID owner) {
        Transaction t = Transaction.create(owner, new Amount(BigDecimal.valueOf(120_000)),
                TransactionType.EXPENSE, Category.FOOD, "cơm trưa",
                LocalDateTime.of(2026, 7, 20, 12, 0));
        t.setId(id);
        return t;
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CREATE")
    class Create {

        @Test
        @DisplayName("a complete draft becomes a confirmable TRANSACTION_CREATE action")
        void completeDraftIsConfirmable() {
            var result = service.preview(userId, draft("CREATE", null, BigDecimal.valueOf(50_000),
                    "EXPENSE", "FOOD", "bún bò", "29/07/2026 12:00", true), message);

            assertThat(result).isPresent();
            ChatResponse.TransactionChangeSuggestion suggestion = result.get().suggestion();
            assertThat(suggestion.getOperation()).isEqualTo(ChatResponse.TransactionChangeOperation.CREATE);
            assertThat(suggestion.getNewAmount()).isEqualTo(50_000L);
            assertThat(suggestion.getNewCategory()).isEqualTo("FOOD");
            assertThat(suggestion.getTransactionId()).isNull();

            PendingChatAction action = result.get().action();
            assertThat(action.getType()).isEqualTo(PendingChatAction.Type.TRANSACTION_CREATE);
            assertThat(action.getTransactionAmount()).isEqualByComparingTo(BigDecimal.valueOf(50_000));
            assertThat(action.getTransactionType()).isEqualTo("EXPENSE");
            assertThat(action.getTransactionDate()).isEqualTo("29/07/2026 12:00");
        }

        @Test
        @DisplayName("complete=false yields nothing, even when every field happens to be filled in")
        void incompleteDraftIsNeverConfirmable() {
            // The assistant is still mid-collection (e.g. about to ask "is that right?"). Until
            // it says it's done, there is nothing to offer.
            var result = service.preview(userId, draft("CREATE", null, BigDecimal.valueOf(50_000),
                    "EXPENSE", "FOOD", null, "29/07/2026 12:00", false), message);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("a missing amount, type, or category yields nothing despite complete=true")
        void missingRequiredFieldsRejectedDespiteCompleteClaim() {
            assertThat(service.preview(userId, draft("CREATE", null, null,
                    "EXPENSE", "FOOD", null, null, true), message)).isEmpty();
            assertThat(service.preview(userId, draft("CREATE", null, BigDecimal.valueOf(50_000),
                    null, "FOOD", null, null, true), message)).isEmpty();
            assertThat(service.preview(userId, draft("CREATE", null, BigDecimal.valueOf(50_000),
                    "EXPENSE", null, null, null, true), message)).isEmpty();
        }

        @Test
        @DisplayName("an omitted date defaults to now instead of costing the user another turn")
        void omittedDateDefaultsToNow() {
            var result = service.preview(userId, draft("CREATE", null, BigDecimal.valueOf(50_000),
                    "EXPENSE", "FOOD", null, null, true), message);

            assertThat(result).isPresent();
            assertThat(result.get().action().getTransactionDate()).isEqualTo("29/07/2026 14:30");
        }

        @Test
        @DisplayName("an explicitly-given but unparseable date is a hard failure, not a fallback to now")
        void badDateIsNotSilentlyReplaced() {
            // Defaulting here would file the money into the wrong month behind the user's back.
            var result = service.preview(userId, draft("CREATE", null, BigDecimal.valueOf(50_000),
                    "EXPENSE", "FOOD", null, "2026-07-29", true), message);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("a category that's illegal for the type is rejected")
        void categoryMustMatchType() {
            // SALARY is an income category; pairing it with an EXPENSE would produce a row the
            // app's own create form could never have made.
            assertThat(service.preview(userId, draft("CREATE", null, BigDecimal.valueOf(50_000),
                    "EXPENSE", "SALARY", null, null, true), message)).isEmpty();
            assertThat(service.preview(userId, draft("CREATE", null, BigDecimal.valueOf(50_000),
                    "INCOME", "FOOD", null, null, true), message)).isEmpty();
        }

        @Test
        @DisplayName("the project-ledger categories can never be created through chat")
        void systemCategoriesRejected() {
            assertThat(service.preview(userId, draft("CREATE", null, BigDecimal.valueOf(50_000),
                    "EXPENSE", "PROJECT_CONTRIBUTION", null, null, true), message)).isEmpty();
            assertThat(service.preview(userId, draft("CREATE", null, BigDecimal.valueOf(50_000),
                    "INCOME", "PROJECT_REFUND", null, null, true), message)).isEmpty();
        }

        @Test
        @DisplayName("a zero or negative amount is rejected")
        void amountMustBePositive() {
            assertThat(service.preview(userId, draft("CREATE", null, BigDecimal.ZERO,
                    "EXPENSE", "FOOD", null, null, true), message)).isEmpty();
            assertThat(service.preview(userId, draft("CREATE", null, BigDecimal.valueOf(-5_000),
                    "EXPENSE", "FOOD", null, null, true), message)).isEmpty();
        }

        @Test
        @DisplayName("an invented category or type name is rejected rather than crashing")
        void unknownEnumNamesRejected() {
            assertThat(service.preview(userId, draft("CREATE", null, BigDecimal.valueOf(50_000),
                    "EXPENSE", "COFFEE", null, null, true), message)).isEmpty();
            assertThat(service.preview(userId, draft("CREATE", null, BigDecimal.valueOf(50_000),
                    "SPENDING", "FOOD", null, null, true), message)).isEmpty();
            assertThat(service.preview(userId, draft("MODIFY", null, BigDecimal.valueOf(50_000),
                    "EXPENSE", "FOOD", null, null, true), message)).isEmpty();
        }
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("UPDATE")
    class Update {

        @Test
        @DisplayName("a partial change carries only the changed fields and shows a before/after diff")
        void partialUpdateKeepsUntouchedFieldsNull() {
            UUID txId = UUID.randomUUID();
            when(transactionRepository.findById(txId)).thenReturn(Optional.of(existingExpense(txId, userId)));

            var result = service.preview(userId, draft("UPDATE", txId.toString(),
                    BigDecimal.valueOf(150_000), null, null, null, null, true), message);

            assertThat(result).isPresent();
            var suggestion = result.get().suggestion();
            assertThat(suggestion.getCurrentAmount()).isEqualTo(120_000L);
            assertThat(suggestion.getNewAmount()).isEqualTo(150_000L);
            assertThat(suggestion.getCurrentCategory()).isEqualTo("FOOD");
            assertThat(suggestion.getNewCategory()).isNull();

            PendingChatAction action = result.get().action();
            assertThat(action.getType()).isEqualTo(PendingChatAction.Type.TRANSACTION_UPDATE);
            assertThat(action.getTransactionId()).isEqualTo(txId.toString());
            // Null here is load-bearing: UpdateTransactionUseCase leaves null fields untouched,
            // so echoing the current values back would be harmless but writing a wrong one
            // wouldn't be — keep the action to exactly what the user asked to change.
            assertThat(action.getTransactionCategory()).isNull();
            assertThat(action.getTransactionType()).isNull();
            assertThat(action.getTransactionDate()).isNull();
        }

        @Test
        @DisplayName("an update that changes nothing is treated as an unfinished draft")
        void noOpUpdateRejected() {
            UUID txId = UUID.randomUUID();
            when(transactionRepository.findById(txId)).thenReturn(Optional.of(existingExpense(txId, userId)));

            var result = service.preview(userId,
                    draft("UPDATE", txId.toString(), null, null, null, null, null, true), message);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("another user's transaction is never offered, even with a valid id")
        void otherUsersTransactionRejected() {
            UUID txId = UUID.randomUUID();
            when(transactionRepository.findById(txId))
                    .thenReturn(Optional.of(existingExpense(txId, UUID.randomUUID())));

            var result = service.preview(userId, draft("UPDATE", txId.toString(),
                    BigDecimal.valueOf(150_000), null, null, null, null, true), message);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("a project-linked transaction is refused — editing it would desync project progress")
        void projectLinkedTransactionRejected() {
            UUID txId = UUID.randomUUID();
            Transaction contribution = Transaction.create(userId, new Amount(BigDecimal.valueOf(2_000_000)),
                    TransactionType.EXPENSE, Category.PROJECT_CONTRIBUTION, "góp dự án",
                    LocalDateTime.of(2026, 7, 10, 9, 0), UUID.randomUUID());
            contribution.setId(txId);
            when(transactionRepository.findById(txId)).thenReturn(Optional.of(contribution));

            var result = service.preview(userId, draft("UPDATE", txId.toString(),
                    BigDecimal.valueOf(1_000_000), null, null, null, null, true), message);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("changing only the type is rejected when it strands an now-illegal category")
        void postChangeCombinationIsValidated() {
            UUID txId = UUID.randomUUID();
            when(transactionRepository.findById(txId)).thenReturn(Optional.of(existingExpense(txId, userId)));

            // FOOD stays on the row, so flipping it to INCOME alone would leave an INCOME
            // transaction categorised FOOD — a state the create path forbids outright.
            var result = service.preview(userId,
                    draft("UPDATE", txId.toString(), null, "INCOME", null, null, null, true), message);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("changing type and category together to a valid income pair is allowed")
        void validTypeAndCategoryChangeTogether() {
            UUID txId = UUID.randomUUID();
            when(transactionRepository.findById(txId)).thenReturn(Optional.of(existingExpense(txId, userId)));

            var result = service.preview(userId,
                    draft("UPDATE", txId.toString(), null, "INCOME", "SALARY", null, null, true), message);

            assertThat(result).isPresent();
            assertThat(result.get().action().getTransactionType()).isEqualTo("INCOME");
            assertThat(result.get().action().getTransactionCategory()).isEqualTo("SALARY");
        }

        @Test
        @DisplayName("a missing, malformed, or unknown transaction id yields nothing")
        void badTransactionIdRejected() {
            UUID unknown = UUID.randomUUID();
            when(transactionRepository.findById(any())).thenReturn(Optional.empty());

            assertThat(service.preview(userId, draft("UPDATE", null,
                    BigDecimal.valueOf(1_000), null, null, null, null, true), message)).isEmpty();
            assertThat(service.preview(userId, draft("UPDATE", "not-a-uuid",
                    BigDecimal.valueOf(1_000), null, null, null, null, true), message)).isEmpty();
            assertThat(service.preview(userId, draft("UPDATE", unknown.toString(),
                    BigDecimal.valueOf(1_000), null, null, null, null, true), message)).isEmpty();
        }
    }

    // ── DELETE ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE")
    class Delete {

        @Test
        @DisplayName("an owned transaction becomes a confirmable TRANSACTION_DELETE action")
        void ownedTransactionIsDeletable() {
            UUID txId = UUID.randomUUID();
            when(transactionRepository.findById(txId)).thenReturn(Optional.of(existingExpense(txId, userId)));

            var result = service.preview(userId,
                    draft("DELETE", txId.toString(), null, null, null, null, null, true), "xóa giao dịch này");

            assertThat(result).isPresent();
            var suggestion = result.get().suggestion();
            assertThat(suggestion.getOperation()).isEqualTo(ChatResponse.TransactionChangeOperation.DELETE);
            // The card has to show what's about to disappear — there's no undo behind it.
            assertThat(suggestion.getCurrentAmount()).isEqualTo(120_000L);
            assertThat(suggestion.getCurrentCategory()).isEqualTo("FOOD");
            assertThat(suggestion.getCurrentDate()).isEqualTo("20/07/2026 12:00");
            assertThat(suggestion.getNewAmount()).isNull();

            assertThat(result.get().action().getType()).isEqualTo(PendingChatAction.Type.TRANSACTION_DELETE);
            assertThat(result.get().action().getTransactionId()).isEqualTo(txId.toString());
        }

        @Test
        @DisplayName("another user's transaction is never deletable")
        void otherUsersTransactionNotDeletable() {
            UUID txId = UUID.randomUUID();
            when(transactionRepository.findById(txId))
                    .thenReturn(Optional.of(existingExpense(txId, UUID.randomUUID())));

            assertThat(service.preview(userId,
                    draft("DELETE", txId.toString(), null, null, null, null, null, true),
                    "xóa giao dịch này")).isEmpty();
        }
    }

    @Test
    @DisplayName("a null draft (every non-transaction turn) yields nothing")
    void nullDraftYieldsNothing() {
        assertThat(service.preview(userId, null, message)).isEmpty();
    }

    @Test
    @DisplayName("reason text follows the message language")
    void reasonLanguageFollowsMessage() {
        var vi = service.preview(userId, draft("CREATE", null, BigDecimal.valueOf(50_000),
                "EXPENSE", "FOOD", null, null, true), "ghi nhận khoản chi 50000 cho ăn uống");
        var en = service.preview(userId, draft("CREATE", null, BigDecimal.valueOf(50_000),
                "EXPENSE", "FOOD", null, null, true), "record a 50000 food expense for me please");

        assertThat(vi).isPresent();
        assertThat(en).isPresent();
        assertThat(vi.get().suggestion().getReason()).contains("Ghi nhận khoản chi");
        assertThat(en.get().suggestion().getReason()).contains("Record a new expense");
    }

    @Test
    @DisplayName("an over-long description is truncated to the DTO's 500-char limit, not rejected")
    void descriptionTruncatedNotRejected() {
        // The 500-char cap is a @Size validator on the DTO, which never runs on this path since
        // the use case is called directly. Truncating keeps a wordy entry usable instead of
        // dead-ending the flow the user just spent several turns on.
        String tooLong = "x".repeat(600);
        var result = service.preview(userId, draft("CREATE", null, BigDecimal.valueOf(50_000),
                "EXPENSE", "FOOD", tooLong, null, true), message);

        assertThat(result).isPresent();
        assertThat(result.get().action().getTransactionDescription()).hasSize(500);
    }
}
