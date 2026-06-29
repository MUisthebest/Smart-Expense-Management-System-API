package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.requests.group.CreateSubPersonalProjectRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectDetailResponse;
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
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
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

        // Actor must be a JOINED member — fetch the snapshot committed at accept time
        BigDecimal capacitySnapshot = group.getMembers().stream()
                .filter(m -> m.getUserId().equals(actor)
                        && m.getInviteStatus() == GroupMemberInviteStatus.JOINED)
                .findFirst()
                .map(m -> m.getCapacitySnapshot())
                .orElseThrow(() -> new ProjectException(
                        "You are not a joined member of this group",
                        ProjectErrorCode.PROJECT_PERMISSION_DENIED));

        // Group capacity is no longer income-derived (Pivot A): use the snapshot committed at accept time.
        BigDecimal effectiveCapacity = capacitySnapshot;

        List<Project> allProjects = projectRepository.findAllByUser(actor);

        // Check for existing sub-project — derived from allProjects to avoid a second DB call
        boolean alreadyJoined = allProjects.stream()
                .anyMatch(p -> p.isSubPersonal()
                        && gp.getId().value().equals(p.getGroupProjectId()));
        if (alreadyJoined) {
            throw new ProjectException("You already have a sub-project for this group project",
                    ProjectErrorCode.PROJECT_DUPLICATE_MEMBER);
        }

        // Max 3 non-terminal projects (ACTIVE + FROZEN): a frozen project still occupies
        // a slot until it recovers or ends. See docs/project-tracking-all-auto-plan.md §8.1.
        long liveCount = allProjects.stream()
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE
                        || p.getStatus() == ProjectStatus.FROZEN)
                .count();
        if (liveCount >= 3) {
            throw new ProjectException("Max 3 active projects allowed (a frozen project still counts until it recovers or ends)",
                    ProjectErrorCode.PROJECT_MAX_ACTIVE_REACHED);
        }

        // Priority must not be in use by another non-terminal project
        ProjectPriority priority = req.getPriority();
        boolean priorityTaken = allProjects.stream()
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE
                        || p.getStatus() == ProjectStatus.FROZEN)
                .anyMatch(p -> p.getPriority() == priority);
        if (priorityTaken) {
            throw new ProjectException("Priority already in use by another active project",
                    ProjectErrorCode.PROJECT_ACTIVE_PRIORITY_CONFLICT);
        }

        // If income dropped, reduce the group project's target so auto-complete can still fire
        BigDecimal capacityGap = capacitySnapshot.subtract(effectiveCapacity);
        if (capacityGap.compareTo(BigDecimal.ZERO) > 0) {
            gp.reduceTargetAmount(capacityGap.multiply(BigDecimal.valueOf(gp.getTotalMonths())));
            groupProjectRepository.save(gp);
        }

        BigDecimal targetAmount = effectiveCapacity.multiply(BigDecimal.valueOf(gp.getTotalMonths()));

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
                effectiveCapacity,
                gp.getTotalMonths(),
                BigDecimal.ZERO
        );
        projectTrackingRepository.save(tracking);

        return ProjectDetailResponse.fromDomain(saved);
    }
}
