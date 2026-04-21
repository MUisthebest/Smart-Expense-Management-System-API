package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.CreateProjectTrackingRequest;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateProjectTrackingUseCase {

    private final ProjectRepository projectRepository;
    private final ProjectTrackingRepository projectTrackingRepository;

    @Transactional
    public ProjectTrackingResponse execute(String actorId, String projectId, CreateProjectTrackingRequest req) {
        Project project = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        UserId actor = UserId.of(actorId);
        ProjectAccessPolicy.requireCanManage(project, actor);

        ProjectId pid = ProjectId.of(projectId);
        if (projectTrackingRepository.findByProjectId(pid).isPresent()) {
            throw new ProjectException("Tracking already exists", ProjectErrorCode.PROJECT_TRACKING_ALREADY_EXISTS);
        }

        validateMoneySaved(project, req.getMoneySaved());

        ProjectTracking tracking = ProjectTracking.createNew(
                pid,
                req.getMoneySaved(),
                req.getCurrentMonth(),
                req.getMonthLeft(),
                req.getMoneyOwed()
        );

        return ProjectTrackingResponse.fromDomain(projectTrackingRepository.save(tracking));
    }

    private void validateMoneySaved(Project project, java.math.BigDecimal moneySaved) {
        if (moneySaved.compareTo(project.getTargetAmount().amount()) >= 0) {
            throw new ProjectException("moneySaved must be less than targetAmount", ProjectErrorCode.PROJECT_TRACKING_INVALID_VALUE);
        }
    }
}
