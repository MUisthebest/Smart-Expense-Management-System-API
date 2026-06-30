package com.example.smartmoney.infrastructure.persistence.entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suggestion_topic_stats")
@Getter
@Setter
public class SuggestionTopicStatEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "dedup_key", nullable = false, columnDefinition = "text")
    private String dedupKey;

    @Column(name = "dismiss_count", nullable = false)
    private int dismissCount;

    @Column(name = "last_dismissed_at")
    private Instant lastDismissedAt;

    @Column(name = "last_pushed_at")
    private Instant lastPushedAt;
}
