package com.example.smartmoney.presentation.controllers;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.usecases.financial.GetFinancialAssistantContextUseCase;
import com.example.smartmoney.application.dtos.responses.FinancialAssistantContextResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final GetFinancialAssistantContextUseCase analyzeUseCase;

    @PostMapping("/analyze")
    public ResponseEntity<CheckResponse<FinancialAssistantContextResponse>> analyze(
            Authentication authentication
    ) {

        UUID userId = UUID.fromString(authentication.getName());

        FinancialAssistantContextResponse context = analyzeUseCase.execute(userId);

        return ResponseEntity.ok(
                CheckResponse.<FinancialAssistantContextResponse>builder()
                        .success(true)
                        .message("Financial analysis started")
                        .data(context)
                        .build()
        );
    }
}