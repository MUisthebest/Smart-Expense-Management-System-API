package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupMember;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.infrastructure.persistence.entities.GroupEntity;
import com.example.smartmoney.infrastructure.persistence.entities.GroupMemberEntity;
import com.example.smartmoney.infrastructure.persistence.mappers.GroupPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.example.smartmoney.domain.models.group.enums.GroupMemberInviteStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class GroupRepositoryImpl implements GroupRepository {

    private final JpaGroupRepository jpaGroupRepository;
    private final JpaGroupMemberRepository jpaGroupMemberRepository;
    private final GroupPersistenceMapper groupPersistenceMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<Group> findById(GroupId id) {
        return jpaGroupRepository.findById(id.value())
                .map(entity -> {
                    List<GroupMemberEntity> members =
                            jpaGroupMemberRepository.findByGroupId(entity.getId());
                    return groupPersistenceMapper.toDomain(entity, members);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Group> findAllByAdmin(UserId adminId) {
        List<GroupEntity> entities = jpaGroupRepository.findByAdminId(adminId.value());
        List<Group> result = new ArrayList<>();
        for (GroupEntity entity : entities) {
            List<GroupMemberEntity> members =
                    jpaGroupMemberRepository.findByGroupId(entity.getId());
            result.add(groupPersistenceMapper.toDomain(entity, members));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Group> findAllByMember(UserId userId) {
        List<GroupMemberEntity> memberships = jpaGroupMemberRepository.findByUserId(userId.value())
                .stream()
                .filter(m -> m.getInviteStatus() == GroupMemberInviteStatus.JOINED)
                .toList();

        List<Group> result = new ArrayList<>();
        for (GroupMemberEntity membership : memberships) {
            jpaGroupRepository.findById(membership.getGroupId()).ifPresent(entity -> {
                List<GroupMemberEntity> members = jpaGroupMemberRepository.findByGroupId(entity.getId());
                result.add(groupPersistenceMapper.toDomain(entity, members));
            });
        }
        return result;
    }

    @Override
    @Transactional
    public Group save(Group group) {
        Instant now = Instant.now();
        UUID groupId = group.getId().value();

        GroupEntity entity = jpaGroupRepository.findById(groupId)
                .orElseGet(() -> {
                    GroupEntity e = groupPersistenceMapper.toEntity(group);
                    e.setCreatedAt(now);
                    e.setUpdatedAt(now);
                    return e;
                });

        entity.setName(group.getName());
        entity.setDescription(group.getDescription());
        entity.setStatus(group.getStatus());
        entity.setUpdatedAt(now);

        jpaGroupRepository.save(entity);

        syncMembers(group, groupId, now);

        return findById(group.getId())
                .orElseThrow(() -> new IllegalStateException("Group not found after save"));
    }

    private void syncMembers(Group group, UUID groupId, Instant now) {
        List<GroupMember> domainMembers = group.getMembers();
        Set<UUID> incomingIds = domainMembers.stream()
                .map(GroupMember::getId)
                .collect(Collectors.toSet());

        List<GroupMemberEntity> existing = jpaGroupMemberRepository.findByGroupId(groupId);
        List<UUID> toDelete = existing.stream()
                .map(GroupMemberEntity::getId)
                .filter(id -> !incomingIds.contains(id))
                .toList();

        if (!toDelete.isEmpty()) {
            jpaGroupMemberRepository.deleteAllById(toDelete);
        }

        List<GroupMemberEntity> toSave = domainMembers.stream()
                .map(m -> groupPersistenceMapper.toMemberEntity(m, groupId))
                .toList();

        if (!toSave.isEmpty()) {
            jpaGroupMemberRepository.saveAll(toSave);
        }
    }
}
