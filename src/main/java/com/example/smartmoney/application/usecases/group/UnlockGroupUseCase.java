package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.responses.group.GroupDetailResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smartmoney.domain.models.group.valueobjects.GroupId;

@Service
@RequiredArgsConstructor
public class UnlockGroupUseCase {

    private final GroupRepository groupRepository;
    private final GroupProjectRepository groupProjectRepository;

    @Transactional
    public GroupDetailResponse execute(String actorId, String groupId) {
        UserId actor = UserId.of(actorId);
        Group group = groupRepository.findById(GroupId.of(groupId))
                .orElseThrow(() -> new ProjectException("Group not found",
                        ProjectErrorCode.PROJECT_NOT_FOUND));

        // Check if group has a project
        if (groupProjectRepository.findByGroupId(group.getId().value()).isPresent()) {
            throw new ProjectException("Cannot unlock group: a project has already been created for this group",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        group.unlock(actor);

        Group saved = groupRepository.save(group);
        return GroupDetailResponse.fromDomain(saved);
    }
}
