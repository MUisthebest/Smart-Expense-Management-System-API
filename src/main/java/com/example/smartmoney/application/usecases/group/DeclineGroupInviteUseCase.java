package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.requests.group.DeclineGroupInviteRequest;
import com.example.smartmoney.application.dtos.responses.group.GroupDetailResponse;
import com.example.smartmoney.application.ports.invite.GroupInviteTokenPort;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeclineGroupInviteUseCase {

    private final GroupRepository groupRepository;
    private final GroupInviteTokenPort inviteTokenPort;

    @Transactional
    public GroupDetailResponse execute(String actorId, DeclineGroupInviteRequest req) {
        UUID groupUuid = inviteTokenPort.find(req.getToken())
                .orElseThrow(() -> new ProjectException(
                        "Invite token is invalid or has expired", ProjectErrorCode.PROJECT_NOT_FOUND));

        Group group = groupRepository.findById(GroupId.of(groupUuid.toString()))
                .orElseThrow(() -> new ProjectException("Group not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        group.declineMember(UserId.of(actorId));
        groupRepository.save(group);
        inviteTokenPort.delete(req.getToken());

        log.info("[GroupInvite] User {} declined group invite for group {}", actorId, groupUuid);
        return GroupDetailResponse.fromDomain(group);
    }
}
