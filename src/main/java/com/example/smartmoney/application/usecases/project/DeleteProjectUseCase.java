package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.services.ProjectRefundService;
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

import java.math.BigDecimal;
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
 * <p>Either path releases the owner's obligation by recalculating safe spending, and
 * either path refunds the project's contribution expenses via {@link ProjectRefundService}
 * — under press-to-contribute the ledger holds real money against the project, so
 * cancelling it must give that money back. (The pre-pivot all-auto model recorded no
 * contributions and so had nothing to refund; that is no longer true.)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeleteProjectUseCase {

    private final ProjectRepository projectRepository;
    private final ProjectTrackingRepository projectTrackingRepository;
    private final ProjectHistoryRepository projectHistoryRepository;
    private final SafeSpendingService safeSpendingService;
    private final ProjectRefundService projectRefundService;

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

        boolean subPersonal = project.getGroupProjectId() != null;

        // Hoisted out of the sub-personal branch so it runs before the refund below —
        // an already-abandoned project was refunded when it was abandoned, and must not
        // reach the ledger again.
        if (subPersonal && project.getStatus() == ProjectStatus.ABANDONED) {
            throw new ProjectException(
                    "Project already abandoned",
                    ProjectErrorCode.PROJECT_ABANDONED
            );
        }

        // Give the contributed money back before anything mutates the project. Order matters:
        // abandon() overwrites the status that the "COMPLETED is not refundable" rule reads, so
        // refunding afterwards would wrongly reverse a completed project's spending. Deleting the
        // project does not touch its ledger rows, so the standalone branch is safe either way.
        BigDecimal refunded = projectRefundService.refundOnCancellation(project);

        if (!subPersonal) {
            // Standalone personal project — hard delete. Remove dependent rows first
            // (history, tracking), then the project (which also clears contributions).
            projectHistoryRepository.deleteByProjectId(pid);
            projectTrackingRepository.deleteByProjectId(pid);
            projectRepository.deleteById(pid);
            log.info("Hard-deleted standalone personal project {} (refunded {})", projectId, refunded);
        } else {
            // Sub-personal project — abandon instead, to keep the group consistent.
            project.abandon(actor);
            projectRepository.save(project);
            log.info("Abandoned sub-personal project {} (group {}, refunded {})",
                    projectId, project.getGroupProjectId(), refunded);
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
