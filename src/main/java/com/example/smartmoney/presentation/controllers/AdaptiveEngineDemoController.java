package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.usecases.insight.SendMonthlyRecapUseCase;
import com.example.smartmoney.application.usecases.insight.SuggestionProducer;
import com.example.smartmoney.infrastructure.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Wave 3 demo triggers: run the scheduler-driven flows on demand for the <b>authenticated user</b>
 * with an explicit {@code asOf} date, so the recap and the month-end sweep can be tested and
 * demoed deterministically (Postman folders 13–14) without waiting for a cron to fire. Exercises
 * the exact same use-case code paths the schedulers call — only the trigger differs.
 *
 * <p>Registered only when {@code app.demo-triggers.enabled=true} — <b>disabled by default</b>: a
 * production build has no reason to expose on-demand recaps. Auth is the normal bearer token, and
 * each call only affects the caller's own account (no fan-out).
 */
@RestController
@RequestMapping("/api/v1/demo/adaptive")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Adaptive Engine Demo", description = "On-demand triggers for the scheduled flows (testing/demo only; property-gated)")
@SecurityRequirement(name = "bearerAuth")
@ConditionalOnProperty(prefix = "app.demo-triggers", name = "enabled", havingValue = "true", matchIfMissing = false)
public class AdaptiveEngineDemoController {

    private final SendMonthlyRecapUseCase sendMonthlyRecapUseCase;
    private final SuggestionProducer suggestionProducer;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(
            summary = "Run my monthly recap now (demo)",
            description = "Evaluates the month BEFORE `asOf` (default: today) for INCOME_DROP / "
                    + "CASHFLOW_NEGATIVE and pushes the recap notifications — the same code path "
                    + "MonthlyRecapScheduler runs at month rollover, for the caller only."
    )
    @PostMapping("/recap")
    public ResponseEntity<CheckResponse<Boolean>> runRecap(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Reference date (YYYY-MM-DD); the month before it is recapped. Defaults to today.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        try {
            UUID userId = extractUserIdFromToken(authHeader);
            boolean sent = sendMonthlyRecapUseCase.sendFor(userId, asOf != null ? asOf : LocalDate.now());
            return ResponseEntity.ok(CheckResponse.<Boolean>builder()
                    .success(true)
                    .message(sent ? "Recap sent" : "Nothing to recap for that month")
                    .data(sent)
                    .build());
        } catch (Exception e) {
            log.error("Demo recap trigger failed", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CheckResponse.<Boolean>builder()
                    .success(false)
                    .message("Recap trigger failed: " + e.getMessage())
                    .build());
        }
    }

    @Operation(
            summary = "Run my month-end savings sweep now (demo)",
            description = "Evaluates savings headroom as of `asOf` (default: today) and may raise a "
                    + "CONTRIBUTE_TO_PROJECT suggestion — the same code path MonthEndSweepScheduler "
                    + "runs on day 25, for the caller only. Best-effort: always returns success; "
                    + "check GET /suggestions for the outcome (dedup/cooldown may legitimately skip)."
    )
    @PostMapping("/sweep")
    public ResponseEntity<CheckResponse<Boolean>> runSweep(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Reference date (YYYY-MM-DD) the sweep evaluates. Defaults to today.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        try {
            UUID userId = extractUserIdFromToken(authHeader);
            suggestionProducer.maybeProduceSavingsContribution(userId, asOf != null ? asOf : LocalDate.now());
            return ResponseEntity.ok(CheckResponse.<Boolean>builder()
                    .success(true)
                    .message("Sweep evaluated — check pending suggestions for a contribution ask")
                    .data(true)
                    .build());
        } catch (Exception e) {
            log.error("Demo sweep trigger failed", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CheckResponse.<Boolean>builder()
                    .success(false)
                    .message("Sweep trigger failed: " + e.getMessage())
                    .build());
        }
    }

    @Operation(
            summary = "Run my project-health check now (demo)",
            description = "Evaluates every active saving project's pace against its deadline as of "
                    + "`asOf` (default: today) and may raise an INCREASE_CONTRIBUTION suggestion per "
                    + "off-track project — the same code path ProjectHealthScheduler runs weekly, for "
                    + "the caller only. Best-effort: always returns success; check GET /suggestions "
                    + "for the outcome (on-pace/young projects and dedup/cooldown legitimately skip)."
    )
    @PostMapping("/project-health")
    public ResponseEntity<CheckResponse<Boolean>> runProjectHealth(
            @Parameter(description = "Bearer token", required = true, hidden = true)
            @RequestHeader("Authorization") String authHeader,
            @Parameter(description = "Reference date (YYYY-MM-DD) the pace is judged at. Defaults to today.")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        try {
            UUID userId = extractUserIdFromToken(authHeader);
            suggestionProducer.maybeProduceProjectHealth(userId, asOf != null ? asOf : LocalDate.now());
            return ResponseEntity.ok(CheckResponse.<Boolean>builder()
                    .success(true)
                    .message("Project health evaluated — check pending suggestions for catch-up asks")
                    .data(true)
                    .build());
        } catch (Exception e) {
            log.error("Demo project-health trigger failed", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CheckResponse.<Boolean>builder()
                    .success(false)
                    .message("Project-health trigger failed: " + e.getMessage())
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
