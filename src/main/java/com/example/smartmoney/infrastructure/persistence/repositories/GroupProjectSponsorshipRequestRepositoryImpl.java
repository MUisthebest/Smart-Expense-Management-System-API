package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.group.GroupProjectSponsorshipRequest;
import com.example.smartmoney.domain.repositories.GroupProjectSponsorshipRequestRepository;
import com.example.smartmoney.infrastructure.persistence.entities.GroupProjectSponsorshipRequestEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class GroupProjectSponsorshipRequestRepositoryImpl implements GroupProjectSponsorshipRequestRepository {

    private final JpaGroupProjectSponsorshipRequestRepository jpaRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<GroupProjectSponsorshipRequest> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupProjectSponsorshipRequest> findAllByGroupProjectId(UUID groupProjectId) {
        return jpaRepository.findAllByGroupProjectId(groupProjectId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupProjectSponsorshipRequest> findAllByUserIdAndStatus(UUID userId, String status) {
        return jpaRepository.findAllByUserIdAndStatus(userId, status).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public GroupProjectSponsorshipRequest save(GroupProjectSponsorshipRequest request) {
        GroupProjectSponsorshipRequestEntity entity = toEntity(request);
        GroupProjectSponsorshipRequestEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    @Transactional
    public void saveAll(List<GroupProjectSponsorshipRequest> requests) {
        List<GroupProjectSponsorshipRequestEntity> entities = requests.stream()
                .map(this::toEntity)
                .toList();
        jpaRepository.saveAll(entities);
    }

    @Override
    @Transactional
    public void deleteAll(List<GroupProjectSponsorshipRequest> requests) {
        List<GroupProjectSponsorshipRequestEntity> entities = requests.stream()
                .map(this::toEntity)
                .toList();
        jpaRepository.deleteAll(entities);
        jpaRepository.flush();
    }

    private GroupProjectSponsorshipRequest toDomain(GroupProjectSponsorshipRequestEntity entity) {
        return new GroupProjectSponsorshipRequest(
                entity.getId(),
                entity.getGroupProjectId(),
                entity.getUserId(),
                entity.getAskedAmount(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private GroupProjectSponsorshipRequestEntity toEntity(GroupProjectSponsorshipRequest domain) {
        return GroupProjectSponsorshipRequestEntity.builder()
                .id(domain.getId())
                .groupProjectId(domain.getGroupProjectId())
                .userId(domain.getUserId())
                .askedAmount(domain.getAskedAmount())
                .status(domain.getStatus())
                .createdAt(domain.getCreatedAt() != null ? domain.getCreatedAt() : Instant.now())
                .updatedAt(domain.getUpdatedAt() != null ? domain.getUpdatedAt() : Instant.now())
                .build();
    }
}
