package com.example.smartmoney.application.dtos.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Returned by GET /api/v1/auth/cli/session/{sessionId}/status.
 *
 * Always 200 — "this session no longer exists" is a normal answer, not an error, so the
 * CLI can call it on every conversation to decide whether it still counts as logged in.
 */
@Data
@Builder
public class CliSessionStatusResponse {

    /** True only while an APPROVED session is still alive in Redis. */
    private boolean authenticated;

    /** PENDING | APPROVED | EXPIRED */
    private String status;

    /** Seconds left before Redis drops the session (0 when it's already gone). */
    @JsonProperty("expires_in")
    private long expiresIn;

    private String email;
}
