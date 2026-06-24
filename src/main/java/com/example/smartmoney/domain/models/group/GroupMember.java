package com.example.smartmoney.domain.models.group;

import com.example.smartmoney.domain.models.group.enums.GroupMemberInviteStatus;
import com.example.smartmoney.domain.models.group.enums.GroupMemberRole;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class GroupMember {

    private final UUID id;
    private final UserId userId;
    private final GroupMemberRole role;
    private GroupMemberInviteStatus inviteStatus;
    private BigDecimal capacitySnapshot;
    private Instant joinedAt;
    private final Instant createdAt;
    private Instant updatedAt;

    public GroupMember(UUID id, UserId userId, GroupMemberRole role,
                       GroupMemberInviteStatus inviteStatus, BigDecimal capacitySnapshot,
                       Instant joinedAt, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.userId = userId;
        this.role = role;
        this.inviteStatus = inviteStatus;
        this.capacitySnapshot = capacitySnapshot;
        this.joinedAt = joinedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static GroupMember invited(UserId userId, GroupMemberRole role) {
        Instant now = Instant.now();
        return new GroupMember(UUID.randomUUID(), userId, role,
                GroupMemberInviteStatus.INVITED, null, null, now, now);
    }

    public static GroupMember joinedAdmin(UserId userId, BigDecimal capacitySnapshot) {
        Instant now = Instant.now();
        return new GroupMember(UUID.randomUUID(), userId, GroupMemberRole.ADMIN,
                GroupMemberInviteStatus.JOINED, capacitySnapshot, now, now, now);
    }

    public void accept(BigDecimal capacitySnapshot) {
        this.inviteStatus = GroupMemberInviteStatus.JOINED;
        this.capacitySnapshot = capacitySnapshot;
        this.joinedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void decline() {
        this.inviteStatus = GroupMemberInviteStatus.DECLINED;
        this.updatedAt = Instant.now();
    }

    public void reinvite() {
        this.inviteStatus = GroupMemberInviteStatus.INVITED;
        this.capacitySnapshot = null;
        this.joinedAt = null;
        this.updatedAt = Instant.now();
    }

    public boolean isResolved() {
        return inviteStatus == GroupMemberInviteStatus.JOINED
                || inviteStatus == GroupMemberInviteStatus.DECLINED;
    }

    public boolean isPending() {
        return inviteStatus == GroupMemberInviteStatus.INVITED;
    }

    public UUID getId() { return id; }
    public UserId getUserId() { return userId; }
    public GroupMemberRole getRole() { return role; }
    public GroupMemberInviteStatus getInviteStatus() { return inviteStatus; }
    public BigDecimal getCapacitySnapshot() { return capacitySnapshot; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
