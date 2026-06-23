package com.example.smartmoney.application.usecases.project;

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

@Service
@RequiredArgsConstructor
public class DeleteProjectUseCase {

    private final ProjectRepository projectRepository;
    private final SafeSpendingService safeSpendingService;

    @Transactional
    public void execute(String actorId, String projectId) {

        ProjectId pid = new ProjectId(java.util.UUID.fromString(projectId));
        UserId actor = UserId.of(actorId);

        Project project = projectRepository.findById(pid)
                .orElseThrow(() -> new ProjectException(
                        "Project not found",
                        ProjectErrorCode.PROJECT_NOT_FOUND
                ));

        // Chỉ owner được xoá
        if (!project.getOwnerId().equals(actor)) {
            throw new ProjectException(
                    "Only owner can delete project",
                    ProjectErrorCode.FORBIDDEN
            );
        }
        projectRepository.deleteById(pid);

        safeSpendingService.recalculateAndSaveSafeSpending(actor);
    }
}