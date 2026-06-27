package com.example.smartmoney.domain.models.project;

import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class ProjectTracking {
    private final UUID id;
    private final ProjectId projectId;
    private BigDecimal moneySaved;
    private int currentMonth;
    private BigDecimal monthlySaving;
    private int monthLeft;
    private BigDecimal moneyOwed;
    private final Instant createdAt;
    private Instant updatedAt;

    public ProjectTracking(
            UUID id,
            ProjectId projectId,
            BigDecimal moneySaved,
            int currentMonth,
            BigDecimal monthlySaving,
            int monthLeft,
            //int frozen_months,
            BigDecimal moneyOwed,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id);
        this.projectId = Objects.requireNonNull(projectId);
        this.moneySaved = normalizeMoney(moneySaved, "moneySaved");
        this.currentMonth = normalizePositiveMonth(currentMonth);
        this.monthlySaving = normalizeMoney(monthlySaving, "monthlySaving");
        this.monthLeft = normalizeNonNegativeInt(monthLeft, "monthLeft");
        this.moneyOwed = normalizeMoney(moneyOwed, "moneyOwed");
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
    }

    public static ProjectTracking createNew(
            ProjectId projectId,
            BigDecimal moneySaved,
            int currentMonth,
            BigDecimal monthlySaving,
            int monthLeft,
            BigDecimal moneyOwed
    ) {
        Instant now = Instant.now();
        return new ProjectTracking(
                UUID.randomUUID(),
                projectId,
                moneySaved,
                currentMonth,
                monthlySaving,
                monthLeft,
                moneyOwed,
                now,
                now
        );
    }

    public void update(
            BigDecimal moneySaved,
            Integer currentMonth,
            Integer monthLeft,
            BigDecimal moneyOwed
    ) {
        if (moneySaved != null) {
            this.moneySaved = normalizeMoney(moneySaved, "moneySaved");
        }
        if (currentMonth != null) {
            this.currentMonth = normalizePositiveMonth(currentMonth);
        }
        if (monthLeft != null) {
            this.monthLeft = normalizeNonNegativeInt(monthLeft, "monthLeft");
        }
        if (moneyOwed != null) {
            this.moneyOwed = normalizeMoney(moneyOwed, "moneyOwed");
        }
        this.updatedAt = Instant.now();
    }

    /**
     * Recalibrates monthly saving and months left when the project's
     * target amount or deadline is changed by the user.
     * Does NOT touch moneySaved, currentMonth, or moneyOwed.
     *
     * @param newMonthlySaving freshly calculated (remaining / monthsLeft)
     * @param newMonthLeft     months to deadline from today
     */
    public void recalibrate(BigDecimal newMonthlySaving, int newMonthLeft) {
        this.monthlySaving = normalizeMoney(newMonthlySaving, "monthlySaving");
        this.monthLeft     = normalizeNonNegativeInt(newMonthLeft, "monthLeft");
        this.updatedAt     = Instant.now();
    }

    private BigDecimal normalizeMoney(BigDecimal value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(field + " must be >= 0");
        }
        return value;
    }

    private int normalizePositiveMonth(int month) {
        if (month <= 0) {
            throw new IllegalArgumentException("currentMonth must be > 0");
        }
        return month;
    }

    private int normalizeNonNegativeInt(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be >= 0");
        }
        return value;
    }

    public UUID getId() {
        return id;
    }

    public ProjectId getProjectId() {
        return projectId;
    }

    public BigDecimal getMoneySaved() {
        return moneySaved;
    }

    public int getCurrentMonth() {
        return currentMonth;
    }

    public BigDecimal getMonthlySaving() {
        return monthlySaving;
    }

    public int getMonthLeft() {
        return monthLeft;
    }

    public BigDecimal getMoneyOwed() {
        return moneyOwed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
