package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectHistoryRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Deletes a project owned by the actor. Behaviour depends on whether the project
 * is standalone or part of a group:
 * <ul>
 *   <li><b>Standalone personal project</b> (no {@code groupProjectId}) — a true
 *       hard delete: its history snapshots, tracking row, contributions and the
 *       project itself are physically removed. Nothing shared depends on it.</li>
 *   <li><b>Sub-personal project</b> (belongs to a group project) — abandoned, not
 *       deleted, so the group's completion math and member roster stay consistent
 *       (the member shows as a clean drop-out rather than a missing row).</li>
 * </ul>
 *
 * <p>Either path releases the owner's obligation by recalculating safe spending.
 * The all-auto model holds no real money, so there is nothing to refund. See
 * docs/project-tracking-all-auto-plan.md §3.4 / §7.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeleteProjectUseCase {

    private final ProjectRepository projectRepository;
    private final ProjectTrackingRepository projectTrackingRepository;
    private final ProjectHistoryRepository projectHistoryRepository;
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
                    "Only owner can delete project",
                    ProjectErrorCode.FORBIDDEN
            );
        }

        if (project.getGroupProjectId() == null) {
            // Standalone personal project — hard delete. Remove dependent rows first
            // (history, tracking), then the project (which also clears contributions).
            projectHistoryRepository.deleteByProjectId(pid);
            projectTrackingRepository.deleteByProjectId(pid);
            projectRepository.deleteById(pid);
            log.info("Hard-deleted standalone personal project {}", projectId);
        } else {
            // Sub-personal project — abandon instead, to keep the group consistent.
            if (project.getStatus() == ProjectStatus.ABANDONED) {
                throw new ProjectException(
                        "Project already abandoned",
                        ProjectErrorCode.PROJECT_ABANDONED
                );
            }
            project.abandon(actor);
            projectRepository.save(project);
            log.info("Abandoned sub-personal project {} (group {})",
                    projectId, project.getGroupProjectId());
        }

        // Release the obligation: recompute the owner's safe spending now that this
        // project no longer counts toward monthly saving / debt.
        try {
            safeSpendingService.recalculateAndSaveSafeSpending(actor);
        } catch (Exception ex) {
            log.warn("Failed to recalculate safe spending for user {} after deleting {}: {}",
                    actor.value(), projectId, ex.getMessage());
        }
    }
}
