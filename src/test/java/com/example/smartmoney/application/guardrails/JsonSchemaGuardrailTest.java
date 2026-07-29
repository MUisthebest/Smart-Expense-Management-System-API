package com.example.smartmoney.application.guardrails;

import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pure unit tests for {@link JsonSchemaGuardrail}.
 */
@DisplayName("JsonSchemaGuardrail")
class JsonSchemaGuardrailTest {

    private final JsonSchemaGuardrail guardrail = new JsonSchemaGuardrail(new ObjectMapper());

    @Test
    @DisplayName("parses well-formed JSON including relatedQuestions")
    void parsesWellFormedJson() {
        String raw = """
                {"intent":"GENERAL_ADVICE","reply":"Ban dang chi tieu on dinh.",
                 "relatedQuestions":["Cau hoi 1?","Cau hoi 2?","Cau hoi 3?"]}
                """;

        Supplier<String> retrySupplier = neverCalledRetry();
        var result = guardrail.validate(raw, retrySupplier);

        assertThat(result.intent()).isEqualTo(ChatResponse.ChatIntent.GENERAL_ADVICE);
        assertThat(result.reply()).isEqualTo("Ban dang chi tieu on dinh.");
        assertThat(result.relatedQuestions()).containsExactly("Cau hoi 1?", "Cau hoi 2?", "Cau hoi 3?");
        verify(retrySupplier, never()).get();
    }

    @Test
    @DisplayName("parses a complete transactionDraft alongside the usual fields")
    void parsesTransactionDraft() {
        String raw = """
                {"intent":"TRANSACTION_UPDATE","reply":"Ghi nhan 50.000 VND?",
                 "relatedQuestions":[],
                 "transactionDraft":{"operation":"CREATE","amount":50000,"type":"EXPENSE",
                   "category":"FOOD","description":"bun bo","date":"29/07/2026 12:00",
                   "complete":true}}
                """;

        var result = guardrail.validate(raw, neverCalledRetry());

        assertThat(result.intent()).isEqualTo(ChatResponse.ChatIntent.TRANSACTION_UPDATE);
        var draft = result.transactionDraft();
        assertThat(draft).isNotNull();
        assertThat(draft.operation()).isEqualTo("CREATE");
        assertThat(draft.amount()).isEqualByComparingTo(java.math.BigDecimal.valueOf(50000));
        assertThat(draft.category()).isEqualTo("FOOD");
        assertThat(draft.complete()).isTrue();
    }

    @Test
    @DisplayName("a missing or null transactionDraft leaves the field null, not an empty draft")
    void absentTransactionDraftIsNull() {
        assertThat(guardrail.validate(
                "{\"intent\":\"GENERAL_ADVICE\",\"reply\":\"Ok\"}", neverCalledRetry())
                .transactionDraft()).isNull();
        assertThat(guardrail.validate(
                "{\"intent\":\"GENERAL_ADVICE\",\"reply\":\"Ok\",\"transactionDraft\":null}",
                neverCalledRetry()).transactionDraft()).isNull();
    }

    @Test
    @DisplayName("a half-filled draft parses without failing the whole response")
    void partialTransactionDraftDoesNotInvalidateTheReply() {
        // The mid-collection turn is the common case: the reply itself ("what category?") is
        // perfectly good and must reach the user. Field-level validation belongs to
        // TransactionChangeService, not here.
        String raw = """
                {"intent":"TRANSACTION_UPDATE","reply":"Danh muc nao?",
                 "transactionDraft":{"operation":"CREATE","amount":50000,"complete":false}}
                """;

        var result = guardrail.validate(raw, neverCalledRetry());

        assertThat(result.intent()).isEqualTo(ChatResponse.ChatIntent.TRANSACTION_UPDATE);
        assertThat(result.reply()).isEqualTo("Danh muc nao?");
        var draft = result.transactionDraft();
        assertThat(draft).isNotNull();
        assertThat(draft.complete()).isFalse();
        assertThat(draft.category()).isNull();
        assertThat(draft.type()).isNull();
    }

    @Test
    @DisplayName("a garbage draft still yields a usable reply rather than triggering the fallback")
    void garbageTransactionDraftIsToleratedNotFatal() {
        String raw = """
                {"intent":"TRANSACTION_UPDATE","reply":"Ok",
                 "transactionDraft":{"operation":"TELEPORT","category":"COFFEE","complete":true}}
                """;

        var result = guardrail.validate(raw, neverCalledRetry());

        // Values are carried through verbatim; rejecting them is TransactionChangeService's job.
        assertThat(result.reply()).isEqualTo("Ok");
        assertThat(result.transactionDraft().operation()).isEqualTo("TELEPORT");
    }

    @Test
    @DisplayName("strips markdown fences before parsing")
    void stripsMarkdownFences() {
        String raw = "```json\n{\"intent\":\"BUDGET_UPDATE\",\"reply\":\"Ok\"}\n```";

        var result = guardrail.validate(raw, neverCalledRetry());

        assertThat(result.intent()).isEqualTo(ChatResponse.ChatIntent.BUDGET_UPDATE);
        assertThat(result.relatedQuestions()).isEmpty();
    }

    @Test
    @DisplayName("extracts the JSON object even when the model adds stray prose around it")
    void toleratesSurroundingProse() {
        String raw = "Chac chan roi, day la ket qua:\n" +
                "{\"intent\":\"GENERAL_ADVICE\",\"reply\":\"Ok\"}\n" +
                "Hy vong cau tra loi huu ich!";

        var result = guardrail.validate(raw, neverCalledRetry());

        assertThat(result.intent()).isEqualTo(ChatResponse.ChatIntent.GENERAL_ADVICE);
        assertThat(result.reply()).isEqualTo("Ok");
    }

    @Test
    @DisplayName("retries once on truncated JSON, then uses the retried response if valid")
    void retriesOnceAndUsesRetryResult() {
        String truncated = "{\"intent\":\"GENERAL_ADVICE\",\"reply\":\"Bi cat cu";
        String retried = "{\"intent\":\"SIMULATION\",\"reply\":\"Ket qua mo phong\"}";

        Supplier<String> retrySupplier = mock(Supplier.class);
        org.mockito.Mockito.when(retrySupplier.get()).thenReturn(retried);

        var result = guardrail.validate(truncated, retrySupplier);

        assertThat(result.intent()).isEqualTo(ChatResponse.ChatIntent.SIMULATION);
        verify(retrySupplier).get();
    }

    @Test
    @DisplayName("falls back to GENERAL_ADVICE when both attempts produce truncated JSON")
    void fallsBackWhenBothAttemptsFail() {
        String truncated = "{\"intent\":\"GENERAL_ADVICE\",\"reply\":\"Bi cat cu";

        var result = guardrail.validate(truncated, () -> truncated);

        assertThat(result.intent()).isEqualTo(ChatResponse.ChatIntent.GENERAL_ADVICE);
        assertThat(result.reply()).contains("Vui lòng thử lại sau");
        assertThat(result.relatedQuestions()).isEmpty();
    }

    @Test
    @DisplayName("falls back when intent is missing or not a valid enum value")
    void fallsBackOnInvalidIntent() {
        String raw = "{\"intent\":\"NOT_A_REAL_INTENT\",\"reply\":\"Ok\"}";

        var result = guardrail.validate(raw, () -> raw);

        assertThat(result.intent()).isEqualTo(ChatResponse.ChatIntent.GENERAL_ADVICE);
        assertThat(result.reply()).contains("Vui lòng thử lại sau");
    }

    private Supplier<String> neverCalledRetry() {
        Supplier<String> retrySupplier = mock(Supplier.class);
        // never invoked because the first parse succeeds
        return retrySupplier;
    }
}
