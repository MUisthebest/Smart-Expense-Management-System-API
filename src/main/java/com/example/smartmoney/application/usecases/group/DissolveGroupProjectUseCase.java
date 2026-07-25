package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.GroupProjectSponsorshipRequest;
import com.example.smartmoney.domain.models.group.enums.GroupProjectStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.group.valueobjects.GroupProjectId;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupProjectSponsorshipRequestRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DissolveGroupProjectUseCase {

    private final GroupProjectRepository groupProjectRepository;
    private final GroupRepository groupRepository;
    private final ProjectRepository projectRepository;
    private final GroupProjectSponsorshipRequestRepository sponsorshipRequestRepository;
    private final SafeSpendingService safeSpendingService;

    @Transactional
    public void execute(String actorId, String groupProjectId) {
        GroupProject gp = groupProjectRepository.findById(GroupProjectId.of(groupProjectId))
                .orElseThrow(() -> new ProjectException("Group project not found",
                        ProjectErrorCode.PROJECT_NOT_FOUND));

        Group group = groupRepository.findById(GroupId.of(gp.getGroupId().toString()))
                .orElseThrow(() -> new ProjectException("Group not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!UserId.of(actorId).equals(group.getAdminId())) {
            throw new ProjectException("Only group admin can dissolve the group project",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        if (gp.getStatus() == GroupProjectStatus.DISSOLVED) {
            throw new ProjectException("Group project is already dissolved",
                    ProjectErrorCode.PROJECT_ALREADY_DISSOLVED);
        }

        List<Project> subProjects = projectRepository.findAllByGroupProjectId(gp.getId().value());

        // Delete only ACTIVE/FROZEN sub-projects; COMPLETED/EXPIRED/ABANDONED are preserved
        // so members retain their history. The all-auto model records no contributions, so
        // there is nothing to refund — deletion releases the obligation. See
        // docs/project-tracking-all-auto-plan.md §3.4 / §7.
        Set<UserId> ownersToRecalculate = new LinkedHashSet<>();
        for (Project sub : subProjects) {
            if (sub.getStatus() == ProjectStatus.ACTIVE || sub.getStatus() == ProjectStatus.FROZEN) {
                projectRepository.deleteById(sub.getId());
                ownersToRecalculate.add(sub.getOwnerId());
            }
        }

        // Recalculate safe spending once per owner after all deletions are complete
        for (UserId ownerId : ownersToRecalculate) {
            safeSpendingService.recalculateAndSaveSafeSpending(ownerId);
        }

        GroupProjectStatus oldStatus = gp.getStatus();
        if (oldStatus == GroupProjectStatus.ACTIVE) {
            gp.dissolve();
            groupProjectRepository.save(gp);
            group.dissolve();
            groupRepository.save(group);
        } else {
            // Delete all associated sponsorship requests first to prevent foreign key constraint violations
            List<GroupProjectSponsorshipRequest> requests = sponsorshipRequestRepository.findAllByGroupProjectId(gp.getId().value());
            sponsorshipRequestRepository.deleteAll(requests);

            groupProjectRepository.delete(gp.getId());
        }
    }
}
