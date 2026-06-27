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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CreateProjectTrackingUseCase {

    private final ProjectRepository projectRepository;
    private final ProjectTrackingRepository projectTrackingRepository;
    // private final UserIncomeRepository userIncomeRepository;  // Disabled: user_income table removed

    @Value("${app.project.max-frozen-months:3}")
    private int maxFrozenMonths;

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
        BigDecimal monthlySaving = calculateMonthlySaving(project);

        // Disabled: 20% monthly obligation check removed (user_income table removed)
        // userIncomeRepository.findByUserId(ownerId.value()).ifPresent(income ->
        //         MonthlyObligationPolicy.assertWithinLimit(income, otherActive, monthlySaving)
        // );

        ProjectTracking tracking = ProjectTracking.createNew(
                pid,
                req.getMoneySaved(),
                req.getCurrentMonth(),
            monthlySaving,
                req.getMonthLeft(),
                req.getMoneyOwed()
        );

        return ProjectTrackingResponse.fromDomain(projectTrackingRepository.save(tracking), project, maxFrozenMonths);
    }

    private void validateMoneySaved(Project project, BigDecimal moneySaved) {
        if (moneySaved.compareTo(project.getTargetAmount().amount()) >= 0) {
            throw new ProjectException("moneySaved must be less than targetAmount", ProjectErrorCode.PROJECT_TRACKING_INVALID_VALUE);
        }
    }

    private BigDecimal calculateMonthlySaving(Project project) {
        LocalDate deadline = project.getDeadline();
        LocalDate today = LocalDate.now();

        if (deadline == null || deadline.isBefore(today)) {
            throw new ProjectException("Project deadline must be today or later", ProjectErrorCode.PROJECT_TRACKING_INVALID_VALUE);
        }

        int months = (deadline.getYear() - today.getYear()) * 12 + (deadline.getMonthValue() - today.getMonthValue()) + 1;
        int divisor = Math.max(1, months);

        return project.getTargetAmount().amount().divide(BigDecimal.valueOf(divisor), 2, RoundingMode.CEILING);
    }
}
