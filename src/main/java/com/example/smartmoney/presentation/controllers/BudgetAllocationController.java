package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.budgets.BudgetAllocationPlanResponse;
import com.example.smartmoney.application.usecases.budget.ComputeBudgetAllocationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Deterministic, LLM-free budget allocation. Unlike {@code /api/v1/ai/budget/generate} (which
 * queues an async AI job), this computes the plan in-process and returns it synchronously.
 */
@RestController
@RequestMapping("/api/v1/budgets/allocation")
public class BudgetAllocationController {

    private final ComputeBudgetAllocationUseCase computeBudgetAllocationUseCase;

    public BudgetAllocationController(ComputeBudgetAllocationUseCase computeBudgetAllocationUseCase) {
        this.computeBudgetAllocationUseCase = computeBudgetAllocationUseCase;
    }

    @Operation(
            summary = "Compute budget allocation (deterministic)",
            description = "Compute a per-category budget plan in pure code from the user's income, "
                    + "profile traits and transaction history — no AI round-trip. Returns the plan "
                    + "synchronously; creating budgets from it is a separate step."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Budget allocation computed"),
            @ApiResponse(responseCode = "400", description = "Financial setup incomplete"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @PostMapping("/compute")
    public CheckResponse<BudgetAllocationPlanResponse> compute(Authentication auth) {
        BudgetAllocationPlanResponse data =
                computeBudgetAllocationUseCase.execute(UUID.fromString(auth.getName()));

        return CheckResponse.success("Budget allocation computed", data);
    }
}
