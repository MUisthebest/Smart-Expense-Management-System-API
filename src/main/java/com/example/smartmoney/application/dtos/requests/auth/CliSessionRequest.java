package com.example.smartmoney.application.dtos.requests.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of POST /api/v1/auth/cli/session — the CLI/MCP asks for a browser-login session.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CliSessionRequest {

    @NotBlank(message = "clientId is required")
    private String clientId;

    /**
     * Stable identifier for the machine/install asking to log in. The CLI generates it
     * once and keeps it forever; on approval the backend links it to the minted JWT in
     * Redis, and from then on the CLI authenticates by presenting this id alone.
     *
     * Optional on the wire — an older CLI that doesn't send one still works: the backend
     * mints a device id for it and returns it in the response.
     */
    private String deviceId;

    /** Locale segment for the returned login URL (the Next.js app is locale-prefixed). */
    private String locale;
}
