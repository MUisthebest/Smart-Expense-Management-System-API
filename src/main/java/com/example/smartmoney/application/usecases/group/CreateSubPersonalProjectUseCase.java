package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.requests.group.CreateSubPersonalProjectRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectDetailResponse;
import com.example.smartmoney.application.usecases.project.MonthlyObligationPolicy;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.enums.GroupMemberInviteStatus;
import com.example.smartmoney.domain.models.group.enums.GroupProjectStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.group.valueobjects.GroupProjectId;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.UserIncome;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateSubPersonalProjectUseCase {

    private final GroupProjectRepository groupProjectRepository;
    private final GroupRepository groupRepository;
    private final ProjectRepository projectRepository;
    private final ProjectTrackingRepository projectTrackingRepository;
    private final UserIncomeRepository userIncomeRepository;
    private final SafeSpendingService safeSpendingService;

    @Transactional
    public ProjectDetailResponse execute(String actorId, String groupProjectId,
                                         CreateSubPersonalProjectRequest req) {
        GroupProject gp = groupProjectRepository.findById(GroupProjectId.of(groupProjectId))
                .orElseThrow(() -> new ProjectException("Group project not found",
                        ProjectErrorCode.PROJECT_NOT_FOUND));

        if (gp.getStatus() != GroupProjectStatus.ACTIVE) {
            throw new ProjectException("Group project is not active",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        Group group = groupRepository.findById(GroupId.of(gp.getGroupId().toString()))
                .orElseThrow(() -> new ProjectException("Group not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        UserId actor = UserId.of(actorId);

        // Actor must be a JOINED member
        BigDecimal capacitySnapshot = group.getMembers().stream()
                .filter(m -> m.getUserId().equals(actor)
                        && m.getInviteStatus() == GroupMemberInviteStatus.JOINED)
                .findFirst()
                .map(m -> m.getCapacitySnapshot())
                .orElseThrow(() -> new ProjectException(
                        "You are not a joined member of this group",
                        ProjectErrorCode.PROJECT_PERMISSION_DENIED));

        // Check for existing sub-project
        boolean alreadyJoined = projectRepository.findAllByGroupProjectId(gp.getId().value()).stream()
                .anyMatch(p -> actor.equals(p.getOwnerId()));
        if (alreadyJoined) {
            throw new ProjectException("You already have a sub-project for this group project",
                    ProjectErrorCode.PROJECT_DUPLICATE_MEMBER);
        }

        UUID actorUuid = UUID.fromString(actorId);
        UserIncome income = userIncomeRepository.findByUserId(actorUuid)
                .orElseThrow(() -> new ProjectException(
                        "Income not set", ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED));

        if (!gp.getCurrency().equalsIgnoreCase(income.getCurrency())) {
            throw new ProjectException(
                    "Your income currency does not match the group project currency",
                    ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED);
        }

        List<Project> allProjects = projectRepository.findAllByUser(actor);

        // Max 3 active projects
        long activeCount = allProjects.stream()
                .filter(p -> actor.equals(p.getOwnerId()))
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE)
                .count();
        if (activeCount >= 3) {
            throw new ProjectException("Max 3 active projects allowed",
                    ProjectErrorCode.PROJECT_MAX_ACTIVE_REACHED);
        }

        // Priority must not be in use
        ProjectPriority priority = req.getPriority();
        boolean priorityTaken = allProjects.stream()
                .filter(p -> actor.equals(p.getOwnerId()))
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE)
                .anyMatch(p -> p.getPriority() == priority);
        if (priorityTaken) {
            throw new ProjectException("Priority already in use by another active project",
                    ProjectErrorCode.PROJECT_ACTIVE_PRIORITY_CONFLICT);
        }

        // 20% obligation check using capacitySnapshot as the new monthly obligation
        List<Project> otherActive = MonthlyObligationPolicy.otherActiveOwned(allProjects, actor, null);
        MonthlyObligationPolicy.assertWithinLimit(income, otherActive, capacitySnapshot);
        safeSpendingService.requireCanAfford(actor, capacitySnapshot);

        BigDecimal targetAmount = capacitySnapshot.multiply(BigDecimal.valueOf(gp.getTotalMonths()));

        Project sub = Project.createSubPersonal(
                actor,
                priority,
                gp.getName(),
                gp.getDescription(),
                new Money(targetAmount, gp.getCurrency()),
                gp.getDeadline(),
                gp.getId().value()
        );

        Project saved = projectRepository.save(sub);

        ProjectTracking tracking = ProjectTracking.createNew(
                ProjectId.of(saved.getId().toString()),
                BigDecimal.ZERO,
                1,
                capacitySnapshot,
                gp.getTotalMonths(),
                BigDecimal.ZERO
        );
        projectTrackingRepository.save(tracking);

        safeSpendingService.recalculateAndSaveSafeSpending(actor);

        return ProjectDetailResponse.fromDomain(saved);
    }
}
