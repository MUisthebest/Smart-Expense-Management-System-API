package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.responses.group.GroupDetailResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.enums.GroupMemberInviteStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetGroupDetailUseCase {

    private final GroupRepository groupRepository;

    @Transactional(readOnly = true)
    public GroupDetailResponse execute(String actorId, String groupId) {
        Group group = groupRepository.findById(GroupId.of(groupId))
                .orElseThrow(() -> new ProjectException("Group not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        UserId actor = UserId.of(actorId);
        boolean canView = actor.equals(group.getAdminId())
                || group.getMembers().stream().anyMatch(m ->
                        m.getUserId().equals(actor)
                        && m.getInviteStatus() == GroupMemberInviteStatus.JOINED);
        if (!canView) {
            throw new ProjectException("Permission denied", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
        return GroupDetailResponse.fromDomain(group);
    }
}
