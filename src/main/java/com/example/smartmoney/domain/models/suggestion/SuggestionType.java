package com.example.smartmoney.domain.models.suggestion;

/**
 * Action a {@code Suggestion} proposes. Phase 1 (workstream C) ships only RAISE_BUDGET;
 * CREATE_BUDGET / CONTRIBUTE_TO_PROJECT etc. are Phase 2+. Stored as a plain varchar
 * (no DB CHECK), so adding values stays a code-only change.
 */
public enum SuggestionType {
    RAISE_BUDGET
}
