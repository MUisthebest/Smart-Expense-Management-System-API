package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.responses.CliSessionTokenResponse;
import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort;
import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort.CliAuthSession;
import com.example.smartmoney.domain.exceptions.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Step 3 of the CLI browser login: the CLI polls GET /api/v1/auth/cli/session/{id}/token
 * until the user has approved in their browser.
 *
 * Unlike the old device flow this does NOT consume the session — the same token stays
 * readable for the rest of its hour so a restarted CLI can pick it back up, and so the
 * Redis key remains the single thing that decides whether the CLI is still logged in.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PollCliSessionTokenUseCase {

    private final CliAuthSessionCachePort sessionCache;

    public static class PendingException extends RuntimeException {
        public PendingException() { super("authorization_pending"); }
    }

    public CliSessionTokenResponse execute(String sessionId) {
        CliAuthSession session = sessionCache.find(sessionId)
                .orElseThrow(() -> new DomainException("expired_session"));

        if (CliAuthSessionCachePort.STATUS_PENDING.equals(session.status())) {
            throw new PendingException();
        }

        return CliSessionTokenResponse.builder()
                .accessToken(session.accessToken())
                .tokenType("Bearer")
                .expiresIn(sessionCache.ttlSeconds(sessionId))
                .email(session.userEmail())
                .build();
    }
}
