package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.suggestion.Suggestion;
import com.example.smartmoney.domain.models.suggestion.SuggestionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SuggestionRepository {
    Suggestion save(Suggestion suggestion);

    Optional<Suggestion> findById(UUID id);

    /** The single open (PENDING) ask for a dedup topic, if one exists — used by the producer pre-check. */
    Optional<Suggestion> findOpenByUserIdAndDedupKey(UUID userId, String dedupKey);

    List<Suggestion> findByUserIdAndStatus(UUID userId, SuggestionStatus status);

    /**
     * Idempotent, lock-free decision transition: flips a PENDING row to {@code newStatus}.
     * Returns the number of rows affected — 0 means already decided / not owner (no-op).
     */
    int markDecidedIfPending(UUID id, UUID userId, SuggestionStatus newStatus, Instant decidedAt);
}
