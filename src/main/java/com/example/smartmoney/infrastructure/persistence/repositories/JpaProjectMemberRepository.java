package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.project.enums.JoinStatus;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;
import java.util.Collection;

public interface JpaProjectMemberRepository extends JpaRepository<ProjectMemberEntity, UUID> {

    List<ProjectMemberEntity> findByProjectId(UUID projectId);

    @Modifying
    @Query("delete from ProjectMemberEntity pm where pm.projectId = :projectId")
    void deleteByProjectId(UUID projectId);

    List<ProjectMemberEntity> findByProjectIdIn(Collection<UUID> projectIds);

    @Query("""
        select pm.projectId
        from ProjectMemberEntity pm
        where pm.userId = :userId and pm.joinStatus = :joinStatus
    """)
    List<UUID> findProjectIdsByUserIdAndJoinStatus(UUID userId, JoinStatus joinStatus);
}
