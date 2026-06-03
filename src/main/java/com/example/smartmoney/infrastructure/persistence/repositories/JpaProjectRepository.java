package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface JpaProjectRepository extends JpaRepository<ProjectEntity, UUID> {
    List<ProjectEntity> findByOwnerId(UUID ownerId);
    List<ProjectEntity> findByStatus(ProjectStatus status);

    @Query("""
     SELECT p
     FROM ProjectEntity p
     WHERE p.id IN :ids
       AND (
           :search IS NULL
           OR LOWER(p.name) LIKE :search
           OR LOWER(p.description) LIKE :search
       )
       AND (:type IS NULL OR p.type = :type)
       AND (:status IS NULL OR p.status = :status)
       AND (:priority IS NULL OR p.priority = :priority)
     ORDER BY p.createdAt DESC
""")
    List<ProjectEntity> searchByIdsAndFilter(
            @Param("ids") Collection<UUID> ids,
            @Param("search") String search,
            @Param("type") ProjectType type,
            @Param("status") ProjectStatus status,
            @Param("priority") ProjectPriority priority
    );
}
