package com.example.smartmoney.application.dtos.responses;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * Returned by POST /api/v1/auth/cli/session.
 *
 * login_url points at the normal web login page with ?cli={session_id} — there is no
 * user code to read out or type in, the CLI just opens the link and polls.
 */
@Data
@Builder
public class CliSessionResponse {

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("login_url")
    private String loginUrl;

    /** Seconds the user has to finish logging in before the pending session is dropped. */
    @JsonProperty("expires_in")
    private int expiresIn;

    /** Suggested seconds between polls of /session/{id}/token. */
    private int interval;
}
