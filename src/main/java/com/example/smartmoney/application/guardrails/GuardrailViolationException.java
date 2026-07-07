package com.example.smartmoney.application.guardrails;

/** Thrown by a {@link PromptGuardrail} when the input violates a rule. */
public class GuardrailViolationException extends RuntimeException {

    public GuardrailViolationException(String message) {
        super(message);
    }
}
