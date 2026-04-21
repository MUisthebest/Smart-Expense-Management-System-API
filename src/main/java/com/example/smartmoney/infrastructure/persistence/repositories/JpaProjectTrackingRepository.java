package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.infrastructure.persistence.entities.ProjectTrackingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface JpaProjectTrackingRepository extends JpaRepository<ProjectTrackingEntity, UUID> {
    Optional<ProjectTrackingEntity> findByProjectId(UUID projectId);
    void deleteByProjectId(UUID projectId);
}
