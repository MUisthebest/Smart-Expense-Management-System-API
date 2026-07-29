package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.responses.CliDeviceStatusResponse;
import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort;
import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort.CliDeviceAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * "Is there a JWT in Redis for this device?" — the check the CLI/MCP runs before it
 * answers any question that needs SmartMoney data. Never throws: a device that was never
 * linked, or whose hour is up, simply comes back authenticated=false and the CLI asks the
 * user to log in.
 */
@Service
@RequiredArgsConstructor
public class GetCliDeviceStatusUseCase {

    private final CliAuthSessionCachePort sessionCache;

    public CliDeviceStatusResponse execute(String deviceId) {
        Optional<CliDeviceAuth> found = sessionCache.findDevice(deviceId);

        if (found.isEmpty()) {
            return CliDeviceStatusResponse.builder()
                    .deviceId(deviceId)
                    .authenticated(false)
                    .hasJwt(false)
                    .status("NO_DEVICE_SESSION")
                    .expiresIn(0)
                    .build();
        }

        CliDeviceAuth device = found.get();
        boolean hasJwt = device.accessToken() != null && !device.accessToken().isBlank();

        return CliDeviceStatusResponse.builder()
                .deviceId(deviceId)
                .authenticated(hasJwt)
                .hasJwt(hasJwt)
                .status(hasJwt ? "AUTHENTICATED" : "NO_DEVICE_SESSION")
                .expiresIn(sessionCache.deviceTtlSeconds(deviceId))
                .email(device.userEmail())
                .build();
    }
}
