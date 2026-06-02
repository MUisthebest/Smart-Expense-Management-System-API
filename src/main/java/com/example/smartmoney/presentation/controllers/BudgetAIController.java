package com.example.smartmoney.presentation.controllers;


import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.usecases.budget.GenerateBudgetAllocationPlanUseCase;
import com.example.smartmoney.domain.models.message.MessageRecord;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/budget")
public class BudgetAIController {

    private final GenerateBudgetAllocationPlanUseCase generateBudgetAllocationPlanUseCase;

    public BudgetAIController(
            GenerateBudgetAllocationPlanUseCase generateBudgetAllocationPlanUseCase
    ) {
        this.generateBudgetAllocationPlanUseCase = generateBudgetAllocationPlanUseCase;
    }

    @Operation(
            summary = "Generate budget allocation plan",
            description = "Generate AI-based budget allocation plan using user income, profile and transaction history"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Budget allocation job created successfully"),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "Required user data not found")
    })
    @PostMapping("/generate")
    public CheckResponse<MessageRecord> generate(Authentication auth) {
        MessageRecord data = generateBudgetAllocationPlanUseCase.execute(actorId(auth));

        return CheckResponse.<MessageRecord>builder()
                .success(true)
                .message("Budget allocation job created")
                .data(data)
                .build();
    }

    private UserId actorId(Authentication auth) {
        return new UserId(UUID.fromString(auth.getName()));
    }
}
