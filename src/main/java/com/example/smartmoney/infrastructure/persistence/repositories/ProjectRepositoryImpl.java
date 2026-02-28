package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.JoinStatus;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectContributionEntity;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectEntity;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectMemberEntity;
import com.example.smartmoney.infrastructure.persistence.mappers.ProjectContributionMapper;
import com.example.smartmoney.infrastructure.persistence.mappers.ProjectEntityMapper;
import com.example.smartmoney.infrastructure.persistence.mappers.ProjectMemberMapper;
import com.example.smartmoney.infrastructure.persistence.mappers.ProjectPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class ProjectRepositoryImpl implements ProjectRepository {

    private final JpaProjectRepository jpaProjectRepository;
    private final JpaProjectMemberRepository jpaProjectMemberRepository;
    private final JpaProjectContributionRepository jpaProjectContributionRepository;

    private final ProjectPersistenceMapper projectPersistenceMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final ProjectContributionMapper projectContributionMapper;
    private final ProjectEntityMapper projectEntityMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<Project> findById(ProjectId id) {
        Optional<ProjectEntity> peOpt = jpaProjectRepository.findById(id.value());
        if (peOpt.isEmpty()) return Optional.empty();

        UUID projectId = id.value();
        List<ProjectMemberEntity> members = jpaProjectMemberRepository.findByProjectId(projectId);
        List<ProjectContributionEntity> contributions =
                jpaProjectContributionRepository.findByProjectIdOrderByContributionDateDescCreatedAtDesc(projectId);

        return Optional.of(projectPersistenceMapper.toDomain(peOpt.get(), members, contributions));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Project> findAllByUser(UserId userId) {
        Long uid = userId.value();

        List<ProjectEntity> owned = jpaProjectRepository.findByOwnerId(uid);

        List<UUID> memberProjectIds =
                jpaProjectMemberRepository.findProjectIdsByUserIdAndJoinStatus(uid, JoinStatus.JOINED);

        Set<UUID> allIds = new LinkedHashSet<>();
        for (ProjectEntity e : owned) allIds.add(e.getId());
        allIds.addAll(memberProjectIds);

        if (allIds.isEmpty()) return List.of();

        List<ProjectEntity> projects = jpaProjectRepository.findAllById(allIds);

        List<Project> result = new ArrayList<>();
        for (ProjectEntity p : projects) {
            UUID pid = p.getId();
            List<ProjectMemberEntity> members = jpaProjectMemberRepository.findByProjectId(pid);
            List<ProjectContributionEntity> contributions =
                    jpaProjectContributionRepository.findByProjectIdOrderByContributionDateDescCreatedAtDesc(pid);

            result.add(projectPersistenceMapper.toDomain(p, members, contributions));
        }
        return result;
    }

    @Override
    @Transactional
    public Project save(Project project) {
        Instant now = Instant.now();
        UUID projectId = project.getId().value();

        ProjectEntity entity = jpaProjectRepository.findById(projectId)
                .orElseGet(() -> {
                    ProjectEntity e = projectEntityMapper.toEntity(project);
                    e.setCreatedAt(now);
                    e.setUpdatedAt(now);
                    return e;
                });

        if (entity.getCreatedAt() == null) entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

// update fields
        projectEntityMapper.updateEntity(entity, project);

        ProjectEntity saved = jpaProjectRepository.save(entity);

        // MVP: replace-all collections (members/contributions)
        jpaProjectMemberRepository.deleteByProjectId(saved.getId());
        jpaProjectContributionRepository.deleteByProjectId(saved.getId());

        if (!project.getMembers().isEmpty()) {
            List<ProjectMemberEntity> memberEntities = project.getMembers().stream()
                    .map(m -> projectMemberMapper.toEntity(m, saved.getId(), now))
                    .toList();
            jpaProjectMemberRepository.saveAll(memberEntities);
        }

        if (!project.getContributions().isEmpty()) {
            List<ProjectContributionEntity> contributionEntities = project.getContributions().stream()
                    .map(c -> projectContributionMapper.toEntity(c, saved.getId()))
                    .toList();
            jpaProjectContributionRepository.saveAll(contributionEntities);
        }

        return findById(new ProjectId(saved.getId()))
                .orElseThrow(() -> new ProjectException("Project not found after save", ProjectErrorCode.PROJECT_NOT_FOUND));
    }

    @Override
    @Transactional
    public void deleteById(ProjectId id) {
        UUID projectId = id.value();
        jpaProjectContributionRepository.deleteByProjectId(projectId);
        jpaProjectMemberRepository.deleteByProjectId(projectId);
        jpaProjectRepository.deleteById(projectId);
    }

}
