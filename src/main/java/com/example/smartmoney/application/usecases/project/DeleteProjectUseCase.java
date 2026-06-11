package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectContribution;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Abandons a project (semantic of the legacy DELETE endpoint). Rather than
 * physically removing the row — which would lose audit trail and orphan the
 * contributions — this:
 * <ol>
 *   <li>marks the project ABANDONED (terminal state, no further mutations);</li>
 *   <li>emits one compensating INCOME transaction per past contribution
 *       (category {@code PROJECT_REFUND}) for each contributor; and</li>
 *   <li>recalculates safe spending for every affected user, so the headroom
 *       previously locked by this project becomes available again.</li>
 * </ol>
 *
 * <p>No real-world money moves; this is purely ledger bookkeeping.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DeleteProjectUseCase {

    private final ProjectRepository projectRepository;
    private final TransactionRepository transactionRepository;
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

        // Collect distinct contributor user ids so we can recalc safe spending
        // for each of them (the owner is included if they ever contributed).
        Set<UUID> affectedUserIds = new HashSet<>();
        affectedUserIds.add(actor.value());

        String projectName = project.getName();

        for (ProjectContribution c : project.getContributions()) {
            UUID contributorId = c.getUserId().value();
            affectedUserIds.add(contributorId);

            try {
                Transaction refund = Transaction.create(
                        contributorId,
                        new Amount(c.getMoney().amount()),
                        TransactionType.INCOME,
                        Category.PROJECT_REFUND,
                        "Refund: project '" + projectName + "' abandoned",
                        LocalDateTime.now()
                );
                transactionRepository.save(refund);
            } catch (Exception ex) {
                // Defensive: log and continue. A missing user (e.g. deleted)
                // should not block abandonment of the project itself.
                log.warn("Failed to issue PROJECT_REFUND for contributor {} on project {}: {}",
                        contributorId, projectId, ex.getMessage());
            }
        }

        // Flip to ABANDONED via the domain method (preserves all invariants)
        // and persist.
        project.abandon(actor);
        projectRepository.save(project);


        // Recalculate safe spending for every affected user.
        for (UUID uid : affectedUserIds) {
            try {
                safeSpendingService.recalculateAndSaveSafeSpending(new UserId(uid));
            } catch (Exception ex) {
                log.warn("Failed to recalculate safe spending for user {} after abandoning {}: {}",
                        uid, projectId, ex.getMessage());
            }
        }
    }
}