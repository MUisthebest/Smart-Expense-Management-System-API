package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.responses.project.ProjectTrackingResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetProjectTrackingUseCase {

    private final ProjectRepository projectRepository;
    private final ProjectTrackingRepository projectTrackingRepository;

    @Value("${app.project.max-frozen-months:3}")
    private int maxFrozenMonths;

    @Transactional(readOnly = true)
    public ProjectTrackingResponse execute(String actorId, String projectId) {
        Project project = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        UserId actor = UserId.of(actorId);
        ProjectAccessPolicy.requireCanView(project, actor);

        ProjectTracking tracking = projectTrackingRepository.findByProjectId(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project tracking not found", ProjectErrorCode.PROJECT_TRACKING_NOT_FOUND));

        return ProjectTrackingResponse.fromDomain(tracking, project, maxFrozenMonths);
    }
}
