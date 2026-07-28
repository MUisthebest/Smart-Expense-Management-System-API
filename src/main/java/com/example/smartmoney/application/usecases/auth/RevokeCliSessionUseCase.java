package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Drops a CLI session's JWT from Redis ahead of its TTL — used by CLI logout so the token
 * stops counting as live the moment the user says so, not an hour later.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RevokeCliSessionUseCase {

    private final CliAuthSessionCachePort sessionCache;

    public void execute(String sessionId) {
        sessionCache.delete(sessionId);
        log.info("CLI login session revoked: sessionId={}", sessionId);
    }
}
