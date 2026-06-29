package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.insight.InsightResponse;
import com.example.smartmoney.application.usecases.insight.GenerateInsightsUseCase;
import com.example.smartmoney.infrastructure.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/insights")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Insights", description = "Deterministic spending insights (pull). Satisfies NOTIFY users entirely.")
@SecurityRequirement(name = "bearerAuth")
public class InsightController {

    private final GenerateInsightsUseCase generateInsightsUseCase;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(
            summary = "Get current-month spending insights",
            description = "Returns deterministic insights (overspend anomalies, budget-pace projections) "
                    + "for the authenticated user's current month, ordered by their focus trait. Pull-only; no mutation."
    )
    @GetMapping
    public ResponseEntity<CheckResponse<List<InsightResponse>>> getInsights(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader) {

        try {
            UUID userId = extractUserIdFromToken(authHeader);
            List<InsightResponse> insights = generateInsightsUseCase.execute(userId);

            return ResponseEntity.ok(CheckResponse.<List<InsightResponse>>builder()
                    .success(true)
                    .message("Insights retrieved successfully")
                    .data(insights)
                    .build());

        } catch (Exception e) {
            log.error("Failed to retrieve insights", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    CheckResponse.<List<InsightResponse>>builder()
                            .success(false)
                            .message("Failed to retrieve insights: " + e.getMessage())
                            .build());
        }
    }

    private UUID extractUserIdFromToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return jwtTokenProvider.getUserIdFromToken(authHeader.substring(7));
        }
        throw new IllegalArgumentException("Invalid Authorization header");
    }
}
