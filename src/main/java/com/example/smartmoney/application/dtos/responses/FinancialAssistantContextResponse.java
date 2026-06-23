package com.example.smartmoney.application.dtos.responses;
import com.example.smartmoney.application.dtos.responses.project.ProjectAIContextResponse;

import lombok.Builder;
import lombok.Value;
import java.util.List;

@Value
@Builder
public class FinancialAssistantContextResponse {
    WeeklyTransactionResponse weeklySummary;

    List<ProjectAIContextResponse> projects;   
}
