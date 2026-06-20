package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Abandons a project (semantic of the legacy DELETE endpoint). Rather than
 * physically removing the row — which would lose audit trail — this:
 * <ol>
 *   <li>marks the project ABANDONED (terminal state, no further mutations); and</li>
 *   <li>recalculates the owner's safe spending, so the obligation previously
 *       locked by this project (monthly saving + any debt) is released.</li>
 * </ol>
 *
 * <p>The all-auto model holds no real money and records no contributions, so there
 * is nothing to refund — abandonment is purely an obligation release. See
 * docs/project-tracking-all-auto-plan.md §3.4 / §7.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeleteProjectUseCase {

    private final ProjectRepository projectRepository;
    private final SafeSpendingService safeSpendingService;

    @Transactional
    public void execute(String actorId, String projectId) {

        ProjectId pid = new ProjectId(UUID.fromString(projectId));
        UserId actor = UserId.of(actorId);

        Project project = projectRepository.findById(pid)
                .orElseThrow(() -> new ProjectException(
                        "Project not found",
                        ProjectErrorCode.PROJECT_NOT_FOUND
                ));

        if (!project.getOwnerId().equals(actor)) {
            throw new ProjectException(
                    "Only owner can abandon project",
                    ProjectErrorCode.FORBIDDEN
            );
        }

        if (project.getStatus() == ProjectStatus.ABANDONED) {
            throw new ProjectException(
                    "Project already abandoned",
                    ProjectErrorCode.PROJECT_ABANDONED
            );
        }

        // Flip to ABANDONED via the domain method (preserves all invariants) and persist.
        project.abandon(actor);
        projectRepository.save(project);

        // Release the obligation: recompute the owner's safe spending now that this
        // project no longer counts toward monthly saving / debt.
        try {
            safeSpendingService.recalculateAndSaveSafeSpending(actor);
        } catch (Exception ex) {
            log.warn("Failed to recalculate safe spending for user {} after abandoning {}: {}",
                    actor.value(), projectId, ex.getMessage());
        }
    }
}