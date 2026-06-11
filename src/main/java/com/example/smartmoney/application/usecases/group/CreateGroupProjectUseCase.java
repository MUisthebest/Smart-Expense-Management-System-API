package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.requests.group.CreateGroupProjectRequest;
import com.example.smartmoney.application.dtos.responses.group.GroupProjectDetailResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupProject;
import com.example.smartmoney.domain.models.group.enums.GroupStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CreateGroupProjectUseCase {

    private final GroupRepository groupRepository;
    private final GroupProjectRepository groupProjectRepository;

    @Value("${app.bypass-date-gate:false}")
    private boolean bypassDateGate;

    @Transactional
    public GroupProjectDetailResponse execute(String actorId, CreateGroupProjectRequest req) {
        int dayOfMonth = LocalDate.now().getDayOfMonth();
        if (!bypassDateGate && dayOfMonth > 7) {
            throw new ProjectException(
                    "Group projects can only be created during days 1-7 of the month",
                    ProjectErrorCode.PROJECT_CREATION_OUTSIDE_WINDOW);
        }

        Group group = groupRepository.findById(GroupId.of(req.getGroupId()))
                .orElseThrow(() -> new ProjectException("Group not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!UserId.of(actorId).equals(group.getAdminId())) {
            throw new ProjectException("Only group admin can create the group project",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
        if (group.getStatus() != GroupStatus.LOCKED) {
            throw new ProjectException("Group must be LOCKED before creating a project",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        // Ensure no group project exists yet for this group
        groupProjectRepository.findByGroupId(group.getId().value()).ifPresent(existing -> {
            throw new ProjectException("A group project already exists for this group",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        });

        LocalDate deadline = LocalDate.now().plusMonths(req.getTotalMonths());

        GroupProject gp = GroupProject.createNew(
                group.getId().value(),
                req.getName(),
                req.getDescription(),
                req.getTargetAmount(),
                group.totalCapacity(),
                req.getCurrency(),
                req.getTotalMonths(),
                deadline
        );

        GroupProject saved = groupProjectRepository.save(gp);
        return GroupProjectDetailResponse.fromDomain(saved, null, null, List.of());
    }
}
