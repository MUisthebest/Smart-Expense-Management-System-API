package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.UpdateProjectTrackingRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectTrackingResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class UpdateProjectTrackingUseCase {

    private final ProjectRepository projectRepository;
    private final ProjectTrackingRepository projectTrackingRepository;
    private final SafeSpendingService safeSpendingService;

    @Value("${app.project.max-frozen-months:3}")
    private int maxFrozenMonths;

    @Transactional
    public ProjectTrackingResponse execute(String actorId, String projectId, UpdateProjectTrackingRequest req) {
        Project project = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        UserId actor = UserId.of(actorId);
        ProjectAccessPolicy.requireCanManage(project, actor);

        ProjectTracking tracking = projectTrackingRepository.findByProjectId(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project tracking not found", ProjectErrorCode.PROJECT_TRACKING_NOT_FOUND));

        BigDecimal nextMoneySaved = req.getMoneySaved() != null ? req.getMoneySaved() : tracking.getMoneySaved();
        validateMoneySaved(project, nextMoneySaved);

        BigDecimal oldMoneyOwed = tracking.getMoneyOwed() != null ? tracking.getMoneyOwed() : BigDecimal.ZERO;

        tracking.update(req.getMoneySaved(), req.getCurrentMonth(), req.getMonthLeft(), req.getMoneyOwed());
        ProjectTracking saved = projectTrackingRepository.save(tracking);

        safeSpendingService.recalculateAndSaveSafeSpending(actor);

        return ProjectTrackingResponse.fromDomain(saved, project, maxFrozenMonths);
    }

    private void validateMoneySaved(Project project, BigDecimal moneySaved) {
        if (moneySaved.compareTo(project.getTargetAmount().amount()) >= 0) {
            throw new ProjectException("moneySaved must be less than targetAmount", ProjectErrorCode.PROJECT_TRACKING_INVALID_VALUE);
        }
    }
}
