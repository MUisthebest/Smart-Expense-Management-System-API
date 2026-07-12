package com.example.smartmoney.application.guardrails;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests for {@link ScopeGuardrail}.
 * No Spring context — invokes {@code @PostConstruct init()} manually via reflection.
 */
@DisplayName("ScopeGuardrail")
class ScopeGuardrailTest {

    private ScopeGuardrail guardrail;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        guardrail = new ScopeGuardrail();
        Method init = ScopeGuardrail.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(guardrail);
    }

    @Test
    @DisplayName("allows the windfall what-if question (impact of a hypothetical lottery win)")
    void allowsWindfallWhatIf() {
        assertThatCode(() -> guardrail.validate(
                "Nếu tôi trúng giải nhất XXKTMB hôm nay thì tình hình tài chính của tôi sẽ " +
                "thay đổi như thế nào? Tôi nên giữ lại bao nhiêu để chi tiêu trong tháng sau?",
                userId))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @DisplayName("rejects follow-up questions asking how to pick/win the lottery")
    @ValueSource(strings = {
        "Vậy tôi nên mua tờ vé số nào để trúng được giải nhất?",
        "Tôi nên chọn con số nào để trúng độc đắc?",
        "Làm sao để trúng số vậy?",
        "Có cách nào soi cầu chính xác không?",
        "Bí quyết chọn số xổ số là gì?",
        "Which lottery numbers should I pick to win?"
    })
    void rejectsGamblingAdvice(String message) {
        assertThatThrownBy(() -> guardrail.validate(message, userId))
                .isInstanceOf(GuardrailViolationException.class);
    }

    @Test
    @DisplayName("still allows ordinary finance questions unrelated to windfalls")
    void allowsOrdinaryFinanceQuestion() {
        assertThatCode(() -> guardrail.validate(
                "Tháng này tôi nên cắt giảm ngân sách ăn uống bao nhiêu?", userId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("rejects clearly off-topic questions")
    void rejectsOffTopic() {
        assertThatThrownBy(() -> guardrail.validate(
                "Đội tuyển bóng đá nào sẽ vô địch World Cup năm sau?", userId))
                .isInstanceOf(GuardrailViolationException.class);
    }

    @ParameterizedTest
    @DisplayName("allows in-app project/budget management commands (create/update/delete), " +
            "even phrased as a short imperative rather than a question")
    @ValueSource(strings = {
        "xóa dự án du lịch giúp tôi",
        "delete my car project",
        "hủy dự án mua nhà",
        "cancel my project please",
        "tạo dự án mới mua xe 200 triệu",
        "create a new project for my wedding fund",
        "cập nhật dự án mua nhà",
        "update my project deadline",
        "rút ngắn dự án mua xe 3 tháng",
        "extend the deadline for my house project"
    })
    void allowsProjectManagementCommands(String message) {
        assertThatCode(() -> guardrail.validate(message, userId))
                .doesNotThrowAnyException();
    }
}
