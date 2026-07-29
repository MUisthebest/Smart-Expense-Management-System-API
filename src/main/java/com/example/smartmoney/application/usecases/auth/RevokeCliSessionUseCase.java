package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Drops a CLI session's JWT from Redis ahead of its TTL — used by CLI logout so the token
 * stops counting as live the moment the user says so, not an hour later.
 *
 * Takes the device binding with it: the two keys hold the same token, so clearing only
 * one would leave the CLI still authenticating happily off the other.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RevokeCliSessionUseCase {

    private final CliAuthSessionCachePort sessionCache;

    public void execute(String sessionId) {
        String deviceId = sessionCache.find(sessionId)
                .map(CliAuthSessionCachePort.CliAuthSession::deviceId)
                .orElse(null);

        sessionCache.delete(sessionId);

        if (deviceId != null && !deviceId.isBlank()) {
            sessionCache.deleteDevice(deviceId);
        }

        log.info("CLI login session revoked: sessionId={}, deviceId={}", sessionId, deviceId);
    }
}
