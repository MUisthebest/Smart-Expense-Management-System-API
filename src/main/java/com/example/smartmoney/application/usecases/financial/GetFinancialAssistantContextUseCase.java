package com.example.smartmoney.application.usecases.financial;

import com.example.smartmoney.application.dtos.responses.FinancialAssistantContextResponse;
import com.example.smartmoney.application.dtos.responses.WeeklyTransactionResponse;
import com.example.smartmoney.application.dtos.responses.project.ProjectAIContextResponse;
import com.example.smartmoney.application.dtos.responses.project.ProjectTrackingResponse;
import com.example.smartmoney.application.usecases.project.GetProjectAIContextUseCase;
import com.example.smartmoney.application.usecases.transaction.GetWeeklyTransactionSummaryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetFinancialAssistantContextUseCase {

    private final GetWeeklyTransactionSummaryUseCase weeklySummaryUseCase;

    private final GetProjectAIContextUseCase projectAIContextUseCase;

    public FinancialAssistantContextResponse execute(UUID userId) {

        WeeklyTransactionResponse weeklySummary =
                weeklySummaryUseCase.execute(userId);

        List<ProjectAIContextResponse> projects =
                projectAIContextUseCase.execute(userId);

        return FinancialAssistantContextResponse.builder()
                .weeklySummary(weeklySummary)
                .projects(projects)
                .build();
    }
}