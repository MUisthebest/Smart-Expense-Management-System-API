package com.example.smartmoney.application.guardrails;

import com.example.smartmoney.application.dtos.requests.chat.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * AOP aspect that intercepts every {@link InputGuardrail}-annotated method.
 *
 * Execution order:
 *  1. Rate limit check (Bucket4j-style via Redis INCR, 20 req/user/min) → 429
 *  2. ScopeGuardrail @Order(1)
 *  3. PromptInjectionGuardrail @Order(2)
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

    public InputGuardrailAspect(
            List<PromptGuardrail> guardrails,
            @Qualifier("aiStringRedisTemplate") StringRedisTemplate redisTemplate) {
        this.guardrails = guardrails.stream()
                .sorted(AnnotationAwareOrderComparator.INSTANCE)
                .toList();
        this.redisTemplate = redisTemplate;
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

        // 2. Run guardrails in @Order sequence
        String message = request.getMessage() != null ? request.getMessage() : "";
        for (PromptGuardrail guardrail : guardrails) {
            guardrail.validate(message.toLowerCase(), userId);
        }

        return pjp.proceed();
    }

    private void checkRateLimit(UUID userId) {
        long minuteBucket = System.currentTimeMillis() / 60_000;
        String key = RATE_KEY_PREFIX + userId + ":" + minuteBucket;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            // Set TTL on first increment so the key auto-expires after the window
            redisTemplate.expire(key, Duration.ofSeconds(65));
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
