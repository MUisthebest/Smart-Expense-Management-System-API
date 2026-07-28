package com.example.smartmoney.application.dtos.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Returned by GET /api/v1/auth/cli/session/{sessionId}/token once the user has approved.
 *
 * No refresh token by design: the CLI session is deliberately short-lived (it dies with
 * the Redis key, one hour after approval) and issuing one here would rotate — and so
 * invalidate — the refresh token of the browser session the user just logged in with.
 */
@Data
@Builder
public class CliSessionTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    /** Seconds left on the Redis key — the CLI should treat this as the token's lifetime. */
    @JsonProperty("expires_in")
    private long expiresIn;

    private String email;
}
