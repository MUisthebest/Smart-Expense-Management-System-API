package com.example.smartmoney.infrastructure.persistence.entities;

import com.example.smartmoney.domain.models.group.enums.GroupMemberInviteStatus;
import com.example.smartmoney.domain.models.group.enums.GroupMemberRole;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "group_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_group_member", columnNames = {"group_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupMemberEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private GroupMemberRole role = GroupMemberRole.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(name = "invite_status", nullable = false, length = 20)
    @Builder.Default
    private GroupMemberInviteStatus inviteStatus = GroupMemberInviteStatus.INVITED;

    @Column(name = "capacity_snapshot", precision = 19, scale = 2)
    private BigDecimal capacitySnapshot;

    @Column(name = "auto_sponsor_enabled")
    @Builder.Default
    private Boolean autoSponsorEnabled = false;

    @Column(name = "auto_sponsor_limit", precision = 19, scale = 2)
    private BigDecimal autoSponsorLimit;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
