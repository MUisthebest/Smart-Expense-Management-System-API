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
import java.util.stream.Collectors;

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

    private final Map<UUID, ProjectMember> members = new HashMap<>();
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
        return new Project(ProjectId.newId(), ownerId, type, ProjectStatus.ACTIVE, priority, name, description, targetAmount, deadline, Instant.now());
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

    public ProjectId getId() {
        return id;
    }

    public UserId getOwnerId() {
        return ownerId;
    }

    public ProjectType getType() {
        return type;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public ProjectPriority getPriority() {
        return priority;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Money getTargetAmount() {
        return targetAmount;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public List<ProjectMember> getMembers() {
        return new ArrayList<>(members.values());
    }

    public List<ProjectContribution> getContributions() {
        return new ArrayList<>(contributions);
    }

    public ProjectTracking getTracking() {
        return tracking;
    }

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

    public void inviteMember(UserId actorId, UserId invitedUserId, boolean isAdmin) {
        requireGroup();
        requireCanManage(actorId);

        if (invitedUserId.equals(ownerId)) {
            throw new ProjectException("Owner cannot be invited", ProjectErrorCode.PROJECT_DUPLICATE_MEMBER);
        }
        ProjectMember existing = members.get(invitedUserId.value());
        if (existing != null && existing.getJoinStatus() != JoinStatus.LEFT) {
            throw new ProjectException("Member already exists", ProjectErrorCode.PROJECT_DUPLICATE_MEMBER);
        }

        ProjectMember member = new ProjectMember(invitedUserId, JoinStatus.INVITED, isAdmin);
        members.put(invitedUserId.value(), member);
    }

    public void acceptInvite(UserId actorId) {
        requireGroup();

        ProjectMember member = members.get(actorId.value());
        if (member == null) {
            throw new ProjectException("Member not found", ProjectErrorCode.PROJECT_MEMBER_NOT_FOUND);
        }
        if (member.getJoinStatus() == JoinStatus.LEFT) {
            throw new ProjectException("Member has left", ProjectErrorCode.PROJECT_MEMBER_NOT_FOUND);
        }
        member.accept();
    }

    public void setAdmin(UserId actorId, UserId targetUserId, boolean isAdmin) {
        requireGroup();
        requireCanManage(actorId);

        ProjectMember member = members.get(targetUserId.value());
        if (member == null || member.getJoinStatus() == JoinStatus.LEFT) {
            throw new ProjectException("Member not found", ProjectErrorCode.PROJECT_MEMBER_NOT_FOUND);
        }
        member.setAdmin(isAdmin);
    }

    public void removeMember(UserId actorId, UserId targetUserId) {
        requireGroup();
        requireCanManage(actorId);

        ProjectMember member = members.get(targetUserId.value());
        if (member == null || member.getJoinStatus() == JoinStatus.LEFT) {
            throw new ProjectException("Member not found", ProjectErrorCode.PROJECT_MEMBER_NOT_FOUND);
        }
        member.leave();
    }

    public ProjectContribution addContribution(UserId actorId, BigDecimal amount,
                                               String note) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ProjectException("Contribution amount must be > 0",
                    ProjectErrorCode.PROJECT_CONTRIBUTION_INVALID_AMOUNT);
        }

        if (type == ProjectType.PERSONAL) {
            if (!actorId.equals(ownerId)) {
                throw new ProjectException("Permission denied",
                        ProjectErrorCode.PROJECT_PERMISSION_DENIED);
            }
        } else {
            if (actorId.equals(ownerId)) {
                // Owner can always contribute in GROUP projects
            } else {
                ProjectMember member = members.get(actorId.value());
                if (member == null) {
                    throw new ProjectException("Member not found", ProjectErrorCode.PROJECT_MEMBER_NOT_FOUND);
                }
                if (member.getJoinStatus() != JoinStatus.JOINED) {
                    throw new ProjectException("Member not joined", ProjectErrorCode.PROJECT_MEMBER_NOT_FOUND);
                }
            }
        }

        Money money = new Money(amount, targetAmount.currency());
        ProjectContribution c = new ProjectContribution(
                ContributionId.newId(),
                actorId,
                money,
                note,
                Instant.now()
        );
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

    public Map<UserId, Money> breakdownByUser() {
        Map<UUID, Money> map = new HashMap<>();
        for (ProjectContribution c : contributions) {
            UUID uid = c.getUserId().value();
            Money cur = map.getOrDefault(uid, Money.zero(targetAmount.currency()));
            map.put(uid, cur.plus(c.getMoney()));
        }
        return map.entrySet().stream()
                .collect(Collectors.toMap(e -> new UserId(e.getKey()), Map.Entry::getValue));
    }

    private void requireGroup() {
        if (type != ProjectType.GROUP) {
            throw new ProjectException("This operation is only for GROUP project",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
    }

    private void requireCanManage(UserId actorId) {
        if (actorId == null) {
            throw new ProjectException("Permission denied",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
        if (actorId.equals(ownerId)) return;

        if (type == ProjectType.GROUP) {
            ProjectMember member = members.get(actorId.value());
            if (member != null && member.getJoinStatus() == JoinStatus.JOINED && member.isAdmin()) return;
        }

        throw new ProjectException("Permission denied", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public static Project rehydrate (ProjectId id,
                                     UserId ownerId,
                                     ProjectType type,
                                     ProjectStatus status,
                                     ProjectPriority priority,
                                     String name,
                                     String description,
                                     Money targetAmount,
                                     LocalDate deadline,
                                     List<ProjectMember> members,
                                     List<ProjectContribution> contributions,
                                     ProjectTracking tracking,
                                     Instant createdAt
    ) {
        Project p = new Project(id, ownerId, type, status, priority, name, description, targetAmount, deadline, createdAt);
        p.members.clear();
        if (members != null) {
            for (ProjectMember m : members) {
                p.members.put(m.getUserId().value(), m);
            }
        }
        p.contributions.clear();
        if (contributions != null) {
            p.contributions.addAll(contributions);
        }
        p.tracking = tracking;
        return p;
    }
}