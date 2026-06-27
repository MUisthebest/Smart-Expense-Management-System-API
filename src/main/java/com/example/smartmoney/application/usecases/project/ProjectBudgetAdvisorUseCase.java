package com.example.smartmoney.application.usecases.project;

import com.example.smartmoney.application.dtos.requests.project.ProjectBudgetAdvisorRequest;
import com.example.smartmoney.application.dtos.responses.project.ProjectBudgetAdvisorResponse;
import com.example.smartmoney.application.ports.llm.LlmPort;
import com.example.smartmoney.domain.exceptions.ProjectErrorCode;
import com.example.smartmoney.domain.exceptions.ProjectException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/*
 * Disabled: ProjectBudgetAdvisor entirely depends on UserIncomeRepository (user_income table removed).
 * Previously computed monthly saving / number of months for a new project
 * under the 20% usable-income obligation rule.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectBudgetAdvisorUseCase {

    // private final UserIncomeRepository userIncomeRepository;  // Disabled: user_income table removed
    private final LlmPort llmPort;

    public ProjectBudgetAdvisorResponse execute(String actorId, ProjectBudgetAdvisorRequest req) {
        throw new ProjectException(
                "Budget advisor is temporarily disabled (user income feature removed)",
                ProjectErrorCode.PROJECT_USER_INCOME_REQUIRED
        );
    }
}
