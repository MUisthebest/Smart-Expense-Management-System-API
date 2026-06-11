package com.example.smartmoney.application.dtos.responses.group;

import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.enums.GroupStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class GroupDetailResponse {
    private String groupId;
    private String name;
    private String description;
    private String adminId;
    private GroupStatus status;
    private List<GroupMemberResponse> members;
    private Instant createdAt;

    public static GroupDetailResponse fromDomain(Group g) {
        return GroupDetailResponse.builder()
                .groupId(g.getId().toString())
                .name(g.getName())
                .description(g.getDescription())
                .adminId(g.getAdminId().toString())
                .status(g.getStatus())
                .members(g.getMembers().stream().map(GroupMemberResponse::fromDomain).toList())
                .createdAt(g.getCreatedAt())
                .build();
    }
}
