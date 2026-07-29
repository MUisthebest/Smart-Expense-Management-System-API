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
 *
 * The request also carries the caller's device id, which is remembered on the pending
 * session so approval knows which device to link the minted JWT to. A caller that doesn't
 * send one gets a fresh id minted here and returned in the response.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StartCliLoginSessionUseCase {

    private final CliAuthSessionCachePort sessionCache;

    /**
     * Frontend host the CLI is redirected to. Configured via app.frontend-url
     * (APP_FRONTEND_URL in the environment); the localhost default is dev-only.
     */
    @Value("${app.frontend-url:http://localhost:3000}")
    private String frontendUrl;

    /** How long the user has to finish logging in before the pending session is dropped. */
    public static final int PENDING_EXPIRES_IN = 600;   // 10 minutes
    public static final int POLL_INTERVAL      = 3;     // seconds

    private static final Set<String> SUPPORTED_LOCALES = Set.of("en", "vi");
    private static final String DEFAULT_LOCALE = "en";

    public CliSessionResponse execute(CliSessionRequest request) {
        String sessionId = UUID.randomUUID().toString();
        String deviceId  = normalizeDeviceId(request.getDeviceId());

        sessionCache.save(
                CliAuthSession.pending(sessionId, request.getClientId(), deviceId),
                Duration.ofSeconds(PENDING_EXPIRES_IN));

        log.info("CLI login session started: sessionId={}, clientId={}, deviceId={}",
                sessionId, request.getClientId(), deviceId);

        return CliSessionResponse.builder()
                .sessionId(sessionId)
                .deviceId(deviceId)
                .loginUrl(buildLoginUrl(sessionId, request.getLocale()))
                .expiresIn(PENDING_EXPIRES_IN)
                .interval(POLL_INTERVAL)
                .build();
    }

    /** Use the caller's device id when it sent one; otherwise mint one for it. */
    private String normalizeDeviceId(String requested) {
        return (requested != null && !requested.isBlank())
                ? requested.trim()
                : UUID.randomUUID().toString();
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
