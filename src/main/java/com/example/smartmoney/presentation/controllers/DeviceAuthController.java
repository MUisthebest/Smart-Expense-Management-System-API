package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.auth.DeviceActivateRequest;
import com.example.smartmoney.application.dtos.requests.auth.DeviceCodeRequest;
import com.example.smartmoney.application.dtos.requests.auth.DeviceTokenRequest;
import com.example.smartmoney.application.dtos.responses.AuthResponse;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.DeviceCodeResponse;
import com.example.smartmoney.application.usecases.auth.ActivateDeviceUseCase;
import com.example.smartmoney.application.usecases.auth.PollDeviceTokenUseCase;
import com.example.smartmoney.application.usecases.auth.RequestDeviceCodeUseCase;
import com.example.smartmoney.domain.exceptions.DomainException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth/device")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Device Flow Auth", description = "OAuth 2.0 Device Authorization Flow for CLI/Desktop apps")
public class DeviceAuthController {

    private final RequestDeviceCodeUseCase requestDeviceCodeUseCase;
    private final ActivateDeviceUseCase    activateDeviceUseCase;
    private final PollDeviceTokenUseCase   pollDeviceTokenUseCase;

    /**
     * STEP 1 — CLI calls this to initiate device flow.
     * Returns device_code (for polling) + user_code (for user to enter in browser).
     *
     * POST /api/v1/auth/device/code
     */
    @Operation(
            summary = "Request device code (CLI step 1)",
            description = "CLI calls this to get a device_code and user_code. " +
                    "Show user_code + verification_uri to the user, then start polling /device/token."
    )
    @PostMapping("/code")
    public ResponseEntity<CheckResponse<DeviceCodeResponse>> requestCode(
            @Valid @RequestBody DeviceCodeRequest request) {

        try {
            DeviceCodeResponse data = requestDeviceCodeUseCase.execute(request);
            return ResponseEntity.ok(CheckResponse.<DeviceCodeResponse>builder()
                    .success(true)
                    .message("Device code issued. Open browser and enter user code.")
                    .data(data)
                    .build());
        } catch (Exception e) {
            log.error("Failed to issue device code", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CheckResponse.<DeviceCodeResponse>builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        }
    }

    /**
     * STEP 2 — Browser calls this after user enters email + password + user_code.
     * This is the activation page endpoint — credentials go directly browser → server.
     *
     * POST /api/v1/auth/device/activate
     */
    @Operation(
            summary = "Activate device (Browser step 2)",
            description = "User enters credentials + user_code in browser. " +
                    "Credentials never pass through the CLI."
    )
    @PostMapping("/activate")
    public ResponseEntity<CheckResponse<Void>> activate(
            @Valid @RequestBody DeviceActivateRequest request) {

        try {
            activateDeviceUseCase.execute(request);
            return ResponseEntity.ok(CheckResponse.<Void>builder()
                    .success(true)
                    .message("Device activated successfully. You may close this tab.")
                    .build());

        } catch (DomainException e) {
            log.warn("Device activation failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(CheckResponse.<Void>builder()
                            .success(false)
                            .message(e.getMessage())
                            .build());
        } catch (Exception e) {
            log.error("Unexpected error during device activation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CheckResponse.<Void>builder()
                            .success(false)
                            .message("Internal server error")
                            .build());
        }
    }

    /**
     * STEP 3 — CLI polls this every 5 seconds until approved.
     *
     * Responses:
     *   202 → { error: "authorization_pending" }  still waiting
     *   200 → { access_token, refresh_token, ... } done!
     *   400 → { error: "expired_token" }           timed out
     *
     * POST /api/v1/auth/device/token
     */
    @Operation(
            summary = "Poll for token (CLI step 3)",
            description = "CLI polls every 5 seconds. " +
                    "Returns 202 while pending, 200 with tokens when approved."
    )
    @PostMapping("/token")
    public ResponseEntity<?> pollToken(
            @Valid @RequestBody DeviceTokenRequest request) {

        try {
            AuthResponse authResponse = pollDeviceTokenUseCase.execute(request);
            return ResponseEntity.ok(CheckResponse.<AuthResponse>builder()
                    .success(true)
                    .message("Authentication successful")
                    .data(authResponse)
                    .build());

        } catch (PollDeviceTokenUseCase.PendingException e) {
            // 202 Accepted — CLI should keep polling
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of("error", "authorization_pending",
                            "message", "Waiting for user to approve on browser"));

        } catch (DomainException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error during token poll", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "server_error"));
        }
    }
}