package com.example.smartmoney.domain.models.group;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class GroupProjectSponsorshipRequest {
    private final UUID id;
    private final UUID groupProjectId;
    private final UUID userId;
    private final BigDecimal askedAmount;
    private String status;
    private final Instant createdAt;
    private Instant updatedAt;

    public GroupProjectSponsorshipRequest(UUID id, UUID groupProjectId, UUID userId,
                                         BigDecimal askedAmount, String status,
                                         Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.groupProjectId = groupProjectId;
        this.userId = userId;
        this.askedAmount = askedAmount;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static GroupProjectSponsorshipRequest createNew(UUID groupProjectId, UUID userId, BigDecimal askedAmount) {
        Instant now = Instant.now();
        return new GroupProjectSponsorshipRequest(UUID.randomUUID(), groupProjectId, userId, askedAmount, "PENDING", now, now);
    }

    public void accept() {
        this.status = "AGREED";
        this.updatedAt = Instant.now();
    }

    public void decline() {
        this.status = "DECLINED";
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getGroupProjectId() { return groupProjectId; }
    public UUID getUserId() { return userId; }
    public BigDecimal getAskedAmount() { return askedAmount; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
