package com.example.smartmoney.domain.models.project;

import com.example.smartmoney.domain.models.project.valueobjects.ContributionId;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;


import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class ProjectContribution {
    private final ContributionId id;
    private final UserId userId;
    private final Money money;
    private final String note;
    private final Instant createdAt;

    public ProjectContribution(
            ContributionId id,
            UserId userId,
            Money money,
            String note,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.money = Objects.requireNonNull(money);
        this.note = note;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public ContributionId getId() {
        return id;
    }

    public UserId getUserId() {
        return userId;
    }

    public Money getMoney() {
        return money;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
