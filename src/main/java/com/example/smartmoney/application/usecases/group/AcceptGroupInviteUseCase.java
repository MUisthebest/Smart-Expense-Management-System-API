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
// Disabled: user_income table removed.
// import com.example.smartmoney.domain.models.user.UserIncome;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.models.user.User;
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
    private final UserRepository userRepository;
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
        UUID actorUuid = UUID.fromString(actorId);

        User user = userRepository.findById(actorUuid)
                .orElseThrow(() -> new ProjectException(
                        "User not found",
                        ProjectErrorCode.PROJECT_PERMISSION_DENIED
                ));

        BigDecimal capacity = user.getSavingCapacity();

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
