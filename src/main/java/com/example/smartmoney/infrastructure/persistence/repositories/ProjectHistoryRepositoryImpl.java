package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.project.ProjectHistory;
import com.example.smartmoney.domain.repositories.ProjectHistoryRepository;
import com.example.smartmoney.infrastructure.persistence.mappers.ProjectHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ProjectHistoryRepositoryImpl implements ProjectHistoryRepository {

    private final JpaProjectHistoryRepository jpaProjectHistoryRepository;
    private final ProjectHistoryMapper projectHistoryMapper;

    @Override
    public ProjectHistory save(ProjectHistory history) {
        var entity = projectHistoryMapper.toEntity(history);
        var saved = jpaProjectHistoryRepository.save(entity);
        return projectHistoryMapper.toDomain(saved);
    }

    @Override
    public List<ProjectHistory> findAllByProjectId(ProjectId projectId) {
        return jpaProjectHistoryRepository.findByProjectIdOrderByYearDescMonthDesc(projectId.value())
                .stream()
                .map(projectHistoryMapper::toDomain)
                .toList();
    }

    @Override
    public void deleteByProjectId(ProjectId projectId) {
        jpaProjectHistoryRepository.deleteByProjectId(projectId.value());
    }
}
