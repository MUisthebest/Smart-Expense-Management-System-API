package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteGroupUseCase {

    private final GroupRepository groupRepository;
    private final GroupProjectRepository groupProjectRepository;

    @Transactional
    public void execute(String actorId, String groupId) {
        UserId actor = UserId.of(actorId);
        GroupId gId = GroupId.of(groupId);

        Group group = groupRepository.findById(gId)
                .orElseThrow(() -> new ProjectException("Group not found",
                        ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!group.getAdminId().equals(actor)) {
            throw new ProjectException("Only group admin can delete this group",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        // Check if group has a group project created
        boolean hasActiveProject = groupProjectRepository.findByGroupId(gId.value())
                .map(gp -> gp.getStatus() != com.example.smartmoney.domain.models.group.enums.GroupProjectStatus.DISSOLVED)
                .orElse(false);
        if (hasActiveProject) {
            throw new ProjectException("Cannot delete group: group project has already been created",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        groupRepository.delete(gId);
    }
}
