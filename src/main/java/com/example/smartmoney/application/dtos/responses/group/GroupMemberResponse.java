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
    private String username;
    private String email;
    private GroupMemberRole role;
    private GroupMemberInviteStatus inviteStatus;
    private BigDecimal capacitySnapshot;
    private Instant joinedAt;
    private Boolean autoSponsorEnabled;
    private BigDecimal autoSponsorLimit;

    public static GroupMemberResponse fromDomain(GroupMember m) {
        return fromDomain(m, null, null);
    }

    public static GroupMemberResponse fromDomain(GroupMember m, String username) {
        return fromDomain(m, username, null);
    }

    public static GroupMemberResponse fromDomain(GroupMember m, String username, String email) {
        return GroupMemberResponse.builder()
                .userId(m.getUserId().toString())
                .username(username)
                .email(email)
                .role(m.getRole())
                .inviteStatus(m.getInviteStatus())
                .capacitySnapshot(m.getCapacitySnapshot())
                .joinedAt(m.getJoinedAt())
                .autoSponsorEnabled(m.getAutoSponsorEnabled())
                .autoSponsorLimit(m.getAutoSponsorLimit())
                .build();
    }
}
