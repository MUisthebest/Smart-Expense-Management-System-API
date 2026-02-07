package com.example.smartmoney.domain.models.project;

import com.example.smartmoney.domain.models.project.valueobjects.ContributionId;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.presentation.controllers.ProjectController;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public class ProjectContribution {
    private final ContributionId id;
    private final UserId userId;
    private final Money money;
    private final LocalDate contributeDate;
    private final String note;
    private final Instant createdAt;

    public ProjectContribution(
            ContributionId id,
            UserId userId,
            Money money,
            LocalDate contributeDate,
            String note,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.money = Objects.requireNonNull(money);
        this.contributeDate = Objects.requireNonNull(contributeDate);
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

    public LocalDate getContributionDate() {
        return contributeDate;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
