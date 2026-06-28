package com.example.smartmoney.application.usecases.settlement;

import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectHistoryRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/*
 * Disabled: monthly settlement penalty/surplus logic depends on UserIncome data (user_income table removed).
 * settleUserProjects is a no-op until user_income is restored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerformMonthlySettlementUseCase {

    private final ProjectRepository projectRepository;
    private final ProjectTrackingRepository projectTrackingRepository;
    private final TransactionRepository transactionRepository;
    // private final UserIncomeRepository userIncomeRepository;  // Disabled: user_income table removed
    private final ProjectHistoryRepository projectHistoryRepository;
    private final SafeSpendingService safeSpendingService;

    public void execute() {
        List<Project> activeProjects = projectRepository.findAllByStatus(ProjectStatus.ACTIVE);
        log.info("[Settlement] Starting monthly settlement for {} active project(s).", activeProjects.size());

        Map<UserId, List<Project>> userProjects = activeProjects.stream()
                .collect(Collectors.groupingBy(Project::getOwnerId));

        for (Map.Entry<UserId, List<Project>> entry : userProjects.entrySet()) {
            try {
                settleUserProjects(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.error("[Settlement] Failed to settle projects for user {}: {}",
                        entry.getKey().value(), e.getMessage(), e);
            }
        }

        log.info("[Settlement] Monthly settlement complete.");
    }

    @Transactional
    public void settleUserProjects(UserId ownerId, List<Project> projects) {
        // Disabled: settlement penalty/surplus logic depends on UserIncome (user_income table removed)
        log.warn("[Settlement] Settlement skipped for user {} - user_income table removed.", ownerId.value());
    }
}
