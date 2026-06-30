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
 * Per-(user, topic) memory of how often the user has dismissed a suggestion topic (P2-A).
 * Keyed by the suggestion {@code dedupKey} — the <em>topic</em>, not a single suggestion row —
 * because dismissals accumulate across successive instances of the same ask. The producer reads
 * {@code dismissCount} to back off on topics the user keeps saying "no" to. See
 * docs/redesign-phase2-3-plan.md §P2-A.
 */
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@Setter
public class SuggestionTopicStat {
    private UUID id;
    private UUID userId;
    private String dedupKey;
    private int dismissCount;
    private Instant lastDismissedAt;

    /** A fresh, zero-count topic stat for a (user, topic) pair. */
    public static SuggestionTopicStat start(UUID userId, String dedupKey) {
        if (userId == null) {
            throw new DomainException("UserId cannot be null");
        }
        if (dedupKey == null || dedupKey.isBlank()) {
            throw new DomainException("dedupKey cannot be empty");
        }
        SuggestionTopicStat stat = new SuggestionTopicStat();
        stat.id = UUID.randomUUID();
        stat.userId = userId;
        stat.dedupKey = dedupKey;
        stat.dismissCount = 0;
        return stat;
    }

    /** Record one more dismissal of this topic. */
    public void recordDismissal(Instant at) {
        this.dismissCount++;
        this.lastDismissedAt = at;
    }
}
