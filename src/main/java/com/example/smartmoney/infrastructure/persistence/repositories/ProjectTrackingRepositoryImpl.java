package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.infrastructure.persistence.mappers.ProjectTrackingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProjectTrackingRepositoryImpl implements ProjectTrackingRepository {

    private final JpaProjectTrackingRepository jpaProjectTrackingRepository;
    private final ProjectTrackingMapper projectTrackingMapper;

    @Override
    public ProjectTracking save(ProjectTracking tracking) {
        var entity = projectTrackingMapper.toEntity(tracking);
        var saved = jpaProjectTrackingRepository.save(entity);
        return projectTrackingMapper.toDomain(saved);
    }

    @Override
    public Optional<ProjectTracking> findByProjectId(ProjectId projectId) {
        return jpaProjectTrackingRepository.findByProjectId(projectId.value())
                .map(projectTrackingMapper::toDomain);
    }

    @Override
    public void deleteByProjectId(ProjectId projectId) {
        jpaProjectTrackingRepository.deleteByProjectId(projectId.value());
    }
}
