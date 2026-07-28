package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.requests.auth.CliSessionRequest;
import com.example.smartmoney.application.dtos.responses.CliSessionResponse;
import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort;
import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort.CliAuthSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * Step 1 of the CLI browser login:
 * the CLI calls POST /api/v1/auth/cli/session and gets back an opaque session id plus the
 * URL of the ordinary web login page carrying that id. Nothing is shown to the user except
 * the link — there is no device/user code to relay.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StartCliLoginSessionUseCase {

    private final CliAuthSessionCachePort sessionCache;

    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    /** How long the user has to finish logging in before the pending session is dropped. */
    public static final int PENDING_EXPIRES_IN = 600;   // 10 minutes
    public static final int POLL_INTERVAL      = 3;     // seconds

    private static final Set<String> SUPPORTED_LOCALES = Set.of("en", "vi");
    private static final String DEFAULT_LOCALE = "en";

    public CliSessionResponse execute(CliSessionRequest request) {
        String sessionId = UUID.randomUUID().toString();

        sessionCache.save(
                CliAuthSession.pending(sessionId, request.getClientId()),
                Duration.ofSeconds(PENDING_EXPIRES_IN));

        log.info("CLI login session started: sessionId={}, clientId={}", sessionId, request.getClientId());

        return CliSessionResponse.builder()
                .sessionId(sessionId)
                .loginUrl(buildLoginUrl(sessionId, request.getLocale()))
                .expiresIn(PENDING_EXPIRES_IN)
                .interval(POLL_INTERVAL)
                .build();
    }

    private String buildLoginUrl(String sessionId, String requestedLocale) {
        String base = frontendUrl.endsWith("/")
                ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;

        String locale = (requestedLocale != null && SUPPORTED_LOCALES.contains(requestedLocale.toLowerCase()))
                ? requestedLocale.toLowerCase()
                : DEFAULT_LOCALE;

        return base + "/" + locale + "/login?cli=" + sessionId;
    }
}
