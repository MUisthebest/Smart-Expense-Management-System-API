package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.infrastructure.persistence.entities.ProjectHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaProjectHistoryRepository extends JpaRepository<ProjectHistoryEntity, UUID> {
    List<ProjectHistoryEntity> findByProjectIdOrderByYearDescMonthDesc(UUID projectId);
    void deleteByProjectId(UUID projectId);
}
