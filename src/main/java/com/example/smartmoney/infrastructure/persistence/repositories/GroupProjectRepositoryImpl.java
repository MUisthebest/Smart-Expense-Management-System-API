package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.enums.GroupProjectStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupProjectId;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.infrastructure.persistence.entities.GroupProjectEntity;
import com.example.smartmoney.infrastructure.persistence.mappers.GroupProjectPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class GroupProjectRepositoryImpl implements GroupProjectRepository {

    private final JpaGroupProjectRepository jpaGroupProjectRepository;
    private final GroupProjectPersistenceMapper groupProjectPersistenceMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<GroupProject> findById(GroupProjectId id) {
        return jpaGroupProjectRepository.findById(id.value())
                .map(groupProjectPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GroupProject> findByGroupId(UUID groupId) {
        return jpaGroupProjectRepository.findByGroupId(groupId)
                .map(groupProjectPersistenceMapper::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupProject> findAllByStatus(GroupProjectStatus status) {
        return jpaGroupProjectRepository.findByStatus(status).stream()
                .map(groupProjectPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public GroupProject save(GroupProject groupProject) {
        Instant now = Instant.now();
        UUID id = groupProject.getId().value();

        GroupProjectEntity entity = jpaGroupProjectRepository.findById(id)
                .orElseGet(() -> {
                    GroupProjectEntity e = groupProjectPersistenceMapper.toEntity(groupProject);
                    e.setCreatedAt(now);
                    e.setUpdatedAt(now);
                    return e;
                });

        entity.setName(groupProject.getName());
        entity.setDescription(groupProject.getDescription());
        entity.setTargetAmount(groupProject.getTargetAmount());
        entity.setTotalCapacity(groupProject.getTotalCapacity());
        entity.setCurrency(groupProject.getCurrency());
        entity.setTotalMonths(groupProject.getTotalMonths());
        entity.setDeadline(groupProject.getDeadline());
        entity.setStatus(groupProject.getStatus());
        entity.setUpdatedAt(now);

        GroupProjectEntity saved = jpaGroupProjectRepository.save(entity);
        return groupProjectPersistenceMapper.toDomain(saved);
    }
}
