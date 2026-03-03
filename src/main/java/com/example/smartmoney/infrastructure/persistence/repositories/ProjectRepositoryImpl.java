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
import java.util.stream.Collectors;

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
                jpaProjectContributionRepository.findByProjectIdOrderByCreatedAtDesc(projectId);

        return Optional.of(projectPersistenceMapper.toDomain(peOpt.get(), members, contributions));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Project> findAllByUser(UserId userId) {
        UUID uid = userId.value();

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
                    jpaProjectContributionRepository.findByProjectIdOrderByCreatedAtDesc(pid);

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

        syncMembers(project, saved.getId(), now);
        syncContributions(project, saved.getId());

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

    private void syncMembers(Project project, UUID projectId, Instant now) {
        List<ProjectMemberEntity> existing = jpaProjectMemberRepository.findByProjectId(projectId);
        Map<UUID, ProjectMemberEntity> existingByUserId = existing.stream()
            .collect(Collectors.toMap(ProjectMemberEntity::getUserId, member -> member));

        Map<UUID, ProjectMemberEntity> incomingByUserId = new LinkedHashMap<>();
        for (var member : project.getMembers()) {
            ProjectMemberEntity mapped = projectMemberMapper.toEntity(member, projectId, now);
            UUID userId = mapped.getUserId();

            ProjectMemberEntity old = existingByUserId.get(userId);
            if (old != null) {
            mapped.setId(old.getId());
            mapped.setCreatedAt(old.getCreatedAt());
            }

            incomingByUserId.put(userId, mapped);
        }

        Set<UUID> incomingUserIds = incomingByUserId.keySet();
        List<UUID> toDelete = existing.stream()
            .filter(existingMember -> !incomingUserIds.contains(existingMember.getUserId()))
            .map(ProjectMemberEntity::getId)
                .toList();

        if (!toDelete.isEmpty()) {
            jpaProjectMemberRepository.deleteAllById(toDelete);
        }
        if (!incomingByUserId.isEmpty()) {
            jpaProjectMemberRepository.saveAll(incomingByUserId.values());
        }
    }

    private void syncContributions(Project project, UUID projectId) {
        List<ProjectContributionEntity> incoming = project.getContributions().stream()
                .map(contribution -> projectContributionMapper.toEntity(contribution, projectId))
                .toList();

        Set<UUID> incomingIds = incoming.stream()
                .map(ProjectContributionEntity::getId)
                .collect(Collectors.toSet());

        List<ProjectContributionEntity> existing = jpaProjectContributionRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        List<UUID> toDelete = existing.stream()
                .map(ProjectContributionEntity::getId)
                .filter(existingId -> !incomingIds.contains(existingId))
                .toList();

        if (!toDelete.isEmpty()) {
            jpaProjectContributionRepository.deleteAllById(toDelete);
        }
        if (!incoming.isEmpty()) {
            jpaProjectContributionRepository.saveAll(incoming);
        }
    }

}
