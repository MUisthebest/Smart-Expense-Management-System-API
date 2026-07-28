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

    /** Locale segment for the returned login URL (the Next.js app is locale-prefixed). */
    private String locale;
}
