package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.responses.group.GroupDetailResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.smartmoney.domain.models.group.valueobjects.GroupId;

@Service
@RequiredArgsConstructor
public class LockGroupUseCase {

    private final GroupRepository groupRepository;

    @Transactional
    public GroupDetailResponse execute(String actorId, String groupId) {
        UserId actor = UserId.of(actorId);
        Group group = groupRepository.findById(GroupId.of(groupId))
                .orElseThrow(() -> new ProjectException("Group not found",
                        ProjectErrorCode.PROJECT_NOT_FOUND));

        group.lock(actor);

        Group saved = groupRepository.save(group);
        return GroupDetailResponse.fromDomain(saved);
    }
}
