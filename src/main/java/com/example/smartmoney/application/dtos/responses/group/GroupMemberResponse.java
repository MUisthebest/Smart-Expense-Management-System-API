package com.example.smartmoney.application.dtos.responses.group;

import com.example.smartmoney.domain.models.group.GroupMember;
import com.example.smartmoney.domain.models.group.enums.GroupMemberInviteStatus;
import com.example.smartmoney.domain.models.group.enums.GroupMemberRole;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
public class GroupMemberResponse {
    private String userId;
    private GroupMemberRole role;
    private GroupMemberInviteStatus inviteStatus;
    private BigDecimal capacitySnapshot;
    private Instant joinedAt;

    public static GroupMemberResponse fromDomain(GroupMember m) {
        return GroupMemberResponse.builder()
                .userId(m.getUserId().toString())
                .role(m.getRole())
                .inviteStatus(m.getInviteStatus())
                .capacitySnapshot(m.getCapacitySnapshot())
                .joinedAt(m.getJoinedAt())
                .build();
    }
}
