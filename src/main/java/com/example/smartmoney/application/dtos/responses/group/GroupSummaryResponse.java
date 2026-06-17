package com.example.smartmoney.application.dtos.responses.group;

import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.enums.GroupMemberInviteStatus;
import com.example.smartmoney.domain.models.group.enums.GroupMemberRole;
import com.example.smartmoney.domain.models.group.enums.GroupStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupSummaryResponse {

    private String groupId;
    private String name;
    private String description;
    private GroupStatus status;
    private String adminId;
    private int memberCount;
    private GroupMemberRole myRole;
    private Instant createdAt;

    public static GroupSummaryResponse fromDomain(Group group, UserId actorId) {
        GroupMemberRole myRole = group.getMembers().stream()
                .filter(m -> m.getUserId().equals(actorId)
                        && m.getInviteStatus() == GroupMemberInviteStatus.JOINED)
                .map(m -> m.getRole())
                .findFirst()
                .orElse(GroupMemberRole.MEMBER);

        int memberCount = (int) group.getMembers().stream()
                .filter(m -> m.getInviteStatus() == GroupMemberInviteStatus.JOINED)
                .count();

        return GroupSummaryResponse.builder()
                .groupId(group.getId().value().toString())
                .name(group.getName())
                .description(group.getDescription())
                .status(group.getStatus())
                .adminId(group.getAdminId().value().toString())
                .memberCount(memberCount)
                .myRole(myRole)
                .createdAt(group.getCreatedAt())
                .build();
    }
}
