package com.example.smartmoney.domain.models.suggestion;

import com.example.smartmoney.domain.exceptions.DomainException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A persisted yes/no ask derived from a high-value {@link com.example.smartmoney.domain.models.insight.Insight}.
 * The payload stores the <em>resolved</em> action (a specific number the user consents to),
 * never a recompute recipe. A partial-unique index on {@code (userId, dedupKey)} where
 * status = PENDING guarantees at most one open ask per topic. See docs/redesign-workstream-c-plan.md §C4.
 */
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Setter
public class Suggestion {
    private UUID id;
    private UUID userId;
    private SuggestionType type;
    private SuggestionStatus status;
    /** JSON: { insightSnapshot, proposedAction:{ category, budgetId, resolvedValue } }. */
    private String payloadJson;
    /** type + category + period, e.g. "RAISE_BUDGET:FOOD:2026-07" — one open ask per topic. */
    private String dedupKey;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant decidedAt;

    /** Factory for a brand-new PENDING suggestion. */
    public static Suggestion create(UUID userId, SuggestionType type, String payloadJson,
                                    String dedupKey, Instant expiresAt) {
        if (userId == null) {
            throw new DomainException("UserId cannot be null");
        }
        if (type == null) {
            throw new DomainException("Suggestion type cannot be null");
        }
        if (payloadJson == null || payloadJson.isBlank()) {
            throw new DomainException("Suggestion payload cannot be empty");
        }
        if (dedupKey == null || dedupKey.isBlank()) {
            throw new DomainException("Suggestion dedupKey cannot be empty");
        }

        Suggestion suggestion = new Suggestion();
        suggestion.id = UUID.randomUUID();
        suggestion.userId = userId;
        suggestion.type = type;
        suggestion.status = SuggestionStatus.PENDING;
        suggestion.payloadJson = payloadJson;
        suggestion.dedupKey = dedupKey;
        suggestion.createdAt = Instant.now();
        suggestion.expiresAt = expiresAt;
        return suggestion;
    }

    // Lifecycle — PENDING-only guards (mirrors the Project lifecycle style).

    public void accept() {
        transitionFromPending(SuggestionStatus.ACCEPTED);
    }

    public void dismiss() {
        transitionFromPending(SuggestionStatus.DISMISSED);
    }

    public void expire() {
        transitionFromPending(SuggestionStatus.EXPIRED);
    }

    public boolean isPending() {
        return status == SuggestionStatus.PENDING;
    }

    private void transitionFromPending(SuggestionStatus target) {
        if (status != SuggestionStatus.PENDING) {
            throw new DomainException("Suggestion is already decided: " + status);
        }
        this.status = target;
        this.decidedAt = Instant.now();
    }
}
