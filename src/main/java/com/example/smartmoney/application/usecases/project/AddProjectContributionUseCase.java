package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.AddProjectContributionRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectDetailResponse;
import com.example.smartmoney.application.usecases.group.GroupProjectCompletionService;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Press-to-contribute (Pivot B). Pressing "contribute" is one atomic operation:
 * it writes a real EXPENSE ledger {@link Transaction} (category PROJECT_CONTRIBUTION,
 * carrying the project id) <b>and</b> appends the {@link com.example.smartmoney.domain.models.project.ProjectContribution}
 * to the project. Because both happen in a single {@link Transactional} unit, the
 * project's progress can never diverge from the user's actual money.
 *
 * <p>Completion is synchronous: if the contribution brings total contributed to or past
 * the target, the project is marked completed here — no cron.
 * See docs/redesign-technical-implementation.md §5.3–5.4.
 */
@Service
@RequiredArgsConstructor
public class AddProjectContributionUseCase {

    private final ProjectRepository projectRepository;
    private final TransactionRepository transactionRepository;
    private final GroupProjectCompletionService groupProjectCompletionService;

    @Transactional
    public ProjectDetailResponse execute(String actorId, String projectId, AddProjectContributionRequest request) {
        Project project = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found",
                        ProjectErrorCode.PROJECT_NOT_FOUND));

        // Lazy-on-read expiry: a deadline-passed project can't be funded. Persist the
        // transition and reject, rather than recording a contribution that can't count.
        if (project.expireIfDeadlinePassed()) {
            projectRepository.save(project);
            throw new ProjectException("Project deadline has passed; it is now expired",
                    ProjectErrorCode.PROJECT_EXPIRED);
        }

        // Domain validates owner, contributable status, and amount > 0 (throws otherwise).
        project.addContribution(UserId.of(actorId), request.getAmount(), request.getNote());

        // Real money leaves spendable into the project — recorded as an EXPENSE that
        // carries the project id so the ledger and project progress stay in lockstep.
        String description = (request.getNote() != null && !request.getNote().isBlank())
                ? request.getNote()
                : "Contribution to project " + project.getName();
        Transaction contributionTx = Transaction.create(
                UUID.fromString(actorId),
                new Amount(request.getAmount()),
                TransactionType.EXPENSE,
                Category.PROJECT_CONTRIBUTION,
                description,
                LocalDateTime.now(),
                UUID.fromString(projectId));
        transactionRepository.save(contributionTx);

        // Synchronous completion: target reached → complete now (no settlement cron).
        if (project.totalContributed().amount().compareTo(project.getTargetAmount().amount()) >= 0) {
            project.complete();
        }

        Project saved = projectRepository.save(project);

        // A contribution to a sub-personal project may complete (or, after lazy sub-expiry,
        // expire) its parent group project — evaluate it now, off the settlement cron.
        if (saved.isSubPersonal()) {
            groupProjectCompletionService.evaluateById(saved.getGroupProjectId());
        }

        return ProjectDetailResponse.fromDomain(saved);
    }
}
