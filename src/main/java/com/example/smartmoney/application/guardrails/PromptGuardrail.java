package com.example.smartmoney.application.guardrails;

import java.util.UUID;

/**
 * Contract for an input guardrail bean.
 * Beans implementing this interface are auto-discovered by {@link InputGuardrailAspect}
 * and invoked in {@code @Order} sequence.
 */
public interface PromptGuardrail {

    /**
     * Validate the incoming message.
     *
     * @param message the raw user message (lowercase)
     * @param userId  the authenticated user
     * @throws GuardrailViolationException if the message violates this guardrail
     */
    void validate(String message, UUID userId);
}
