package com.example.smartmoney.domain.models.group;

import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.enums.GroupMemberInviteStatus;
import com.example.smartmoney.domain.models.group.enums.GroupMemberRole;
import com.example.smartmoney.domain.models.group.enums.GroupStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Group {

    public static final int MAX_GROUP_MEMBERS = 10;

    private final GroupId id;
    private String name;
    private String description;
    private final UserId adminId;
    private GroupStatus status;
    private final List<GroupMember> members = new ArrayList<>();
    private final Instant createdAt;
    private Instant updatedAt;

    private Group(GroupId id, String name, String description, UserId adminId,
                  GroupStatus status, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.description = description;
        this.adminId = Objects.requireNonNull(adminId);
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Group createNew(UserId adminId, String name, String description,
                                  BigDecimal adminCapacitySnapshot) {
        Instant now = Instant.now();
        Group g = new Group(GroupId.newId(), name.trim(), description, adminId,
                GroupStatus.FORMING, now, now);
        g.members.add(GroupMember.joinedAdmin(adminId, adminCapacitySnapshot));
        return g;
    }

    public void inviteMember(UserId invitedUserId) {
        requireForming();
        if (invitedUserId.equals(adminId)) {
            throw new ProjectException("Admin cannot be re-invited", ProjectErrorCode.PROJECT_DUPLICATE_MEMBER);
        }
        boolean alreadyJoined = members.stream()
                .anyMatch(m -> m.getUserId().equals(invitedUserId)
                        && m.getInviteStatus() == GroupMemberInviteStatus.JOINED);
        if (alreadyJoined) {
            throw new ProjectException("Member already joined", ProjectErrorCode.PROJECT_DUPLICATE_MEMBER);
        }

        // Re-invite a previously invited or declined member by resetting their status instead of adding a duplicate
        members.stream()
                .filter(m -> m.getUserId().equals(invitedUserId)
                        && (m.getInviteStatus() == GroupMemberInviteStatus.DECLINED 
                            || m.getInviteStatus() == GroupMemberInviteStatus.INVITED))
                .findFirst()
                .ifPresentOrElse(existing -> {
                    long activeCount = members.stream()
                            .filter(m -> m.getInviteStatus() != GroupMemberInviteStatus.DECLINED
                                    || m == existing)
                            .count();
                    if (activeCount > MAX_GROUP_MEMBERS) {
                        throw new ProjectException("Member limit reached", ProjectErrorCode.PROJECT_MEMBER_LIMIT_REACHED);
                    }
                    existing.reinvite();
                }, () -> {
                    long activeCount = members.stream()
                            .filter(m -> m.getInviteStatus() != GroupMemberInviteStatus.DECLINED)
                            .count();
                    if (activeCount >= MAX_GROUP_MEMBERS) {
                        throw new ProjectException("Member limit reached", ProjectErrorCode.PROJECT_MEMBER_LIMIT_REACHED);
                    }
                    members.add(GroupMember.invited(invitedUserId, GroupMemberRole.MEMBER));
                });

        updatedAt = Instant.now();
    }

    public void acceptMember(UserId userId, BigDecimal capacitySnapshot) {
        requireForming();
        GroupMember member = findMember(userId);
        if (member.getInviteStatus() != GroupMemberInviteStatus.INVITED) {
            throw new ProjectException("Invite not pending", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
        member.accept(capacitySnapshot);
        updatedAt = Instant.now();
    }

    public void declineMember(UserId userId) {
        requireForming();
        GroupMember member = findMember(userId);
        if (member.getInviteStatus() != GroupMemberInviteStatus.INVITED) {
            throw new ProjectException("Invite not pending", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
        member.decline();
        updatedAt = Instant.now();
    }

    public void removeOrKickMember(UserId actorId, UserId targetUserId) {
        requireAdmin(actorId);
        requireForming();
        GroupMember member = findMember(targetUserId);
        if (member.getInviteStatus() != GroupMemberInviteStatus.DECLINED
                && member.getInviteStatus() != GroupMemberInviteStatus.INVITED) {
            throw new ProjectException("Only pending or declined members can be removed",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
        members.remove(member);
        updatedAt = Instant.now();
    }

    /**
     * Admin explicitly locks the group. At least one non-admin member must be JOINED.
     * No pending invites are required — admin decides when the group is ready.
     */
    public void lock(UserId actorId) {
        requireAdmin(actorId);
        requireForming();
        boolean hasNonAdminJoined = members.stream()
                .anyMatch(m -> m.getRole() == GroupMemberRole.MEMBER
                        && m.getInviteStatus() == GroupMemberInviteStatus.JOINED);
        if (!hasNonAdminJoined) {
            throw new ProjectException(
                    "Cannot lock group: at least one non-admin member must have joined",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
        // Automatically remove all pending invites when locking
        members.removeIf(m -> m.getInviteStatus() == GroupMemberInviteStatus.INVITED);
        this.status = GroupStatus.LOCKED;
        this.updatedAt = Instant.now();
    }

    public void unlock(UserId actorId) {
        requireAdmin(actorId);
        if (this.status != GroupStatus.LOCKED) {
            throw new ProjectException("Group is not locked",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
        this.status = GroupStatus.FORMING;
        this.updatedAt = Instant.now();
    }

    public void dissolve() {
        this.status = GroupStatus.DISSOLVED;
        this.updatedAt = Instant.now();
    }

    public BigDecimal totalCapacity() {
        List<BigDecimal> activeCapacities = members.stream()
                .filter(m -> m.getInviteStatus() == GroupMemberInviteStatus.JOINED)
                .map(GroupMember::getCapacitySnapshot)
                .filter(Objects::nonNull)
                .toList();

        if (activeCapacities.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal minCapacity = activeCapacities.stream()
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        return minCapacity.multiply(BigDecimal.valueOf(activeCapacities.size()));
    }

    public List<GroupMember> joinedMembers() {
        return members.stream()
                .filter(m -> m.getInviteStatus() == GroupMemberInviteStatus.JOINED)
                .toList();
    }

    private GroupMember findMember(UserId userId) {
        return members.stream()
                .filter(m -> m.getUserId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new ProjectException("Member not found",
                        ProjectErrorCode.PROJECT_MEMBER_NOT_FOUND));
    }

    private void requireForming() {
        if (status != GroupStatus.FORMING) {
            throw new ProjectException("Group is not in FORMING state",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
    }

    private void requireAdmin(UserId actorId) {
        if (!actorId.equals(adminId)) {
            throw new ProjectException("Only group admin can perform this action",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
    }

    public static Group rehydrate(GroupId id, String name, String description, UserId adminId,
                                  GroupStatus status, List<GroupMember> members,
                                  Instant createdAt, Instant updatedAt) {
        Group g = new Group(id, name, description, adminId, status, createdAt, updatedAt);
        g.members.clear();
        if (members != null) g.members.addAll(members);
        return g;
    }

    public GroupId getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public UserId getAdminId() { return adminId; }
    public GroupStatus getStatus() { return status; }
    public List<GroupMember> getMembers() { return new ArrayList<>(members); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
