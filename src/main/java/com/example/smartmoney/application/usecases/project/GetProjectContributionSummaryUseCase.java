package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.responses.project.ContributionSummaryResponse;
import com.example.smartmoney.application.dtos.responses.project.ContributorResponse;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectContribution;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class GetProjectContributionSummaryUseCase {

    private final ProjectRepository projectRepository;

    @Transactional(readOnly = true)
    public ContributionSummaryResponse execute(String actorId, String projectId) {
        Project p = projectRepository.findById(ProjectId.of(projectId))
                .orElseThrow(() -> new ProjectException("Project not found", ProjectErrorCode.PROJECT_NOT_FOUND));

        UserId actor = UserId.of(actorId);
        if (!actor.equals(p.getOwnerId())) {
            throw new ProjectException("Permission denied", ProjectErrorCode.PROJECT_PERMISSION_DENIED);
        }

        Money total = p.totalContributed();
        Money remaining = p.remaining();

        Map<UserId, BigDecimal> byUser = new LinkedHashMap<>();
        Map<UserId, Instant> lastDate = new HashMap<>();

        for (ProjectContribution c : p.getContributions()) {
            byUser.merge(c.getUserId(), c.getMoney().amount(), BigDecimal::add);
            lastDate.merge(c.getUserId(), c.getCreatedAt(),
                    (oldV, newV) -> newV != null && (oldV == null || newV.isAfter(oldV)) ? newV : oldV);
        }

        List<ContributorResponse> contributors = byUser.entrySet().stream()
                .map(e -> {
                    BigDecimal percentOfTarget = BigDecimal.ZERO;
                    if (p.getTargetAmount().amount().compareTo(BigDecimal.ZERO) > 0) {
                        percentOfTarget = e.getValue()
                                .multiply(new BigDecimal("100"))
                                .divide(p.getTargetAmount().amount(), 2, RoundingMode.HALF_UP);
                    }
                    return ContributorResponse.builder()
                            .userId(e.getKey().toString())
                            .totalAmount(e.getValue())
                            .percentOfTarget(percentOfTarget)
                            .createdAt(lastDate.get(e.getKey()))
                            .build();
                })
                .sorted(Comparator.comparing(ContributorResponse::getTotalAmount).reversed())
                .toList();

        return ContributionSummaryResponse.builder()
                .totalContributed(total.amount())
                .remaining(remaining.amount())
                .progressPercent(p.progressPercent())
                .contributors(contributors)
                .build();
    }
}
