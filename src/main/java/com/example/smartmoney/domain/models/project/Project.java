package com.example.smartmoney.domain.models.project;

import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.enums.*;
import com.example.smartmoney.domain.models.project.valueobjects.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

public class Project {

    private final ProjectId id;
    private final UserId ownerId;
    private final ProjectType type;
    private ProjectStatus status;
    private ProjectPriority priority;

    private String name;
    private String description;
    private Money targetAmount;
    private LocalDate deadline;

    /** Non-null for sub-personal projects that belong to a group project. */
    private final UUID groupProjectId;

    private final List<ProjectContribution> contributions = new ArrayList<>();
    private ProjectTracking tracking;
    private final Instant createdAt;

    private Project(ProjectId id,
                    UserId ownerId,
                    ProjectType type,
                    ProjectStatus status,
                    ProjectPriority priority,
                    String name,
                    String description,
                    Money targetAmount,
                    LocalDate deadline,
                    UUID groupProjectId,
                    Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.ownerId = Objects.requireNonNull(ownerId);
        this.type = Objects.requireNonNull(type);
        this.status = status == null ? ProjectStatus.ACTIVE : status;
        this.priority = priority == null ? ProjectPriority.MEDIUM : priority;
        this.name = normalizeName(name);
        this.description = normalizeDescription(description);
        this.targetAmount = Objects.requireNonNull(targetAmount);
        this.deadline = Objects.requireNonNull(deadline);
        this.groupProjectId = groupProjectId;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;

        validateCreate();
    }

    public static Project createNew(UserId ownerId,
                                    ProjectType type,
                                    ProjectPriority priority,
                                    String name,
                                    String description,
                                    Money targetAmount,
                                    LocalDate deadline) {
        return new Project(ProjectId.newId(), ownerId, type, ProjectStatus.ACTIVE, priority,
                name, description, targetAmount, deadline, null, Instant.now());
    }

    public static Project createSubPersonal(UserId ownerId,
                                            ProjectPriority priority,
                                            String name,
                                            String description,
                                            Money targetAmount,
                                            LocalDate deadline,
                                            UUID groupProjectId) {
        Objects.requireNonNull(groupProjectId, "groupProjectId required for sub-personal project");
        return new Project(ProjectId.newId(), ownerId, ProjectType.PERSONAL, ProjectStatus.ACTIVE,
                priority, name, description, targetAmount, deadline, groupProjectId, Instant.now());
    }

    private void validateCreate() {
        if (targetAmount.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ProjectException("Target amount must be > 0", ProjectErrorCode.PROJECT_INVALID_TARGET_AMOUNT);
        }
        if (deadline.isBefore(LocalDate.now())) {
            throw new ProjectException("Deadline must be today or later", ProjectErrorCode.PROJECT_INVALID_DEADLINE);
        }
    }

    private String normalizeName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ProjectException("Project name is required", ProjectErrorCode.PROJECT_INVALID_TARGET_AMOUNT);
        }
        return raw.trim();
    }

    private String normalizeDescription(String raw) {
        return raw == null ? null : raw.trim();
    }

    public ProjectId getId() { return id; }
    public UserId getOwnerId() { return ownerId; }
    public ProjectType getType() { return type; }
    public ProjectStatus getStatus() { return status; }
    public ProjectPriority getPriority() { return priority; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Money getTargetAmount() { return targetAmount; }
    public LocalDate getDeadline() { return deadline; }
    public UUID getGroupProjectId() { return groupProjectId; }
    public boolean isSubPersonal() { return groupProjectId != null; }
    public List<ProjectContribution> getContributions() { return new ArrayList<>(contributions); }
    public ProjectTracking getTracking() { return tracking; }
    public Instant getCreatedAt() { return createdAt; }

    public void setTracking(ProjectTracking tracking) {
        this.tracking = tracking;
    }

    public void updateInfo(UserId actorId, String name, String description, Money targetAmount, LocalDate deadline) {
        requireCanManage(actorId);

        if (name != null && !name.isBlank()) this.name = name.trim();
        if (description != null) this.description = description.trim();

        if (targetAmount != null) {
            if (targetAmount.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ProjectException("Target amount must be > 0", ProjectErrorCode.PROJECT_INVALID_TARGET_AMOUNT);
            }
            Money already = totalContributed();
            if (!already.currency().equals(targetAmount.currency())) {
                throw new ProjectException("Currency mismatch", ProjectErrorCode.PROJECT_INVALID_TARGET_AMOUNT);
            }
            this.targetAmount = targetAmount;
        }

        if (deadline != null) {
            if (deadline.isBefore(LocalDate.now())) {
                throw new ProjectException("Deadline must be today or later", ProjectErrorCode.PROJECT_INVALID_DEADLINE);
            }
            this.deadline = deadline;
        }
    }

    public void updateStatus(UserId actorId, ProjectStatus newStatus) {
        requireCanManage(actorId);
        if (newStatus == null) return;
        this.status = newStatus;
    }

    public void updatePriority(UserId actorId, ProjectPriority newPriority) {
        requireCanManage(actorId);
        if (newPriority == null) return;
        this.priority = newPriority;
    }

    public ProjectContribution addContribution(UserId actorId, BigDecimal amount, String note) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ProjectException("Contribution amount must be > 0",
                    ProjectErrorCode.PROJECT_CONTRIBUTION_INVALID_AMOUNT);
        }
        requireContributable();

        if (!actorId.equals(ownerId)) {
            throw new ProjectException("Permission denied", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        Money money = new Money(amount, targetAmount.currency());
        ProjectContribution c = new ProjectContribution(
                ContributionId.newId(), actorId, money, note, Instant.now());
        contributions.add(c);
        return c;
    }

    public Money totalContributed() {
        if (tracking != null) {
            return new Money(tracking.getMoneySaved(), targetAmount.currency());
        }
        Money total = Money.zero(targetAmount.currency());
        for (ProjectContribution c : contributions) {
            total = total.plus(c.getMoney());
        }
        return total;
    }

    public Money remaining() {
        Money total = totalContributed();
        BigDecimal rem = targetAmount.amount().subtract(total.amount());
        if (rem.compareTo(BigDecimal.ZERO) < 0) rem = BigDecimal.ZERO;
        return new Money(rem, targetAmount.currency());
    }

    public BigDecimal progressPercent() {
        if (targetAmount.amount().compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        BigDecimal percent = totalContributed().amount()
                .multiply(new BigDecimal("100"))
                .divide(targetAmount.amount(), 2, RoundingMode.HALF_UP);
        if (percent.compareTo(new BigDecimal("100")) > 0) return new BigDecimal("100.00");
        if (percent.compareTo(BigDecimal.ZERO) < 0) return BigDecimal.ZERO;
        return percent;
    }

    private void requireContributable() {
        if (status == ProjectStatus.FROZEN) {
            throw new ProjectException("Project is frozen; contributions are paused",
                    ProjectErrorCode.PROJECT_FROZEN);
        }
        if (status == ProjectStatus.ABANDONED) {
            throw new ProjectException("Project is abandoned; contributions are closed",
                    ProjectErrorCode.PROJECT_ABANDONED);
        }
        if (status != ProjectStatus.ACTIVE) {
            throw new ProjectException("Project is not active; contributions are closed",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
    }

    private void requireCanManage(UserId actorId) {
        if (actorId == null || !actorId.equals(ownerId)) {
            throw new ProjectException("Permission denied", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
    }

    public void freeze(UserId actorId) {
        if (actorId != null && !actorId.equals(ownerId)) {
            throw new ProjectException("Only owner can freeze project",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
        if (status == ProjectStatus.ABANDONED) {
            throw new ProjectException("Project is abandoned", ProjectErrorCode.PROJECT_ABANDONED);
        }
        if (status == ProjectStatus.COMPLETED) {
            throw new ProjectException("Project is completed", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
        this.status = ProjectStatus.FROZEN;
    }

    public void unfreeze(UserId actorId) {
        if (!actorId.equals(ownerId)) {
            throw new ProjectException("Only owner can unfreeze project",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
        if (status != ProjectStatus.FROZEN) {
            throw new ProjectException("Project is not frozen",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
        this.status = ProjectStatus.ACTIVE;
    }

    public void abandon(UserId actorId) {
        if (!actorId.equals(ownerId)) {
            throw new ProjectException("Only owner can abandon project",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
        if (status == ProjectStatus.ABANDONED) {
            throw new ProjectException("Project already abandoned",
                    ProjectErrorCode.PROJECT_ABANDONED);
        }
        this.status = ProjectStatus.ABANDONED;
    }

    public static Project rehydrate(ProjectId id,
                                    UserId ownerId,
                                    ProjectType type,
                                    ProjectStatus status,
                                    ProjectPriority priority,
                                    String name,
                                    String description,
                                    Money targetAmount,
                                    LocalDate deadline,
                                    UUID groupProjectId,
                                    List<ProjectContribution> contributions,
                                    ProjectTracking tracking,
                                    Instant createdAt) {
        Project p = new Project(id, ownerId, type, status, priority, name, description,
                targetAmount, deadline, groupProjectId, createdAt);
        p.contributions.clear();
        if (contributions != null) {
            p.contributions.addAll(contributions);
        }
        p.tracking = tracking;
        return p;
    }
}
