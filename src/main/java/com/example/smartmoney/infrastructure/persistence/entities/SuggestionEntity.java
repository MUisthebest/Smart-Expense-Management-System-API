package com.example.smartmoney.infrastructure.persistence.entities;

import com.example.smartmoney.domain.models.suggestion.SuggestionStatus;
import com.example.smartmoney.domain.models.suggestion.SuggestionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "suggestion")
@Getter
@Setter
public class SuggestionEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private SuggestionType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SuggestionStatus status;

    // Stored as text (JSON string). Plain text rather than jsonb keeps Hibernate
    // ddl-auto: validate happy with a String attribute. See V21 migration note.
    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "dedup_key", nullable = false, columnDefinition = "text")
    private String dedupKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "decided_at")
    private Instant decidedAt;
}
