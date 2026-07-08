package com.example.smartmoney.domain.models.suggestion;

/**
 * Lifecycle of a {@code Suggestion}: PENDING is the only open state; a decision moves it
 * to ACCEPTED or DISMISSED, and time can move it to EXPIRED. Transitions out of PENDING
 * are one-way (mirrors the Project lifecycle style).
 */
public enum SuggestionStatus {
    PENDING,
    ACCEPTED,
    DISMISSED,
    EXPIRED
}
