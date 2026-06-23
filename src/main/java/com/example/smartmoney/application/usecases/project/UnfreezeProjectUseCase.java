package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.responses.project.ProjectDetailResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lifts a project freeze. The domain enforces that the owner has zero arrears
 * (i.e. they've caught up) before unfreezing succeeds.
 */
@Service
@RequiredArgsConstructor
public class UnfreezeProjectUseCase {

    private final ProjectRepository projectRepository;
    private final SafeSpendingService safeSpendingService;

    @Transactional
    public ProjectDetailResponse execute(String actorId, String projectId) {
        Project p = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found",
                        ProjectErrorCode.PROJECT_NOT_FOUND));
        UserId actor = UserId.of(actorId);
        p.unfreeze(actor);
        Project saved = projectRepository.save(p);

        // Reactivating the project re-introduces its obligation into the
        // owner's safe-spending calculation.
        safeSpendingService.recalculateAndSaveSafeSpending(actor);

        return ProjectDetailResponse.fromDomain(saved);
    }
}