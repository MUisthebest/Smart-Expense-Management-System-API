package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.UpdateProjectRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectDetailResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UpdateProjectUseCase {

    private final ProjectRepository projectRepository;
    private final ProjectTrackingRepository projectTrackingRepository;
    // private final UserIncomeRepository userIncomeRepository;  // Disabled: user_income table removed
    private final SafeSpendingService safeSpendingService;

    @Transactional
    public ProjectDetailResponse execute(String actorId, String projectId, UpdateProjectRequest req) {
        Project p = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        validateUpdate(actorId, p, req);

        Money target = null;
        if (req.getTargetAmount() != null) {
            String currency = req.getCurrency() != null ? req.getCurrency() : p.getTargetAmount().currency();
            target = new Money(req.getTargetAmount(), currency);
        }

        p.updateInfo(UserId.of(actorId), req.getName(), req.getDescription(), target, req.getDeadline());
        if (req.getStatus() != null) {
            p.updateStatus(UserId.of(actorId), req.getStatus());
        }
        if (req.getPriority() != null) {
            p.updatePriority(UserId.of(actorId), req.getPriority());
        }

        Project saved = projectRepository.save(p);

        if (req.getTargetAmount() != null || req.getDeadline() != null) {
            recalibrateTracking(saved);
        }

        safeSpendingService.recalculateAndSaveSafeSpending(UserId.of(actorId));

        return ProjectDetailResponse.fromDomain(saved);
    }

    private void validateUpdate(String actorId, Project existing, UpdateProjectRequest req) {
        // Disabled: user_income validation removed (user_income table removed)
        // Income check and 20% monthly obligation rule are no longer enforced

        // Compute post-update values
        ProjectStatus nextStatus = req.getStatus() != null ? req.getStatus() : existing.getStatus();
        ProjectPriority nextPriority = req.getPriority() != null ? req.getPriority() : existing.getPriority();
        if (nextPriority == null) nextPriority = ProjectPriority.MEDIUM;

        LocalDate nextDeadline = req.getDeadline() != null ? req.getDeadline() : existing.getDeadline();

        // Deadline must be within 60 months (update-time rule too)
        int monthsToDeadline = monthsToDeadline(LocalDate.now(), nextDeadline);
        if (monthsToDeadline > 60) {
            throw new ProjectException(
                    "Max deadline is 60 months",
                    ProjectErrorCode.PROJECT_INVALID_DEADLINE
            );
        }

        // Only enforce ACTIVE-project constraints when the resulting status is ACTIVE.
        if (nextStatus != ProjectStatus.ACTIVE) {
            return;
        }

        UserId actor = UserId.of(actorId);
        List<Project> all = projectRepository.findAllByUser(actor);

        List<Project> activeOwnedOther = all.stream()
                .filter(p -> actor.equals(p.getOwnerId()))
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE)
                .filter(p -> !p.getId().equals(existing.getId()))
                .toList();

        // Max 3 ACTIVE projects (including this one)
        if (activeOwnedOther.size() >= 3) {
            throw new ProjectException(
                    "Max 3 ACTIVE projects allowed",
                    ProjectErrorCode.PROJECT_MAX_ACTIVE_REACHED
            );
        }

        // Each ACTIVE project must have a unique priority
        Set<ProjectPriority> existingPriorities = new HashSet<>();
        for (Project p : activeOwnedOther) {
            ProjectPriority pr = p.getPriority();
            if (!existingPriorities.add(pr)) {
                throw new ProjectException(
                        "Each ACTIVE project must have a unique priority (fix existing duplicates first)",
                        ProjectErrorCode.PROJECT_ACTIVE_PRIORITY_CONFLICT
                );
            }
        }

        if (existingPriorities.contains(nextPriority)) {
            throw new ProjectException(
                    "Priority already used by another ACTIVE project",
                    ProjectErrorCode.PROJECT_ACTIVE_PRIORITY_CONFLICT
            );
        }
    }

    private int monthsToDeadline(LocalDate today, LocalDate deadline) {
        if (deadline == null) {
            throw new ProjectException("Deadline is required", ProjectErrorCode.PROJECT_INVALID_DEADLINE);
        }
        if (deadline.isBefore(today)) {
            throw new ProjectException("Deadline must be today or later", ProjectErrorCode.PROJECT_INVALID_DEADLINE);
        }

        int months = (deadline.getYear() - today.getYear()) * 12 + (deadline.getMonthValue() - today.getMonthValue()) + 1;
        return Math.max(1, months);
    }

    private BigDecimal monthlyTarget(BigDecimal targetAmount, LocalDate deadline) {
        if (targetAmount == null) return BigDecimal.ZERO;
        int months = monthsToDeadline(LocalDate.now(), deadline);
        return targetAmount.divide(new BigDecimal(months), 2, RoundingMode.CEILING);
    }

    private void recalibrateTracking(Project project) {
        Optional<ProjectTracking> opt =
                projectTrackingRepository.findByProjectId(project.getId());

        if (opt.isEmpty()) {
            return;
        }

        ProjectTracking tracking = opt.get();

        LocalDate today    = LocalDate.now();
        LocalDate deadline = project.getDeadline();
        int newMonthLeft   = monthsToDeadline(today, deadline);

        BigDecimal remaining = project.getTargetAmount().amount()
                .subtract(tracking.getMoneySaved());

        BigDecimal newMonthlySaving;
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            tracking.recalibrate(BigDecimal.ZERO.setScale(2), 0);
            newMonthlySaving = BigDecimal.ZERO;
        } else {
            newMonthlySaving = remaining.divide(
                    BigDecimal.valueOf(newMonthLeft), 2, RoundingMode.CEILING);

            // Disabled: 20% income obligation check removed (user_income table removed)
            // userIncomeRepository.findByUserId(ownerId.value()).ifPresent(income ->
            //         MonthlyObligationPolicy.assertWithinLimit(income, otherActive, newMonthlySaving)
            // );

            tracking.recalibrate(newMonthlySaving, newMonthLeft);
        }

        projectTrackingRepository.save(tracking);
    }
}
