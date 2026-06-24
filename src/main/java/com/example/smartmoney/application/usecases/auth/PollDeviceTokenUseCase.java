package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.requests.auth.DeviceTokenRequest;
import com.example.smartmoney.application.dtos.responses.AuthResponse;
import com.example.smartmoney.application.dtos.responses.UserResponse;
import com.example.smartmoney.application.ports.auth.AuthPort;
import com.example.smartmoney.application.ports.device.DeviceFlowCachePort;
import com.example.smartmoney.application.ports.device.DeviceFlowCachePort.DeviceSession;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.RefreshToken;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Step 3 of Device Flow:
 * CLI polls POST /api/v1/auth/device/token every 5 seconds.
 *
 * Returns:
 *   - 202 + error="authorization_pending"  → still waiting
 *   - 200 + AuthResponse                   → approved, tokens issued
 *   - 400 + error="expired_token"          → session expired
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PollDeviceTokenUseCase {

    private final DeviceFlowCachePort deviceFlowCache;
    private final UserRepository      userRepository;
    private final AuthPort            authPort;

    public static class PendingException extends RuntimeException {
        public PendingException() { super("authorization_pending"); }
    }

    @Transactional
    public AuthResponse execute(DeviceTokenRequest request) {
        DeviceSession session = deviceFlowCache.findByDeviceCode(request.getDeviceCode())
                .orElseThrow(() -> new DomainException("expired_token"));

        // Validate client_id matches
        if (!session.clientId().equals(request.getClientId())) {
            throw new DomainException("invalid_client");
        }

        return switch (session.status()) {
            case "PENDING"  -> throw new PendingException();
            case "APPROVED" -> issueTokens(session);
            default         -> throw new DomainException("expired_token");
        };
    }

    private AuthResponse issueTokens(DeviceSession session) {
        UUID userId = UUID.fromString(session.userId());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException("User not found"));

        String accessToken  = authPort.generateAccessToken(user);
        String refreshToken = authPort.generateRefreshToken(user);

        user.updateRefreshToken(new RefreshToken(refreshToken,
                java.time.LocalDateTime.now().plusDays(7)));
        userRepository.save(user);

        // Clean up session — one-time use
        deviceFlowCache.delete(session.deviceCode(), session.userCode());

        log.info("Device flow tokens issued for userId={}", userId);

        UserResponse userResponse = mapToUserResponse(user);
        return new AuthResponse(accessToken, refreshToken, "Bearer", 3600L, userResponse);
    }

    private UserResponse mapToUserResponse(User user) {
        UserResponse r = new UserResponse();
        r.setId(user.getId());
        r.setUsername(user.getUsername());
        r.setFullName(user.getFullName());
        r.setAvatar(user.getAvatar() != null ? user.getAvatar().getUrl() : null);
        r.setDateOfBirth(user.getDateOfBirth().getFormattedValue());
        r.setPhone(user.getPhone().getValue());
        r.setEmail(user.getEmail().getValue());
        r.setCoin(user.getCoin());
        r.setRole(user.getRole());
        r.setRate(user.getRate());
        r.setActive(user.isActive());
        return r;
    }
}