package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.requests.group.CreateGroupRequest;
import com.example.smartmoney.application.dtos.requests.group.InviteGroupMemberRequest;
import com.example.smartmoney.application.dtos.responses.group.GroupDetailResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.GroupMember;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
// Disabled: user_income table removed.
// import com.example.smartmoney.domain.models.user.UserIncome;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.models.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateGroupUseCase {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final InviteGroupMemberUseCase inviteGroupMemberUseCase;

    @Transactional
    public GroupDetailResponse execute(String actorId, CreateGroupRequest req) {
        User user = userRepository.findById(UUID.fromString(actorId))
                .orElseThrow(() -> new ProjectException(
                        "User not found",
                        ProjectErrorCode.PROJECT_PERMISSION_DENIED
                ));

        BigDecimal capacity = user.getSavingCapacity();
        Group group = Group.createNew(UserId.of(actorId), req.getName(), req.getDescription(), capacity);
        Group saved = groupRepository.save(group);

        if (req.getCloneGroupId() != null && !req.getCloneGroupId().isBlank()) {
            Group clonedGroup = groupRepository.findById(GroupId.of(req.getCloneGroupId()))
                    .orElseThrow(() -> new ProjectException(
                            "Cloned group not found",
                            ProjectErrorCode.PROJECT_NOT_FOUND
                    ));
            // Security check: actorId must be a member of the cloned group
            boolean isMember = clonedGroup.getMembers().stream()
                    .anyMatch(m -> m.getUserId().value().toString().equals(actorId));
            if (!isMember) {
                throw new ProjectException(
                        "You must be a member of the cloned group",
                        ProjectErrorCode.PROJECT_PERMISSION_DENIED
                );
            }
            // Fetch all other members and invite them
            for (GroupMember member : clonedGroup.getMembers()) {
                String memberUserId = member.getUserId().value().toString();
                if (memberUserId.equals(actorId)) {
                    continue;
                }
                User memberUser = userRepository.findById(UUID.fromString(memberUserId))
                        .orElse(null);
                if (memberUser != null && memberUser.getEmail() != null) {
                    InviteGroupMemberRequest inviteReq = new InviteGroupMemberRequest();
                    inviteReq.setEmail(memberUser.getEmail().getValue());
                    inviteGroupMemberUseCase.execute(actorId, saved.getId().toString(), inviteReq);
                }
            }
            // Re-fetch the saved group to return it with the newly invited members
            saved = groupRepository.findById(saved.getId())
                    .orElse(saved);
        }

        return GroupDetailResponse.fromDomain(saved);
    }
}
