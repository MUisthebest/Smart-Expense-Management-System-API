package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.ports.auth.AuthPort;
import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort;
import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort.CliAuthSession;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Step 2 of the CLI browser login, called from the web login page once the user is
 * authenticated there (POST /api/v1/auth/cli/session/{id}/approve, with that user's
 * Bearer token). Mints a fresh access token for the CLI and parks it in Redis.
 *
 * The Redis TTL is deliberately the same hour the JWT itself is valid for, so a token
 * that has expired can never still be sitting in the cache: when the key goes, the CLI's
 * next status check comes back unauthenticated and Claude Desktop asks for a new login.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApproveCliLoginSessionUseCase {

    private final CliAuthSessionCachePort sessionCache;
    private final UserRepository          userRepository;
    private final AuthPort                authPort;

    /** Must match app.jwt.access-token-expiration (3600000 ms). */
    public static final int APPROVED_TTL_SECONDS = 3600;   // 1 hour

    public void execute(String sessionId, UUID userId) {
        CliAuthSession session = sessionCache.find(sessionId)
                .orElseThrow(() -> new DomainException("expired_session"));

        if (!CliAuthSessionCachePort.STATUS_PENDING.equals(session.status())) {
            throw new DomainException("session_already_approved");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException("User not found"));

        if (!user.isActive()) {
            throw new DomainException("Account is deactivated");
        }

        String accessToken = authPort.generateAccessToken(user);

        sessionCache.save(
                new CliAuthSession(
                        sessionId,
                        session.clientId(),
                        CliAuthSessionCachePort.STATUS_APPROVED,
                        userId.toString(),
                        user.getEmail().getValue(),
                        accessToken),
                Duration.ofSeconds(APPROVED_TTL_SECONDS));

        log.info("CLI login session approved: sessionId={}, userId={}, ttl={}s",
                sessionId, userId, APPROVED_TTL_SECONDS);
    }
}
