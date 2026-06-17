package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.group.enums.GroupProjectStatus;
import com.example.smartmoney.infrastructure.persistence.entities.GroupProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JpaGroupProjectRepository extends JpaRepository<GroupProjectEntity, UUID> {
    Optional<GroupProjectEntity> findByGroupId(UUID groupId);
    List<GroupProjectEntity> findByStatus(GroupProjectStatus status);
}
