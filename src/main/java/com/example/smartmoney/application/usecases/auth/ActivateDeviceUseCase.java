package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.requests.auth.DeviceActivateRequest;
import com.example.smartmoney.application.ports.auth.AuthPort;
import com.example.smartmoney.application.ports.device.DeviceFlowCachePort;
import com.example.smartmoney.application.ports.device.DeviceFlowCachePort.DeviceSession;
import com.example.smartmoney.domain.exceptions.AuthenticationException;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Step 2 of Device Flow:
 * User opens browser, enters email + password + user_code
 * → session becomes APPROVED, userId stored in Redis
 *
 * Called by POST /api/v1/auth/device/activate
 * (This endpoint is hit from the browser, NOT from the CLI)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ActivateDeviceUseCase {

    private final DeviceFlowCachePort deviceFlowCache;
    private final UserRepository      userRepository;
    private final AuthPort            authPort;

    public void execute(DeviceActivateRequest request) {
        // 1. Find deviceCode via userCode
        String userCode   = request.getUserCode().toUpperCase().trim();
        String deviceCode = deviceFlowCache.findDeviceCodeByUserCode(userCode)
                .orElseThrow(() -> new DomainException("Invalid or expired user code"));

        // 2. Check session is still PENDING
        DeviceSession session = deviceFlowCache.findByDeviceCode(deviceCode)
                .orElseThrow(() -> new DomainException("Device session expired"));

        if (!"PENDING".equals(session.status())) {
            throw new DomainException("Device code already used");
        }

        // 3. Validate user credentials (email + password) — same as normal login
        Email email = new Email(request.getEmail());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationException("Invalid credentials"));

        if (!user.isEmailVerified()) {
            throw new DomainException("Please verify your email first");
        }

        if (!authPort.validatePassword(request.getPassword(), user.getPassword().getHash())) {
            throw new AuthenticationException("Invalid credentials");
        }

        if (!user.isActive()) {
            throw new DomainException("Account is deactivated");
        }

        // 4. Approve session — store userId so CLI can pick it up
        deviceFlowCache.approve(deviceCode, user.getId().toString());

        log.info("Device activated for userId={}, userCode={}", user.getId(), userCode);
    }
}