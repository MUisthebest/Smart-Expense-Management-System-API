package com.example.smartmoney.application.guardrails;

import com.example.smartmoney.application.dtos.requests.chat.ChatRequest;
import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.usecases.chat.ResetConversationUseCase;
import com.example.smartmoney.application.usecases.chat.ResolvePlainAnswerUseCase;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * AOP aspect that intercepts every {@link InputGuardrail}-annotated method.
 *
 * Execution order:
 *  1. Rate limit check (Bucket4j-style via Redis INCR, 20 req/user/min) → 429
 *  2. Deterministic conversation-reset gate ({@link ResetConversationUseCase}) — "hãy xóa đoạn
 *     hội thoại" wipes the Redis window. Runs before the content guardrails for the same reason as
 *     step 3, and before step 3 specifically so a reset always wins over a stale pending action:
 *     the user asked to drop the context, not to answer a question inside it.
 *  3. Deterministic plain yes/no confirm/deny gate ({@link ResolvePlainAnswerUseCase}) — only for
 *     methods returning {@link ChatResponse} (the sync /chat endpoint). MUST run before the
 *     content guardrails below: a bare "yes"/"no"/"có"/"không" answering a pending suggestion has
 *     no financial content for them to evaluate, and SemanticScopeGuardrail's LLM classifier (no
 *     conversation context) reliably misreads an isolated "có" as off-topic — see
 *     ResolvePlainAnswerUseCase's class doc for the bug this caused when this check used to live
 *     inside the guarded method body instead of here.
 *  4. ScopeGuardrail @Order(1) — cheap regex, known domains/patterns
 *  5. PromptInjectionGuardrail @Order(2) — cheap regex, jailbreak/injection patterns
 *  6. SemanticScopeGuardrail @Order(3) — LLM classifier, general-purpose scope net
 *  Violation → GuardrailViolationException → controller maps to OUT_OF_SCOPE.
 */
@Slf4j
@Aspect
@Component
public class InputGuardrailAspect {

    private static final int MAX_REQUESTS_PER_MINUTE = 20;
    private static final String RATE_KEY_PREFIX = "rate:chat:";

    private final List<PromptGuardrail> guardrails;
    private final StringRedisTemplate redisTemplate;
    private final ResolvePlainAnswerUseCase resolvePlainAnswerUseCase;
    private final ResetConversationUseCase resetConversationUseCase;

    public InputGuardrailAspect(
            List<PromptGuardrail> guardrails,
            @Qualifier("aiStringRedisTemplate") StringRedisTemplate redisTemplate,
            ResolvePlainAnswerUseCase resolvePlainAnswerUseCase,
            ResetConversationUseCase resetConversationUseCase) {
        this.guardrails = guardrails.stream()
                .sorted(AnnotationAwareOrderComparator.INSTANCE)
                .toList();
        this.redisTemplate = redisTemplate;
        this.resolvePlainAnswerUseCase = resolvePlainAnswerUseCase;
        this.resetConversationUseCase = resetConversationUseCase;
    }

    @Around("@annotation(com.example.smartmoney.application.guardrails.InputGuardrail)")
    public Object intercept(ProceedingJoinPoint pjp) throws Throwable {
        Object[] args = pjp.getArgs();

        ChatRequest request = extractChatRequest(args);
        UUID userId = extractUserId(args);

        if (request == null || userId == null) {
            return pjp.proceed();
        }

        // 1. Rate limit — gate before pattern scan (cheapest check first)
        checkRateLimit(userId);

        // 2. Deterministic conversation-reset gate — checked before the yes/no gate so "xóa đoạn
        // hội thoại" can never be swallowed as an answer to a leftover pending action.
        if (returnsChatResponse(pjp)) {
            Optional<ChatResponse> reset =
                    resetConversationUseCase.tryReset(userId, request.getMessage());
            if (reset.isPresent()) return reset.get();
        }

        // 3. Deterministic plain yes/no confirm/deny gate — short-circuits entirely on a match,
        // skipping both the content guardrails and the guarded method body.
        if (returnsChatResponse(pjp)) {
            Optional<ChatResponse> plainAnswer =
                    resolvePlainAnswerUseCase.tryResolve(userId, request.getMessage());
            if (plainAnswer.isPresent()) return plainAnswer.get();
        }

        // 4. Run guardrails in @Order sequence
        String message = request.getMessage() != null ? request.getMessage() : "";
        for (PromptGuardrail guardrail : guardrails) {
            guardrail.validate(message.toLowerCase(), userId);
        }

        return pjp.proceed();
    }

    private boolean returnsChatResponse(ProceedingJoinPoint pjp) {
        return pjp.getSignature() instanceof MethodSignature sig
                && ChatResponse.class.equals(sig.getReturnType());
    }

    private void checkRateLimit(UUID userId) {
        long minuteBucket = System.currentTimeMillis() / 60_000;
        String key = RATE_KEY_PREFIX + userId + ":" + minuteBucket;
        Long count;
        try {
            count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                // Set TTL on first increment so the key auto-expires after the window
                redisTemplate.expire(key, Duration.ofSeconds(65));
            }
        } catch (Exception e) {
            // Redis unavailable/timed out — fail open rather than blocking chat entirely.
            log.warn("Rate limit check failed for user {}, allowing request: {}", userId, e.getMessage());
            return;
        }
        if (count != null && count > MAX_REQUESTS_PER_MINUTE) {
            throw new RateLimitExceededException(
                    "Rate limit exceeded: max " + MAX_REQUESTS_PER_MINUTE + " requests per minute");
        }
    }

    private ChatRequest extractChatRequest(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof ChatRequest) return (ChatRequest) arg;
        }
        return null;
    }

    private UUID extractUserId(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof UUID) return (UUID) arg;
        }
        return null;
    }
}
