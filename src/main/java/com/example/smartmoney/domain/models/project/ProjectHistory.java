package com.example.smartmoney.domain.models.project;

import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class ProjectHistory {

    private final UUID id;
    private final ProjectId projectId;
    private final UUID userId;
    private final int month;
    private final int year;
    private final BigDecimal moneySavedBefore;
    private final BigDecimal moneySavedAfter;
    private final BigDecimal monthlySaving;
    private final BigDecimal penalty;
    private final BigDecimal surplusInvested;
    private final int monthLeftBefore;
    private final int monthLeftAfter;
    private final Instant createdAt;

    private ProjectHistory(
            UUID id,
            ProjectId projectId,
            UUID userId,
            int month,
            int year,
            BigDecimal moneySavedBefore,
            BigDecimal moneySavedAfter,
            BigDecimal monthlySaving,
            BigDecimal penalty,
            BigDecimal surplusInvested,
            int monthLeftBefore,
            int monthLeftAfter,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.projectId = Objects.requireNonNull(projectId, "projectId is required");
        this.userId = Objects.requireNonNull(userId, "userId is required");
        this.month = month;
        this.year = year;
        this.moneySavedBefore = Objects.requireNonNull(moneySavedBefore, "moneySavedBefore is required");
        this.moneySavedAfter = Objects.requireNonNull(moneySavedAfter, "moneySavedAfter is required");
        this.monthlySaving = Objects.requireNonNull(monthlySaving, "monthlySaving is required");
        this.penalty = Objects.requireNonNull(penalty, "penalty is required");
        this.surplusInvested = Objects.requireNonNull(surplusInvested, "surplusInvested is required");
        this.monthLeftBefore = monthLeftBefore;
        this.monthLeftAfter = monthLeftAfter;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    }

    public static ProjectHistory createSnapshot(
            ProjectId projectId,
            UUID userId,
            int month,
            int year,
            BigDecimal moneySavedBefore,
            BigDecimal moneySavedAfter,
            BigDecimal monthlySaving,
            BigDecimal penalty,
            BigDecimal surplusInvested,
            int monthLeftBefore,
            int monthLeftAfter
    ) {
        return new ProjectHistory(
                UUID.randomUUID(),
                projectId,
                userId,
                month,
                year,
                moneySavedBefore,
                moneySavedAfter,
                monthlySaving,
                penalty,
                surplusInvested,
                monthLeftBefore,
                monthLeftAfter,
                Instant.now()
        );
    }

    // Rehydration constructor for persistence layer
    public static ProjectHistory rehydrate(
            UUID id,
            ProjectId projectId,
            UUID userId,
            int month,
            int year,
            BigDecimal moneySavedBefore,
            BigDecimal moneySavedAfter,
            BigDecimal monthlySaving,
            BigDecimal penalty,
            BigDecimal surplusInvested,
            int monthLeftBefore,
            int monthLeftAfter,
            Instant createdAt
    ) {
        return new ProjectHistory(id, projectId, userId, month, year,
                moneySavedBefore, moneySavedAfter, monthlySaving,
                penalty, surplusInvested, monthLeftBefore, monthLeftAfter, createdAt);
    }

    public UUID getId() { return id; }
    public ProjectId getProjectId() { return projectId; }
    public UUID getUserId() { return userId; }
    public int getMonth() { return month; }
    public int getYear() { return year; }
    public BigDecimal getMoneySavedBefore() { return moneySavedBefore; }
    public BigDecimal getMoneySavedAfter() { return moneySavedAfter; }
    public BigDecimal getMonthlySaving() { return monthlySaving; }
    public BigDecimal getPenalty() { return penalty; }
    public BigDecimal getSurplusInvested() { return surplusInvested; }
    public int getMonthLeftBefore() { return monthLeftBefore; }
    public int getMonthLeftAfter() { return monthLeftAfter; }
    public Instant getCreatedAt() { return createdAt; }
}
