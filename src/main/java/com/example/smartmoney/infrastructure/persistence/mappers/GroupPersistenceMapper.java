package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupMember;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.infrastructure.persistence.entities.GroupEntity;
import com.example.smartmoney.infrastructure.persistence.entities.GroupMemberEntity;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class GroupPersistenceMapper {

    public Group toDomain(GroupEntity entity, List<GroupMemberEntity> memberEntities) {
        List<GroupMember> members = memberEntities == null ? List.of() : memberEntities.stream()
                .map(this::memberToDomain)
                .toList();

        return Group.rehydrate(
                new GroupId(entity.getId()),
                entity.getName(),
                entity.getDescription(),
                new UserId(entity.getAdminId()),
                entity.getStatus(),
                members,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public GroupMember memberToDomain(GroupMemberEntity e) {
        return new GroupMember(
                e.getId(),
                new UserId(e.getUserId()),
                e.getRole(),
                e.getInviteStatus(),
                e.getCapacitySnapshot(),
                e.getJoinedAt(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    public GroupEntity toEntity(Group group) {
        return GroupEntity.builder()
                .id(group.getId().value())
                .name(group.getName())
                .description(group.getDescription())
                .adminId(group.getAdminId().value())
                .status(group.getStatus())
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .build();
    }

    public GroupMemberEntity toMemberEntity(GroupMember member, UUID groupId) {
        return GroupMemberEntity.builder()
                .id(member.getId())
                .groupId(groupId)
                .userId(member.getUserId().value())
                .role(member.getRole())
                .inviteStatus(member.getInviteStatus())
                .capacitySnapshot(member.getCapacitySnapshot())
                .joinedAt(member.getJoinedAt())
                .createdAt(member.getCreatedAt() != null ? member.getCreatedAt() : Instant.now())
                .updatedAt(member.getUpdatedAt() != null ? member.getUpdatedAt() : Instant.now())
                .build();
    }
}
