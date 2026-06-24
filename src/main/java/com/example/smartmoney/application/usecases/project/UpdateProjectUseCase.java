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
import com.example.smartmoney.domain.models.user.UserIncome;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateProjectUseCase {

    private final ProjectRepository projectRepository;
    private final ProjectTrackingRepository projectTrackingRepository;
    private final UserIncomeRepository userIncomeRepository;
    private final UserRepository userRepository;
    private final SafeSpendingService safeSpendingService;

    @Transactional
    public ProjectDetailResponse execute(String actorId, String projectId, UpdateProjectRequest req) {
        Project p = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        // V13: terminal/frozen states block edits — owner must abandon or
        // unfreeze first, not back-door changes through update.
        if (p.getStatus() == ProjectStatus.ABANDONED) {
            throw new ProjectException("Project is abandoned",
                    ProjectErrorCode.PROJECT_ABANDONED);
        }
        if (p.getStatus() == ProjectStatus.EXPIRED) {
            throw new ProjectException("Project has expired and can no longer be edited",
                    ProjectErrorCode.PROJECT_ABANDONED);
        }

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

        // If target amount or deadline changed, recalibrate tracking so
        // monthly_saving and month_left stay accurate for settlement.
        if (req.getTargetAmount() != null || req.getDeadline() != null) {
            recalibrateTracking(saved);
        }

        safeSpendingService.recalculateAndSaveSafeSpending(UserId.of(actorId));

        return ProjectDetailResponse.fromDomain(saved);
    }

    private void validateUpdate(String actorId, Project existing, UpdateProjectRequest req) {
        UUID userId;
        try {
            userId = UUID.fromString(actorId);
        } catch (Exception ex) {
            throw new ProjectException("Invalid actor id", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ProjectException("User not found", ProjectErrorCode.PROJECT_PERMISSION_DENIED));

        if (!user.isIncomeSetupCompleted()) {
            throw new ProjectException(
                    "Please set up your income profile before updating projects",
                    ProjectErrorCode.INCOME_PROFILE_REQUIRED);
        }

        if (!user.isFinancialSetupCompleted()) {
            throw new ProjectException(
                    "Please set up your financial profile before updating projects",
                    ProjectErrorCode.FINANCIAL_PROFILE_REQUIRED);
        }

        UserIncome income = userIncomeRepository.findByUserId(userId)
                .orElseThrow(() -> new ProjectException(
                        "You must set net_income and usable_income before updating projects",
                        ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED
                ));

        if (income.getNetIncome() == null || income.getUsableIncome() == null || income.getUsableIncome() <= 0) {
            throw new ProjectException(
                    "You must set net_income and usable_income before updating projects",
                    ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED
            );
        }

        // Compute post-update values
        ProjectStatus nextStatus = req.getStatus() != null ? req.getStatus() : existing.getStatus();
        ProjectPriority nextPriority = req.getPriority() != null ? req.getPriority() : existing.getPriority();
        if (nextPriority == null) nextPriority = ProjectPriority.MEDIUM;

        LocalDate nextDeadline = req.getDeadline() != null ? req.getDeadline() : existing.getDeadline();

        BigDecimal nextTargetAmount = existing.getTargetAmount().amount();
        if (req.getTargetAmount() != null) {
            nextTargetAmount = req.getTargetAmount();
        }

        String projectCurrency = existing.getTargetAmount().currency();
        // Note: domain currently only changes currency when targetAmount is provided.
        if (req.getTargetAmount() != null && req.getCurrency() != null) {
            projectCurrency = req.getCurrency();
        }

        if (income.getCurrency() != null && projectCurrency != null
                && !income.getCurrency().equalsIgnoreCase(projectCurrency)) {
            throw new ProjectException(
                    "Project currency must match your user income currency",
                    ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED
            );
        }

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

        // 20% monthly obligation rule — enforced via MonthlyObligationPolicy
        MonthlyObligationPolicy.assertWithinLimit(
                income,
                activeOwnedOther,
                MonthlyObligationPolicy.derivedMonthly(nextTargetAmount, nextDeadline)
        );
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

    /**
     * Recalculates monthly_saving and month_left in ProjectTracking whenever
     * the project's target_amount or deadline is updated.
     *
     * Formula:
     *   newMonthLeft    = months from today to deadline  (authoritative reset)
     *   newMonthlySaving = (targetAmount - moneySaved) / newMonthLeft
     *                      [floored to 1 month, rounded CEILING]
     *
     * The DB trigger on project_tracking will then automatically
     * refresh user_income.safe_spending via the existing PL/pgSQL functions.
     */
    private void recalibrateTracking(Project project) {
        Optional<ProjectTracking> opt =
                projectTrackingRepository.findByProjectId(project.getId());

        if (opt.isEmpty()) {
            // Tracking not created yet — CreateProjectTrackingUseCase will
            // compute correct values when it is called.
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
            // Already at or past the target
            tracking.recalibrate(BigDecimal.ZERO.setScale(2), 0);
            newMonthlySaving = BigDecimal.ZERO;
        } else {
            newMonthlySaving = remaining.divide(
                    BigDecimal.valueOf(newMonthLeft), 2, RoundingMode.CEILING);

            // 20% guard on the actual value being written to tracking
            UserId ownerId = project.getOwnerId();
            List<Project> otherActive = MonthlyObligationPolicy.otherActiveOwned(
                    projectRepository.findAllByUser(ownerId),
                    ownerId,
                    project.getId().value()
            );
            userIncomeRepository.findByUserId(ownerId.value()).ifPresent(income ->
                    MonthlyObligationPolicy.assertWithinLimit(income, otherActive, newMonthlySaving)
            );

            // V13: hard safe-spending gate — if the new monthly saving is HIGHER
            // than the previous one, require the user's current safe-spending
            // headroom to cover the delta. (Decreases never need a gate.)
            BigDecimal oldMonthlySaving = tracking.getMonthlySaving() != null
                    ? tracking.getMonthlySaving() : BigDecimal.ZERO;
            BigDecimal delta = newMonthlySaving.subtract(oldMonthlySaving);
            if (delta.compareTo(BigDecimal.ZERO) > 0) {
                safeSpendingService.requireCanAfford(ownerId, delta);
            }

            tracking.recalibrate(newMonthlySaving, newMonthLeft);
        }

        projectTrackingRepository.save(tracking);
    }
}
