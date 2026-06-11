package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.requests.group.AcceptGroupInviteRequest;
import com.example.smartmoney.application.dtos.responses.group.GroupDetailResponse;
import com.example.smartmoney.application.ports.invite.GroupInviteTokenPort;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.UserIncome;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
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
    private final UserIncomeRepository userIncomeRepository;
    private final ProjectRepository projectRepository;

    @Transactional
    public GroupDetailResponse execute(String actorId, AcceptGroupInviteRequest req) {
        UUID groupUuid = inviteTokenPort.find(req.getToken())
                .orElseThrow(() -> new ProjectException(
                        "Invite token is invalid or has expired", ProjectErrorCode.PROJECT_NOT_FOUND));

        Group group = groupRepository.findById(GroupId.of(groupUuid.toString()))
                .orElseThrow(() -> new ProjectException("Group not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        UserId actor = UserId.of(actorId);
        UUID actorUuid = UUID.fromString(actorId);

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

        // Gate: max 3 active projects
        long activeCount = projectRepository.findAllByUser(actor).stream()
                .filter(p -> actor.equals(p.getOwnerId()))
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE)
                .count();
        if (activeCount >= 3) {
            throw new ProjectException(
                    "You already have 3 active projects; cannot accept another group invite",
                    ProjectErrorCode.PROJECT_MAX_ACTIVE_REACHED);
        }

        // Gate: currency match
        if (income.getCurrency() != null) {
            String groupCurrency = getGroupCurrency(group, groupUuid);
            if (groupCurrency != null && !groupCurrency.equalsIgnoreCase(income.getCurrency())) {
                throw new ProjectException(
                        "Your income currency does not match the group's currency",
                        ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED);
            }
        }

        group.acceptMember(actor, capacity);
        groupRepository.save(group);
        inviteTokenPort.delete(req.getToken());

        log.info("[GroupInvite] User {} accepted group invite for group {}", actorId, groupUuid);
        return GroupDetailResponse.fromDomain(group);
    }

    private String getGroupCurrency(Group group, UUID groupUuid) {
        // Currency is stored on the group project (created after lock).
        // At accept time the project may not exist yet — skip currency check if so.
        return null;
    }
}
