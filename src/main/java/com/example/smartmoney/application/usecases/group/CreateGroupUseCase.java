package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.requests.group.CreateGroupRequest;
import com.example.smartmoney.application.dtos.responses.group.GroupDetailResponse;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CreateGroupUseCase {

    private final GroupRepository groupRepository;

    @Transactional
    public GroupDetailResponse execute(String actorId, CreateGroupRequest req) {
        // Group capacity is no longer income-derived (Pivot A — net income only, usable_income
        // and safe_spending dropped). Created with zero capacity until a non-income capacity
        // model is defined.
        BigDecimal capacity = BigDecimal.ZERO;
        Group group = Group.createNew(UserId.of(actorId), req.getName(), req.getDescription(), capacity);
        Group saved = groupRepository.save(group);
        return GroupDetailResponse.fromDomain(saved);
    }
}
