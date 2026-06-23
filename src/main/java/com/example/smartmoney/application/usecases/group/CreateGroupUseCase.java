package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.requests.group.CreateGroupRequest;
import com.example.smartmoney.application.dtos.responses.group.GroupDetailResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.UserIncome;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateGroupUseCase {

    private final GroupRepository groupRepository;
    private final UserIncomeRepository userIncomeRepository;

    @Transactional
    public GroupDetailResponse execute(String actorId, CreateGroupRequest req) {
        UUID adminUuid = UUID.fromString(actorId);
        UserIncome income = userIncomeRepository.findByUserId(adminUuid)
                .orElseThrow(() -> new ProjectException(
                        "You must set your income before creating a group",
                        ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED));

        if (income.getUsableIncome() == null || income.getUsableIncome() <= 0) {
            throw new ProjectException(
                    "usable_income must be > 0 before creating a group",
                    ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED);
        }

        BigDecimal capacity = computeCapacity(income);
        Group group = Group.createNew(UserId.of(actorId), req.getName(), req.getDescription(), capacity);
        Group saved = groupRepository.save(group);
        return GroupDetailResponse.fromDomain(saved);
    }

    static BigDecimal computeCapacity(UserIncome income) {
        BigDecimal usable = BigDecimal.valueOf(income.getUsableIncome());
        BigDecimal limit = usable.multiply(new BigDecimal("0.20"));
        BigDecimal safe = income.getSafeSpending() != null
                ? BigDecimal.valueOf(income.getSafeSpending()) : BigDecimal.ZERO;
        BigDecimal capacity = limit.subtract(usable.subtract(safe));
        return capacity.max(BigDecimal.ZERO);
    }
}
