package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.responses.CliSessionStatusResponse;
import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort;
import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort.CliAuthSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * "Is this CLI session still logged in?" — the check Claude Desktop runs before answering
 * a prompt. Never throws: a session that has already been evicted from Redis simply comes
 * back as EXPIRED / authenticated=false.
 */
@Service
@RequiredArgsConstructor
public class GetCliSessionStatusUseCase {

    private final CliAuthSessionCachePort sessionCache;

    public CliSessionStatusResponse execute(String sessionId) {
        Optional<CliAuthSession> found = sessionCache.find(sessionId);

        if (found.isEmpty()) {
            return CliSessionStatusResponse.builder()
                    .authenticated(false)
                    .status("EXPIRED")
                    .expiresIn(0)
                    .build();
        }

        CliAuthSession session = found.get();
        boolean approved = CliAuthSessionCachePort.STATUS_APPROVED.equals(session.status());

        return CliSessionStatusResponse.builder()
                .authenticated(approved)
                .status(session.status())
                .expiresIn(sessionCache.ttlSeconds(sessionId))
                .email(session.userEmail())
                .build();
    }
}
