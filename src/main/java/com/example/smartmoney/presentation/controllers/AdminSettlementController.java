package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.usecases.settlement.PerformMonthlySettlementUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/settlement")
@RequiredArgsConstructor
@Slf4j
@Tag(
        name = "Admin Settlement",
        description = "Admin APIs for triggering financial settlement manually"
)
@SecurityRequirement(name = "bearerAuth")
public class AdminSettlementController {

    private final PerformMonthlySettlementUseCase performMonthlySettlementUseCase;

    @Operation(
            summary = "Trigger monthly settlement manually",
            description = "Runs the monthly settlement process across all active projects immediately. " +
                          "Requires ADMIN role."
    )
    @PostMapping("/run")
    public CheckResponse<Void> runSettlementManually() {
        log.info("Admin triggered monthly settlement manually via API");
        performMonthlySettlementUseCase.execute();
        return CheckResponse.<Void>builder()
                .success(true)
                .message("Settlement process completed")
                .build();
    }
}
