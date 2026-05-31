package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;

import java.util.Optional;

public interface ProjectTrackingRepository {
    ProjectTracking save(ProjectTracking tracking);
    Optional<ProjectTracking> findByProjectId(ProjectId projectId);
    void deleteByProjectId(ProjectId projectId);
}
