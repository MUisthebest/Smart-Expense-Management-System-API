package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.enums.GroupProjectStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.group.valueobjects.GroupProjectId;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DissolveGroupProjectUseCase {

    private final GroupProjectRepository groupProjectRepository;
    private final GroupRepository groupRepository;
    private final ProjectRepository projectRepository;
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
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        // Abandon all active/frozen sub-personal projects
        List<Project> subProjects = projectRepository.findAllByGroupProjectId(gp.getId().value());
        for (Project sub : subProjects) {
            if (sub.getStatus() == ProjectStatus.ACTIVE || sub.getStatus() == ProjectStatus.FROZEN) {
                sub.abandon(sub.getOwnerId());
                projectRepository.save(sub);
                safeSpendingService.recalculateAndSaveSafeSpending(sub.getOwnerId());
            }
        }

        gp.dissolve();
        groupProjectRepository.save(gp);

        group.dissolve();
        groupRepository.save(group);
    }
}
