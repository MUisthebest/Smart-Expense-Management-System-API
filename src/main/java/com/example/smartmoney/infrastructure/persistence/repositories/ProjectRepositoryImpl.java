package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.application.dtos.requests.project.ProjectFilterRequest;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectContributionEntity;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectEntity;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectTrackingEntity;
import com.example.smartmoney.infrastructure.persistence.mappers.ProjectContributionMapper;
import com.example.smartmoney.infrastructure.persistence.mappers.ProjectEntityMapper;
import com.example.smartmoney.infrastructure.persistence.mappers.ProjectPersistenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ProjectRepositoryImpl implements ProjectRepository {

    private final JpaProjectRepository jpaProjectRepository;
    private final JpaProjectContributionRepository jpaProjectContributionRepository;
    private final JpaProjectTrackingRepository jpaProjectTrackingRepository;

    private final ProjectPersistenceMapper projectPersistenceMapper;
    private final ProjectContributionMapper projectContributionMapper;
    private final ProjectEntityMapper projectEntityMapper;

    @Override
    @Transactional(readOnly = true)
    public Optional<Project> findById(ProjectId id) {
        Optional<ProjectEntity> peOpt = jpaProjectRepository.findById(id.value());
        if (peOpt.isEmpty()) return Optional.empty();

        UUID projectId = id.value();
        List<ProjectContributionEntity> contributions =
                jpaProjectContributionRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        ProjectTrackingEntity tracking =
                jpaProjectTrackingRepository.findByProjectId(projectId).orElse(null);

        return Optional.of(projectPersistenceMapper.toDomain(peOpt.get(), contributions, tracking));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Project> findAllByUser(UserId userId) {
        return findAllByUser(userId, new ProjectFilterRequest(null, null, null, null));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Project> findAllByUser(UserId userId, ProjectFilterRequest filter) {
        UUID uid = userId.value();

        List<ProjectEntity> owned = jpaProjectRepository.findByOwnerId(uid);
        Set<UUID> allIds = new LinkedHashSet<>();
        for (ProjectEntity e : owned) allIds.add(e.getId());

        if (allIds.isEmpty()) return List.of();

        String search = filter.search();
        if (search != null && !search.isBlank()) {
            search = "%" + search.trim().toLowerCase() + "%";
        } else {
            search = null;
        }

        List<ProjectEntity> projects = jpaProjectRepository.searchByIdsAndFilter(
                allIds, search, filter.type(), filter.status(), filter.priority());

        List<Project> result = new ArrayList<>();
        for (ProjectEntity p : projects) {
            ProjectTrackingEntity tracking =
                    jpaProjectTrackingRepository.findByProjectId(p.getId()).orElse(null);
            result.add(projectPersistenceMapper.toDomain(p, null, tracking));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Project> findAllByStatus(ProjectStatus status) {
        List<ProjectEntity> entities = jpaProjectRepository.findByStatus(status);
        List<Project> result = new ArrayList<>();
        for (ProjectEntity p : entities) {
            ProjectTrackingEntity tracking =
                    jpaProjectTrackingRepository.findByProjectId(p.getId()).orElse(null);
            result.add(projectPersistenceMapper.toDomain(p, null, tracking));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Project> findAllByGroupProjectId(UUID groupProjectId) {
        List<ProjectEntity> entities = jpaProjectRepository.findByGroupProjectId(groupProjectId);
        List<Project> result = new ArrayList<>();
        for (ProjectEntity p : entities) {
            ProjectTrackingEntity tracking =
                    jpaProjectTrackingRepository.findByProjectId(p.getId()).orElse(null);
            result.add(projectPersistenceMapper.toDomain(p, null, tracking));
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
        projectEntityMapper.updateEntity(entity, project);
        entity.setGroupProjectId(project.getGroupProjectId());

        ProjectEntity saved = jpaProjectRepository.save(entity);
        syncContributions(project, saved.getId());

        return findById(new ProjectId(saved.getId()))
                .orElseThrow(() -> new ProjectException("Project not found after save",
                        ProjectErrorCode.PROJECT_NOT_FOUND));
    }

    @Override
    @Transactional
    public void deleteById(ProjectId id) {
        UUID projectId = id.value();
        jpaProjectContributionRepository.deleteByProjectId(projectId);
        jpaProjectRepository.deleteById(projectId);
    }

    private void syncContributions(Project project, UUID projectId) {
        List<ProjectContributionEntity> incoming = project.getContributions().stream()
                .map(c -> projectContributionMapper.toEntity(c, projectId))
                .toList();

        Set<UUID> incomingIds = incoming.stream()
                .map(ProjectContributionEntity::getId)
                .collect(Collectors.toSet());

        List<ProjectContributionEntity> existing =
                jpaProjectContributionRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        List<UUID> toDelete = existing.stream()
                .map(ProjectContributionEntity::getId)
                .filter(eid -> !incomingIds.contains(eid))
                .toList();

        if (!toDelete.isEmpty()) jpaProjectContributionRepository.deleteAllById(toDelete);
        if (!incoming.isEmpty()) jpaProjectContributionRepository.saveAll(incoming);
    }
}
