package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.CreateProjectRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectDetailResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CreateProjectUseCase {

    private final ProjectRepository projectRepository;
    private final ProjectTrackingRepository projectTrackingRepository;
    // private final UserIncomeRepository userIncomeRepository;  // Disabled: user_income table removed
    private final SafeSpendingService safeSpendingService;

    @Transactional
    public ProjectDetailResponse execute(String actorId, CreateProjectRequest req) {
        validateCreate(actorId, req);

        Project p = Project.createNew(
                UserId.of(actorId),
                req.getType(),
                req.getPriority(),
                req.getName(),
                req.getDescription(),
                new Money(req.getTargetAmount(), req.getCurrency()),
                req.getDeadline()
        );

        Project savedProject = projectRepository.save(p);

        // Automate tracking creation
        int monthsLeft = monthsToDeadline(LocalDate.now(), req.getDeadline());
        BigDecimal monthlySaving = monthlyTarget(req.getTargetAmount(), req.getDeadline());

        ProjectTracking tracking = ProjectTracking.createNew(
                ProjectId.of(savedProject.getId().toString()),
                BigDecimal.ZERO, // moneySaved
                1,               // currentMonth
                monthlySaving,
                monthsLeft,
                BigDecimal.ZERO  // moneyOwed
        );
        projectTrackingRepository.save(tracking);

        safeSpendingService.recalculateAndSaveSafeSpending(UserId.of(actorId));

        return ProjectDetailResponse.fromDomain(savedProject);
    }

    private void validateCreate(String actorId, CreateProjectRequest req) {
        // Disabled: user_income validation removed (user_income table removed)
        // Income check and 20% monthly obligation rule are no longer enforced

        UserId actor = UserId.of(actorId);
        List<Project> all = projectRepository.findAllByUser(actor);
        List<Project> activeOwned = all.stream()
                .filter(p -> actor.equals(p.getOwnerId()))
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE)
                .toList();

        if (activeOwned.size() >= 3) {
            throw new ProjectException(
                    "Max 3 ACTIVE projects allowed",
                    ProjectErrorCode.PROJECT_MAX_ACTIVE_REACHED
            );
        }

        // Deadline must be within 60 months (create-time rule)
        int monthsToDeadline = monthsToDeadline(LocalDate.now(), req.getDeadline());
        if (monthsToDeadline > 60) {
            throw new ProjectException(
                    "Max deadline is 60 months",
                    ProjectErrorCode.PROJECT_INVALID_DEADLINE
            );
        }

        // Each ACTIVE project must have a unique priority
        Set<ProjectPriority> existingPriorities = new HashSet<>();
        for (Project p : activeOwned) {
            ProjectPriority pr = p.getPriority();
            if (!existingPriorities.add(pr)) {
                throw new ProjectException(
                        "Each ACTIVE project must have a unique priority (fix existing duplicates first)",
                        ProjectErrorCode.PROJECT_ACTIVE_PRIORITY_CONFLICT
                );
            }
        }

        ProjectPriority newPriority = req.getPriority() != null ? req.getPriority() : ProjectPriority.MEDIUM;
        if (existingPriorities.contains(newPriority)) {
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
}
