package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.requests.group.AcceptGroupInviteRequest;
import com.example.smartmoney.application.dtos.responses.group.GroupDetailResponse;
import com.example.smartmoney.application.ports.invite.GroupInviteTokenPort;
import com.example.smartmoney.application.ports.invite.GroupInviteTokenPort.InvitePayload;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AcceptGroupInviteUseCase {

    private final GroupRepository groupRepository;
    private final GroupInviteTokenPort inviteTokenPort;
    private final GroupProjectRepository groupProjectRepository;

    @Transactional
    public GroupDetailResponse execute(String actorId, AcceptGroupInviteRequest req) {
        InvitePayload payload = inviteTokenPort.find(req.getToken())
                .orElseThrow(() -> new ProjectException(
                        "Invite token is invalid or has expired", ProjectErrorCode.PROJECT_NOT_FOUND));

        // Token is bound to a specific user — reject if someone else tries to redeem it
        if (!payload.invitedUserId().equals(UUID.fromString(actorId))) {
            throw new ProjectException(
                    "This invite was not issued to you",
                    ProjectErrorCode.PROJECT_INVITE_USER_MISMATCH);
        }

        Group group = groupRepository.findById(GroupId.of(payload.groupId().toString()))
                .orElseThrow(() -> new ProjectException("Group not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        UserId actor = UserId.of(actorId);

        // Group capacity is no longer income-derived (Pivot A — usable_income / safe_spending
        // dropped): members join with zero capacity until a non-income capacity model is defined.
        BigDecimal capacity = BigDecimal.ZERO;

        // NOTE: The 3-project cap is NOT checked here — accepting an invite does not
        // consume a project slot. The cap is enforced at sub-project creation time
        // in CreateSubPersonalProjectUseCase, where the slot is actually reserved.

        group.acceptMember(actor, capacity);
        groupRepository.save(group);
        inviteTokenPort.delete(req.getToken());

        log.info("[GroupInvite] User {} accepted group invite for group {}", actorId, payload.groupId());
        return GroupDetailResponse.fromDomain(group);
    }
}
