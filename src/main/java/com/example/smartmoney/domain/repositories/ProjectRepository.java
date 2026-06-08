package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.application.dtos.requests.project.ProjectFilterRequest;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository {
    Optional<Project> findById(ProjectId id);
    List<Project> findAllByUser(UserId userId);
    List<Project> findAllByUser(UserId userId, ProjectFilterRequest filter);
    List<Project> findAllByStatus(ProjectStatus status);
    Project save(Project project);
    void deleteById(ProjectId id);
}