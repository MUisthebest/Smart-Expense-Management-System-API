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
// import com.example.smartmoney.domain.repositories.UserIncomeRepository;
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
    // Disabled: user_income table removed.
    // private final UserIncomeRepository userIncomeRepository;
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

        // Disabled: income-based capacity / currency gating removed (user_income table removed).
        // TODO: redefine capacity & currency validation now that user income is no longer tracked.
        BigDecimal capacity = BigDecimal.ZERO; // Disabled: capacity gating removed
        /*
        UserIncome income = userIncomeRepository.findByUserId(actorUuid)
                .orElseThrow(() -> new ProjectException(
                        "You must set your income before accepting a group invite",
                        ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED));

        if (income.getUsableIncome() == null || income.getUsableIncome() <= 0) {
            throw new ProjectException("usable_income must be > 0", ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED);
        }

        // Gate: safe_spending must be < 20% * usable_income (capacity > 0)
        BigDecimal capacity = CreateGroupUseCase.computeCapacity(income);
        if (capacity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ProjectException(
                    "You have no remaining project capacity (safe_spending >= 20% of usable_income)",
                    ProjectErrorCode.PROJECT_USABLE_INCOME_LIMIT_EXCEEDED);
        }

        // Gate: currency match — checked only when the group project already exists
        groupProjectRepository.findByGroupId(payload.groupId()).ifPresent(gp -> {
            if (income.getCurrency() == null || income.getCurrency().isBlank()) {
                throw new ProjectException(
                        "Your income currency is not set; please update your income before accepting a group invite",
                        ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED);
            }
            if (!gp.getCurrency().equalsIgnoreCase(income.getCurrency())) {
                throw new ProjectException(
                        "Your income currency (" + income.getCurrency()
                                + ") does not match the group's currency (" + gp.getCurrency() + ")",
                        ProjectErrorCode.PROJECT_CURRENCY_MISMATCH);
            }
        });
        */

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
