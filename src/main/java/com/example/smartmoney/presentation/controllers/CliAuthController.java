package com.example.smartmoney.presentation.controllers;

import com.example.smartmoney.application.dtos.requests.auth.CliSessionRequest;
import com.example.smartmoney.application.dtos.responses.CheckResponse;
import com.example.smartmoney.application.dtos.responses.CliDeviceStatusResponse;
import com.example.smartmoney.application.dtos.responses.CliSessionResponse;
import com.example.smartmoney.application.dtos.responses.CliSessionStatusResponse;
import com.example.smartmoney.application.dtos.responses.CliSessionTokenResponse;
import com.example.smartmoney.application.usecases.auth.ApproveCliLoginSessionUseCase;
import com.example.smartmoney.application.usecases.auth.GetCliDeviceStatusUseCase;
import com.example.smartmoney.application.usecases.auth.GetCliDeviceTokenUseCase;
import com.example.smartmoney.application.usecases.auth.GetCliSessionStatusUseCase;
import com.example.smartmoney.application.usecases.auth.PollCliSessionTokenUseCase;
import com.example.smartmoney.application.usecases.auth.RevokeCliDeviceUseCase;
import com.example.smartmoney.application.usecases.auth.RevokeCliSessionUseCase;
import com.example.smartmoney.application.usecases.auth.StartCliLoginSessionUseCase;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.infrastructure.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Browser login for the CLI / MCP server (Claude Desktop).
 *
 * Supersedes the OAuth Device Flow under /api/v1/auth/device — same idea, minus the
 * device/user code: the CLI holds one opaque session id, the user just logs in on the
 * ordinary web login page, and the resulting JWT is parked in Redis for exactly as long
 * as it is valid (1 hour). When the key expires the CLI is unauthenticated again and
 * prompts for a fresh login, so an expired JWT is never left lying around in the cache.
 *
 *   1. CLI     POST   /api/v1/auth/cli/session                  → session_id + login_url
 *   2. Browser POST   /api/v1/auth/cli/session/{id}/approve     → JWT stored in Redis (1h)
 *   3. CLI     GET    /api/v1/auth/cli/session/{id}/token       → picks the JWT up
 *      CLI     GET    /api/v1/auth/cli/session/{id}/status      → still logged in?
 *      CLI     DELETE /api/v1/auth/cli/session/{id}             → logout
 *
 * The session id above covers one login. What the CLI uses from then on is its device id,
 * which approval links to the same JWT under cli:device:{deviceId}:
 *
 *      CLI     GET    /api/v1/auth/cli/device/{deviceId}/status → is there a JWT for me?
 *      CLI     GET    /api/v1/auth/cli/device/{deviceId}/token  → give me that JWT
 *      CLI     DELETE /api/v1/auth/cli/device/{deviceId}        → unlink (logout)
 *
 * So a CLI that has kept nothing but its own device id can ask the status endpoint whether
 * it is still logged in, and either carry on or send the user through the browser again.
 */
@RestController
@RequestMapping("/api/v1/auth/cli")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "CLI Auth", description = "Browser login session for the CLI / MCP server")
public class CliAuthController {

    private final StartCliLoginSessionUseCase   startCliLoginSessionUseCase;
    private final ApproveCliLoginSessionUseCase approveCliLoginSessionUseCase;
    private final PollCliSessionTokenUseCase    pollCliSessionTokenUseCase;
    private final GetCliSessionStatusUseCase    getCliSessionStatusUseCase;
    private final RevokeCliSessionUseCase       revokeCliSessionUseCase;
    private final GetCliDeviceStatusUseCase     getCliDeviceStatusUseCase;
    private final GetCliDeviceTokenUseCase      getCliDeviceTokenUseCase;
    private final RevokeCliDeviceUseCase        revokeCliDeviceUseCase;

    /**
     * STEP 1 — CLI starts a login session.
     * POST /api/v1/auth/cli/session
     */
    @Operation(
            summary = "Start a CLI login session (CLI step 1)",
            description = "Returns an opaque session_id and the web login URL carrying it. " +
                    "Show login_url to the user, then poll /session/{id}/token."
    )
    @PostMapping("/session")
    public ResponseEntity<CheckResponse<CliSessionResponse>> startSession(
            @Valid @RequestBody CliSessionRequest request) {

        try {
            CliSessionResponse data = startCliLoginSessionUseCase.execute(request);
            return ResponseEntity.ok(CheckResponse.success(
                    "CLI login session created. Open login_url in a browser.", data));
        } catch (Exception e) {
            log.error("Failed to start CLI login session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CheckResponse.error(e.getMessage()));
        }
    }

    /**
     * STEP 2 — the web login page calls this once the user is authenticated there,
     * with that user's own Bearer token. Credentials never pass through the CLI.
     *
     * POST /api/v1/auth/cli/session/{sessionId}/approve
     */
    @Operation(
            summary = "Approve a CLI login session (Browser step 2)",
            description = "Called from the web login page after a successful login (or from an " +
                    "already-signed-in session, on explicit confirmation). Requires a Bearer token."
    )
    @PostMapping("/session/{sessionId}/approve")
    public ResponseEntity<CheckResponse<Void>> approve(
            @PathVariable String sessionId,
            @Parameter(hidden = true) @CurrentUser UUID userId) {

        // /api/v1/auth/** is permitAll, so an anonymous call reaches us with a null userId.
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(CheckResponse.error("You must be logged in to approve this session"));
        }

        try {
            approveCliLoginSessionUseCase.execute(sessionId, userId);
            return ResponseEntity.ok(CheckResponse.<Void>builder()
                    .success(true)
                    .message("Authenticated. You can go back to your terminal or chat.")
                    .build());

        } catch (DomainException e) {
            log.warn("CLI session approval failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(CheckResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error approving CLI session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(CheckResponse.error("Internal server error"));
        }
    }

    /**
     * STEP 3 — CLI polls until the browser side approves.
     *
     * 202 → { error: "authorization_pending" }  still waiting
     * 200 → { access_token, expires_in, ... }   done
     * 400 → { error: "expired_session" }        session gone (never started, or TTL elapsed)
     *
     * GET /api/v1/auth/cli/session/{sessionId}/token
     */
    @Operation(
            summary = "Poll for the CLI token (CLI step 3)",
            description = "202 while the user hasn't finished logging in, 200 with the access token once they have."
    )
    @GetMapping("/session/{sessionId}/token")
    public ResponseEntity<?> pollToken(@PathVariable String sessionId) {
        try {
            CliSessionTokenResponse data = pollCliSessionTokenUseCase.execute(sessionId);
            return ResponseEntity.ok(CheckResponse.success("Authentication successful", data));

        } catch (PollCliSessionTokenUseCase.PendingException e) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of("error", "authorization_pending",
                            "message", "Waiting for the user to log in on the browser"));

        } catch (DomainException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error polling CLI session token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "server_error"));
        }
    }

    /**
     * Liveness check — the CLI calls this before answering a prompt. Redis is the source of
     * truth: once the key expires this returns authenticated=false and the user is asked to
     * log in again.
     *
     * GET /api/v1/auth/cli/session/{sessionId}/status
     */
    @Operation(
            summary = "Check whether a CLI session is still authenticated",
            description = "Always 200. authenticated=false once the session's hour in Redis is up."
    )
    @GetMapping("/session/{sessionId}/status")
    public ResponseEntity<CheckResponse<CliSessionStatusResponse>> status(@PathVariable String sessionId) {
        CliSessionStatusResponse data = getCliSessionStatusUseCase.execute(sessionId);
        return ResponseEntity.ok(CheckResponse.success(data));
    }

    /**
     * Logout — drop the JWT from Redis now instead of waiting out its TTL.
     *
     * DELETE /api/v1/auth/cli/session/{sessionId}
     */
    @Operation(summary = "Revoke a CLI session (logout)")
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<CheckResponse<Void>> revoke(@PathVariable String sessionId) {
        revokeCliSessionUseCase.execute(sessionId);
        return ResponseEntity.ok(CheckResponse.<Void>builder()
                .success(true)
                .message("CLI session revoked.")
                .build());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // DEVICE BINDING — what the CLI authenticates against after the first login
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * "Is there a JWT in Redis for my device id?" — the check the CLI runs before
     * answering anything that needs SmartMoney data.
     *
     * Always 200. authenticated=false (has_jwt=false) means the device was never linked or
     * its hour is up, and the CLI should send the user through the login flow.
     *
     * GET /api/v1/auth/cli/device/{deviceId}/status
     */
    @Operation(
            summary = "Check whether a device is linked to a live JWT",
            description = "Always 200. has_jwt=false means no token is cached for this device — log in."
    )
    @GetMapping("/device/{deviceId}/status")
    public ResponseEntity<CheckResponse<CliDeviceStatusResponse>> deviceStatus(@PathVariable String deviceId) {
        CliDeviceStatusResponse data = getCliDeviceStatusUseCase.execute(deviceId);
        return ResponseEntity.ok(CheckResponse.success(data));
    }

    /**
     * Hands the device's cached JWT back, so a CLI that has lost its local copy but still
     * knows its device id doesn't have to send the user through the browser again.
     *
     * 200 → { access_token, expires_in, ... }
     * 400 → { error: "no_device_session" }   nothing cached for this device
     *
     * GET /api/v1/auth/cli/device/{deviceId}/token
     */
    @Operation(
            summary = "Fetch the JWT linked to a device",
            description = "400 with no_device_session when the device has no live token in Redis."
    )
    @GetMapping("/device/{deviceId}/token")
    public ResponseEntity<?> deviceToken(@PathVariable String deviceId) {
        try {
            CliSessionTokenResponse data = getCliDeviceTokenUseCase.execute(deviceId);
            return ResponseEntity.ok(CheckResponse.success("Device is authenticated", data));

        } catch (DomainException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));

        } catch (Exception e) {
            log.error("Unexpected error reading CLI device token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "server_error"));
        }
    }

    /**
     * Unlink the device from its JWT — CLI logout.
     *
     * DELETE /api/v1/auth/cli/device/{deviceId}
     */
    @Operation(summary = "Unlink a device from its JWT (logout)")
    @DeleteMapping("/device/{deviceId}")
    public ResponseEntity<CheckResponse<Void>> revokeDevice(@PathVariable String deviceId) {
        revokeCliDeviceUseCase.execute(deviceId);
        return ResponseEntity.ok(CheckResponse.<Void>builder()
                .success(true)
                .message("Device unlinked.")
                .build());
    }
}
