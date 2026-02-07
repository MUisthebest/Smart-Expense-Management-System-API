package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.infrastructure.persistence.entities.ProjectContributionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface JpaProjectContributionRepository extends JpaRepository<ProjectContributionEntity, UUID> {

    List<ProjectContributionEntity> findByProjectIdOrderByContributionDateDescCreatedAtDesc(UUID projectId);

    @Modifying
    @Query("delete from ProjectContributionEntity pc where pc.projectId = :projectId")
    void deleteByProjectId(UUID projectId);
}
