package com.example.smartmoney.application.guardrails;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a use-case method for input guardrail interception.
 * {@link InputGuardrailAspect} intercepts any method carrying this annotation,
 * runs the rate-limit check first, then all {@link PromptGuardrail} beans in order.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InputGuardrail {
}
