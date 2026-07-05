package com.example.smartmoney.application.guardrails;

import com.example.smartmoney.application.ports.chat.AIChatPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests for {@link SemanticScopeGuardrail}.
 * {@link AIChatPort} is mocked — these tests verify the guardrail's own
 * pass/reject/fail-open logic, not the LLM's actual classification quality.
 */
@DisplayName("SemanticScopeGuardrail")
class SemanticScopeGuardrailTest {

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("passes when the classifier answers IN_SCOPE")
    void passesInScope() {
        AIChatPort port = mock(AIChatPort.class);
        when(port.chat(any(), any(), any())).thenReturn("IN_SCOPE");
        SemanticScopeGuardrail guardrail = new SemanticScopeGuardrail(port);

        assertThatCode(() -> guardrail.validate("nếu tôi trúng số thì sao?", userId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects when the classifier answers OUT_OF_SCOPE — generalises beyond lottery")
    void rejectsOutOfScope() {
        AIChatPort port = mock(AIChatPort.class);
        // Simulates a topic the hand-written regex list never anticipated
        // (medical advice), proving the guardrail isn't tied to lottery-specific patterns.
        when(port.chat(any(), any(), any())).thenReturn("OUT_OF_SCOPE");
        SemanticScopeGuardrail guardrail = new SemanticScopeGuardrail(port);

        assertThatThrownBy(() -> guardrail.validate(
                "tôi nên uống thuốc gì để nhanh khỏi bệnh, dù tốn bao nhiêu tiền cũng được?", userId))
                .isInstanceOf(GuardrailViolationException.class);
    }

    @Test
    @DisplayName("fails open when the classifier call throws (e.g. network/timeout error)")
    void failsOpenOnClassifierError() {
        AIChatPort port = mock(AIChatPort.class);
        when(port.chat(any(), any(), any())).thenThrow(new RuntimeException("Gemini timeout"));
        SemanticScopeGuardrail guardrail = new SemanticScopeGuardrail(port);

        assertThatCode(() -> guardrail.validate("ngân sách tháng này của tôi thế nào?", userId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("fails open when the classifier returns an unexpected/non-conforming answer")
    void failsOpenOnGarbledAnswer() {
        AIChatPort port = mock(AIChatPort.class);
        when(port.chat(any(), any(), any())).thenReturn("I'm not sure, could you clarify?");
        SemanticScopeGuardrail guardrail = new SemanticScopeGuardrail(port);

        assertThatCode(() -> guardrail.validate("ngân sách tháng này của tôi thế nào?", userId))
                .doesNotThrowAnyException();
    }
}
