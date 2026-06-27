package com.example.smartmoney.application.usecases.project;
import com.example.smartmoney.application.dtos.responses.project.ProjectAIContextResponse;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class GetProjectAIContextUseCase {

    private final ProjectRepository projectRepository;

    private final ProjectTrackingRepository trackingRepository;

    public List<ProjectAIContextResponse> execute(UUID userId) {

        List<Project> projects =
                projectRepository.findAllByUser(
                        UserId.of(userId.toString())
                );

        return projects.stream()
                .map(this::buildContext)
                .filter(Objects::nonNull)
                .toList();
    }

    private ProjectAIContextResponse buildContext(Project project) {

        ProjectTracking tracking =
                trackingRepository.findByProjectId(project.getId())
                        .orElse(null);

        if (tracking == null) {
            return null;
        }

        BigDecimal targetAmount =
                project.getTargetAmount().amount();

        BigDecimal moneySaved =
                tracking.getMoneySaved();

        BigDecimal remainingToGoal =
                targetAmount.subtract(moneySaved);

        if (remainingToGoal.compareTo(BigDecimal.ZERO) < 0) {
            remainingToGoal = BigDecimal.ZERO;
        }

        int completedMonths =
                Math.max(
                        tracking.getCurrentMonth() - 1,
                        0
                );

        BigDecimal expectedSaved =
                tracking.getMonthlySaving()
                        .multiply(
                                BigDecimal.valueOf(
                                        completedMonths
                                )
                        );

        BigDecimal savingGap =
                expectedSaved.subtract(moneySaved);

        if (savingGap.compareTo(BigDecimal.ZERO) < 0) {
            savingGap = BigDecimal.ZERO;
        }

        BigDecimal requiredMonthlyFromNow =
                BigDecimal.ZERO;

        if (tracking.getMonthLeft() > 0) {
            requiredMonthlyFromNow =
                    remainingToGoal.divide(
                            BigDecimal.valueOf(
                                    tracking.getMonthLeft()
                            ),
                            0,
                            RoundingMode.UP
                    );
        }

        int progressPercent = 0;

        if (targetAmount.compareTo(BigDecimal.ZERO) > 0) {
            progressPercent =
                    moneySaved.multiply(BigDecimal.valueOf(100))
                            .divide(
                                    targetAmount,
                                    0,
                                    RoundingMode.HALF_UP
                            )
                            .intValue();
        }

        return ProjectAIContextResponse.fromDomain(
                project.getId().value().toString(),
                project.getName(),
                targetAmount,
                moneySaved,
                expectedSaved,
                savingGap,
                remainingToGoal,
                requiredMonthlyFromNow,
                tracking.getMonthlySaving(),
                tracking.getMoneyOwed(),
                tracking.getMonthLeft(),
                progressPercent
        );
    }
}