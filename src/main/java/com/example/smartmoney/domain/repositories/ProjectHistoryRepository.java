package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.project.ProjectHistory;

import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import java.util.List;

public interface ProjectHistoryRepository {
    ProjectHistory save(ProjectHistory history);
    List<ProjectHistory> findAllByProjectId(ProjectId projectId);
    void deleteByProjectId(ProjectId projectId);
}
