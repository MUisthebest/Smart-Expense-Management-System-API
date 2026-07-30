package com.example.smartmoney.application.guardrails;

import com.example.smartmoney.application.dtos.requests.chat.ChatRequest;
import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.usecases.chat.ResetConversationUseCase;
import com.example.smartmoney.application.usecases.chat.ResolvePlainAnswerUseCase;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the bug where a bare "có"/"yes" answering a pending suggestion got
 * rejected as OUT_OF_SCOPE: the deterministic plain-answer gate ({@link ResolvePlainAnswerUseCase})
 * used to live inside {@code ChatWithAIUseCase.execute()}'s body, but that method is wrapped by
 * this aspect's {@code @Around} advice — the guardrail chain (including the context-blind
 * SemanticScopeGuardrail LLM classifier) always ran to completion BEFORE the method body, so it
 * could throw before the plain-answer check ever ran. These tests verify the gate now runs here,
 * in the aspect, before any guardrail — and that it short-circuits without ever calling
 * {@code pjp.proceed()}.
 */
@DisplayName("InputGuardrailAspect")
class InputGuardrailAspectTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ResolvePlainAnswerUseCase resolvePlainAnswerUseCase = mock(ResolvePlainAnswerUseCase.class);
    private final ResetConversationUseCase resetConversationUseCase = mock(ResetConversationUseCase.class);
    private final PromptGuardrail guardrail = mock(PromptGuardrail.class);

    @SuppressWarnings("unchecked")
    private InputGuardrailAspect newAspect() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
        // Default: not a reset request, so existing cases exercise the paths after that gate.
        when(resetConversationUseCase.tryReset(any(), any())).thenReturn(Optional.empty());
        return new InputGuardrailAspect(
                List.of(guardrail), redisTemplate, resolvePlainAnswerUseCase, resetConversationUseCase);
    }

    private ProceedingJoinPoint mockJoinPoint(Object[] args, Class<?> returnType) {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(pjp.getSignature()).thenReturn(signature);
        doReturn(returnType).when(signature).getReturnType();
        when(pjp.getArgs()).thenReturn(args);
        return pjp;
    }

    @Test
    @DisplayName("a bare yes/no resolving a pending action bypasses the content guardrails entirely")
    void plainAnswerBypassesGuardrails() throws Throwable {
        UUID userId = UUID.randomUUID();
        ChatRequest request = ChatRequest.builder().message("có").build();
        ChatResponse resolved = ChatResponse.builder().reply("Đã cập nhật thành công.")
                .intent(ChatResponse.ChatIntent.GENERAL_ADVICE).build();
        when(resolvePlainAnswerUseCase.tryResolve(userId, "có")).thenReturn(Optional.of(resolved));
        // Proves the bypass actually skips this: if it didn't, the exception below would surface
        // instead of the resolved response.
        doThrow(new GuardrailViolationException("should never be reached"))
                .when(guardrail).validate(any(), any());

        InputGuardrailAspect aspect = newAspect();
        ProceedingJoinPoint pjp = mockJoinPoint(new Object[]{request, userId}, ChatResponse.class);

        Object result = aspect.intercept(pjp);

        assertThat(result).isSameAs(resolved);
        verify(guardrail, never()).validate(any(), any());
        verify(pjp, never()).proceed();
    }

    @Test
    @DisplayName("a normal message (no plain-answer match) still runs the guardrail chain and proceeds")
    void normalMessageStillRunsGuardrails() throws Throwable {
        UUID userId = UUID.randomUUID();
        ChatRequest request = ChatRequest.builder().message("ngân sách tháng này của tôi thế nào?").build();
        when(resolvePlainAnswerUseCase.tryResolve(userId, request.getMessage())).thenReturn(Optional.empty());

        InputGuardrailAspect aspect = newAspect();
        ProceedingJoinPoint pjp = mockJoinPoint(new Object[]{request, userId}, ChatResponse.class);
        when(pjp.proceed()).thenReturn("proceeded");

        Object result = aspect.intercept(pjp);

        assertThat(result).isEqualTo("proceeded");
        verify(guardrail).validate(any(), any());
    }

    @Test
    @DisplayName("the plain-answer bypass is skipped for methods that don't return ChatResponse (e.g. streamChat)")
    void bypassSkippedForNonChatResponseMethods() throws Throwable {
        UUID userId = UUID.randomUUID();
        ChatRequest request = ChatRequest.builder().message("có").build();

        InputGuardrailAspect aspect = newAspect();
        ProceedingJoinPoint pjp = mockJoinPoint(new Object[]{request, userId}, void.class);
        when(pjp.proceed()).thenReturn(null);

        aspect.intercept(pjp);

        verifyNoInteractions(resolvePlainAnswerUseCase);
        verify(guardrail).validate(any(), any());
    }
}
