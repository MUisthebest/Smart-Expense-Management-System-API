package com.example.smartmoney.application.usecases.group;

import com.example.smartmoney.application.dtos.requests.group.GetGroupProjectSuggestionsRequest;
import com.example.smartmoney.application.dtos.responses.group.GroupProjectSuggestionsResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.group.Group;
import com.example.smartmoney.domain.models.group.enums.GroupStatus;
import com.example.smartmoney.domain.models.group.valueobjects.GroupId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class GetGroupProjectSuggestionsUseCase {

    private final GroupRepository groupRepository;

    @Transactional(readOnly = true)
    public GroupProjectSuggestionsResponse execute(String actorId, GetGroupProjectSuggestionsRequest req) {
        Group group = groupRepository.findById(GroupId.of(req.getGroupId()))
                .orElseThrow(() -> new ProjectException("Group not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        if (!UserId.of(actorId).equals(group.getAdminId())) {
            throw new ProjectException("Only group admin can view suggestions",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }
        if (group.getStatus() != GroupStatus.LOCKED) {
            throw new ProjectException("Group must be LOCKED to view suggestions",
                    ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        BigDecimal A = group.totalCapacity();

        Integer suggestedMonths = null;
        if (req.getInputAmount() != null && req.getInputAmount().compareTo(BigDecimal.ZERO) > 0
                && A.compareTo(BigDecimal.ZERO) > 0) {
            double raw = req.getInputAmount().divide(A, 10, RoundingMode.HALF_UP).doubleValue();
            suggestedMonths = (int) Math.ceil(raw);
        }

        BigDecimal suggestedAmount = null;
        if (req.getInputMonths() != null && req.getInputMonths() > 0) {
            suggestedAmount = A.multiply(BigDecimal.valueOf(req.getInputMonths()))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return GroupProjectSuggestionsResponse.builder()
                .totalCapacity(A)
                .suggestedMonths(suggestedMonths)
                .suggestedAmount(suggestedAmount)
                .build();
    }
}
