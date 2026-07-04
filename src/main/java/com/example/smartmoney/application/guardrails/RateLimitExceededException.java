package com.example.smartmoney.application.guardrails;

/** Thrown when a user exceeds the 20 req/min chat rate limit. */
public class RateLimitExceededException extends RuntimeException {

    public RateLimitExceededException(String message) {
        super(message);
    }
}
