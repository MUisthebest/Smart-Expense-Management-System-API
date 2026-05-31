package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.responses.project.ProjectHistoryResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectHistoryRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetProjectHistoryUseCase {

    private final ProjectRepository projectRepository;
    private final ProjectHistoryRepository projectHistoryRepository;

    @Transactional(readOnly = true)
    public List<ProjectHistoryResponse> execute(String actorId, String projectId) {
        Project project = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        UserId actor = UserId.of(actorId);
        ProjectAccessPolicy.requireCanManage(project, actor);

        return projectHistoryRepository.findAllByProjectId(ProjectId.of(projectId))
                .stream()
                .map(ProjectHistoryResponse::fromDomain)
                .toList();
    }
}
