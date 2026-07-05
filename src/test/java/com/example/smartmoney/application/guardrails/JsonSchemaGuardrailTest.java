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
