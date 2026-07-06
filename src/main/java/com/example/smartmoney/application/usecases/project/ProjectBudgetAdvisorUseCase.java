package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.ProjectBudgetAdvisorRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectBudgetAdvisorResponse;
import com.example.smartmoney.application.ports.llm.LlmPort;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectBudgetAdvisorUseCase {

    private static final int MAX_MONTHS = 60;

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final LlmPort llmPort;

    public ProjectBudgetAdvisorResponse execute(String actorId, ProjectBudgetAdvisorRequest req) {
        User user = userRepository.findById(UUID.fromString(actorId))
                .orElseThrow(() -> new ProjectException(
                        "User not found",
                        ProjectErrorCode.PROJECT_PERMISSION_DENIED
                ));

        if (!user.hasCompletedFinancialSetup()) {
            throw new ProjectException(
                    "Vui lòng hoàn thành thiết lập tài chính của bạn trước khi sử dụng trợ lý tài chính",
                    ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED
            );
        }

        UserId actor = UserId.of(actorId);
        List<Project> all = projectRepository.findAllByUser(actor);
        List<Project> activeOwned = all.stream()
                .filter(p -> actor.equals(p.getOwnerId()))
                .filter(p -> p.getStatus() == ProjectStatus.ACTIVE)
                .toList();

        // Calculate existing monthly obligation
        BigDecimal existingObligations = BigDecimal.ZERO;
        for (Project p : activeOwned) {
            int monthsLeft = monthsToDeadline(LocalDate.now(), p.getDeadline());
            BigDecimal activeMonthlyTarget = p.getTargetAmount().amount().divide(BigDecimal.valueOf(monthsLeft), 2, RoundingMode.CEILING);
            existingObligations = existingObligations.add(activeMonthlyTarget);
        }

        BigDecimal maxObligation = user.getSavingCapacity();
        BigDecimal maxAllowedNewMonthlySaving = maxObligation.subtract(existingObligations);

        if (maxAllowedNewMonthlySaving.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ProjectException(
                    "Với mức thu nhập hiện tại của bạn, bạn đã đạt giới hạn tiết kiệm cho các dự án đang hoạt động. Vui lòng hoàn thành hoặc hủy bớt dự án trước khi tạo thêm nhé!",
                    ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED
            );
        }

        int months;
        BigDecimal monthlySaving;

        if (req.getMode() == ProjectBudgetAdvisorRequest.Mode.URGENT) {
            months = req.getTargetAmount()
                    .divide(maxAllowedNewMonthlySaving, 0, RoundingMode.CEILING)
                    .intValue();
            months = Math.max(1, months);
        } else {
            BigDecimal relaxedMonthlySaving = maxAllowedNewMonthlySaving
                    .divide(new BigDecimal("2"), 2, RoundingMode.FLOOR);
            if (relaxedMonthlySaving.compareTo(BigDecimal.ZERO) <= 0) {
                relaxedMonthlySaving = maxAllowedNewMonthlySaving;
            }
            months = req.getTargetAmount()
                    .divide(relaxedMonthlySaving, 0, RoundingMode.CEILING)
                    .intValue();
            months = Math.min(Math.max(1, months), MAX_MONTHS);
        }

        monthlySaving = req.getTargetAmount()
                .divide(BigDecimal.valueOf(months), 2, RoundingMode.CEILING);

        return ProjectBudgetAdvisorResponse.builder()
                .monthlySaving(monthlySaving)
                .numberOfMonths(months)
                .build();
    }

    private int monthsToDeadline(LocalDate today, LocalDate deadline) {
        if (deadline == null) {
            throw new ProjectException("Deadline is required", ProjectErrorCode.PROJECT_INVALID_DEADLINE);
        }
        if (deadline.isBefore(today)) {
            throw new ProjectException("Deadline must be today or later", ProjectErrorCode.PROJECT_INVALID_DEADLINE);
        }
        int months = (deadline.getYear() - today.getYear()) * 12 + (deadline.getMonthValue() - today.getMonthValue()) + 1;
        return Math.max(1, months);
    }
}
