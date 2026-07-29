package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Unlinks a device from its JWT ahead of the TTL — CLI logout. Also drops the login
 * session the token came from, so nothing is left behind that could hand the same token
 * out again.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RevokeCliDeviceUseCase {

    private final CliAuthSessionCachePort sessionCache;

    public void execute(String deviceId) {
        sessionCache.findDevice(deviceId)
                .map(CliAuthSessionCachePort.CliDeviceAuth::sessionId)
                .ifPresent(sessionCache::delete);

        sessionCache.deleteDevice(deviceId);
        log.info("CLI device binding revoked: deviceId={}", deviceId);
    }
}
