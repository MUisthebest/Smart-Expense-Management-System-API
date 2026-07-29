package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.responses.CliSessionTokenResponse;
import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort;
import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort.CliDeviceAuth;
import com.example.smartmoney.domain.exceptions.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Hands back the JWT currently linked to a device, so a CLI that only remembers its own
 * device id (fresh install, cleared credentials file, restarted machine) can pick its
 * token back up without sending the user through the browser again.
 *
 * Does not consume the binding — it stays readable for the rest of its hour, because the
 * Redis key, not the caller, is what decides when the login ends.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GetCliDeviceTokenUseCase {

    private final CliAuthSessionCachePort sessionCache;

    public CliSessionTokenResponse execute(String deviceId) {
        CliDeviceAuth device = sessionCache.findDevice(deviceId)
                .orElseThrow(() -> new DomainException("no_device_session"));

        if (device.accessToken() == null || device.accessToken().isBlank()) {
            throw new DomainException("no_device_session");
        }

        return CliSessionTokenResponse.builder()
                .accessToken(device.accessToken())
                .tokenType("Bearer")
                .expiresIn(sessionCache.deviceTtlSeconds(deviceId))
                .email(device.userEmail())
                .build();
    }
}
