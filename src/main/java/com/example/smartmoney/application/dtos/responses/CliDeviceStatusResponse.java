package com.example.smartmoney.application.dtos.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Returned by GET /api/v1/auth/cli/device/{deviceId}/status.
 *
 * This is the check the CLI/MCP runs before answering anything: "does Redis hold a JWT
 * for my device id?". Always 200 — "no token for this device" is a normal answer, not an
 * error, and simply means the CLI should send the user through the login flow.
 */
@Data
@Builder
public class CliDeviceStatusResponse {

    @JsonProperty("device_id")
    private String deviceId;

    /** True only while a JWT is still linked to this device in Redis. */
    private boolean authenticated;

    /** Whether the cached value actually carries a token — the literal has-a-JWT answer. */
    @JsonProperty("has_jwt")
    private boolean hasJwt;

    /** AUTHENTICATED | NO_DEVICE_SESSION */
    private String status;

    /** Seconds left before Redis drops the binding (0 when it's already gone). */
    @JsonProperty("expires_in")
    private long expiresIn;

    private String email;
}
