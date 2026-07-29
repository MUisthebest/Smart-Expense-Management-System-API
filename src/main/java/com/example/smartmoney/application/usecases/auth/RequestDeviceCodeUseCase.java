package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.requests.auth.DeviceCodeRequest;
import com.example.smartmoney.application.dtos.responses.DeviceCodeResponse;
import com.example.smartmoney.application.ports.device.DeviceFlowCachePort;
import com.example.smartmoney.application.ports.device.DeviceFlowCachePort.DeviceSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Step 1 of Device Flow:
 * CLI calls POST /api/v1/auth/device/code
 * → receives device_code, user_code, verification_uri
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestDeviceCodeUseCase {

    private final DeviceFlowCachePort deviceFlowCache;

    /**
     * Frontend host the activation link points at. Configured via app.frontend-url
     * (APP_FRONTEND_URL in the environment); the localhost default is dev-only.
     */
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    private static final int EXPIRES_IN = 300;  // 5 minutes
    private static final int INTERVAL   = 5;    // poll every 5 seconds
    private static final String CHARS   = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no confusing chars

    public DeviceCodeResponse execute(DeviceCodeRequest request) {
        String deviceCode = UUID.randomUUID().toString();
        String userCode   = generateUserCode();

        DeviceSession session = new DeviceSession(
                deviceCode,
                userCode,
                request.getClientId(),
                "PENDING",
                null,
                EXPIRES_IN
        );

        deviceFlowCache.saveSession(session);

        log.info("Device flow initiated: userCode={}, clientId={}", userCode, request.getClientId());

        String base = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;

        return DeviceCodeResponse.builder()
                .deviceCode(deviceCode)
                .userCode(userCode)
                .verificationUri(base + "/en/activate?code=" + userCode)
                .expiresIn(EXPIRES_IN)
                .interval(INTERVAL)
                .build();
    }

    /** Generates an 8-char code like "ABCD-1234" */
    private String generateUserCode() {
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(9);
        for (int i = 0; i < 4; i++) sb.append(CHARS.charAt(rng.nextInt(CHARS.length())));
        sb.append('-');
        for (int i = 0; i < 4; i++) sb.append(CHARS.charAt(rng.nextInt(CHARS.length())));
        return sb.toString();
    }
}