package com.example.smartmoney.domain.models.group;

import com.example.smartmoney.domain.models.group.enums.GroupProjectStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupProjectId;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

public class GroupProject {

    private final GroupProjectId id;
    private final UUID groupId;
    private final String name;
    private final String description;
    private BigDecimal targetAmount;
    private final BigDecimal totalCapacity;
    private final String currency;
    private final int totalMonths;
    private final LocalDate deadline;
    private GroupProjectStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private GroupProject(GroupProjectId id, UUID groupId, String name, String description,
                         BigDecimal targetAmount, BigDecimal totalCapacity, String currency,
                         int totalMonths, LocalDate deadline, GroupProjectStatus status,
                         Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.groupId = Objects.requireNonNull(groupId);
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.targetAmount = Objects.requireNonNull(targetAmount);
        this.totalCapacity = Objects.requireNonNull(totalCapacity);
        this.currency = Objects.requireNonNull(currency);
        this.totalMonths = totalMonths;
        this.deadline = Objects.requireNonNull(deadline);
        this.status = Objects.requireNonNull(status);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static GroupProject createNew(UUID groupId, String name, String description,
                                         BigDecimal targetAmount, BigDecimal totalCapacity,
                                         String currency, int totalMonths, LocalDate deadline) {
        Instant now = Instant.now();
        return new GroupProject(GroupProjectId.newId(), groupId, name.trim(), description,
                targetAmount, totalCapacity, currency.toUpperCase(), totalMonths, deadline,
                GroupProjectStatus.ACTIVE, now, now);
    }

    public void reduceTargetAmount(BigDecimal reduction) {
        this.targetAmount = this.targetAmount.subtract(reduction).max(BigDecimal.ZERO);
        this.updatedAt = Instant.now();
    }

    public void markCompleted() {
        this.status = GroupProjectStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public void dissolve() {
        this.status = GroupProjectStatus.DISSOLVED;
        this.updatedAt = Instant.now();
    }

    public static GroupProject rehydrate(GroupProjectId id, UUID groupId, String name,
                                         String description, BigDecimal targetAmount,
                                         BigDecimal totalCapacity, String currency,
                                         int totalMonths, LocalDate deadline,
                                         GroupProjectStatus status, Instant createdAt,
                                         Instant updatedAt) {
        return new GroupProject(id, groupId, name, description, targetAmount, totalCapacity,
                currency, totalMonths, deadline, status, createdAt, updatedAt);
    }

    public GroupProjectId getId() { return id; }
    public UUID getGroupId() { return groupId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getTargetAmount() { return targetAmount; }
    public BigDecimal getTotalCapacity() { return totalCapacity; }
    public String getCurrency() { return currency; }
    public int getTotalMonths() { return totalMonths; }
    public LocalDate getDeadline() { return deadline; }
    public GroupProjectStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
