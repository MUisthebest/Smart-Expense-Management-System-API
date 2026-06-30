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
 * Per-(user, topic) memory for a suggestion topic, keyed by the suggestion {@code dedupKey} — the
 * <em>topic</em>, not a single suggestion row — because both dismissals and pushes accumulate across
 * successive instances of the same ask.
 * <ul>
 *   <li>P2-A: {@code dismissCount} lets the producer back off on topics the user keeps saying "no" to.</li>
 *   <li>P3-A: {@code lastPushedAt} lets the producer enforce a per-topic cooldown (a minimum spacing
 *       between pushes on the same topic) on top of dedup and dismissal suppression.</li>
 * </ul>
 * See docs/redesign-phase2-3-plan.md §P2-A / §P3-A.
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
    private Instant lastPushedAt;

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

    /** Stamp the moment this topic was last pushed to the user (drives the P3-A cooldown). */
    public void recordPush(Instant at) {
        this.lastPushedAt = at;
    }
}
