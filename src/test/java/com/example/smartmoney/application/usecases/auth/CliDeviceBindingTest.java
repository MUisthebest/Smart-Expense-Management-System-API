package com.example.smartmoney.application.usecases.auth;

import com.example.smartmoney.application.dtos.requests.auth.CliSessionRequest;
import com.example.smartmoney.application.dtos.responses.CliDeviceStatusResponse;
import com.example.smartmoney.application.dtos.responses.CliSessionResponse;
import com.example.smartmoney.application.dtos.responses.CliSessionTokenResponse;
import com.example.smartmoney.application.ports.auth.AuthPort;
import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort;
import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort.CliAuthSession;
import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort.CliDeviceAuth;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.valueobjects.Email;
import com.example.smartmoney.domain.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the deviceId→JWT binding: the CLI/MCP authenticates by presenting its device id
 * and asking whether Redis still holds a token for it, so what matters here is that
 * approval actually writes that key, that the status/token reads agree with it, and that
 * revoking clears it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CLI device binding (cli:device:{deviceId} → JWT)")
class CliDeviceBindingTest {

    private static final String DEVICE_ID  = "device-abc";
    private static final String SESSION_ID = "session-xyz";
    private static final String JWT        = "minted.jwt.value";
    private static final String FRONTEND_URL = "https://frontend.example.test";

    @Mock private CliAuthSessionCachePort sessionCache;
    @Mock private UserRepository          userRepository;
    @Mock private AuthPort                authPort;

    private StartCliLoginSessionUseCase   start;
    private ApproveCliLoginSessionUseCase approve;
    private GetCliDeviceStatusUseCase     deviceStatus;
    private GetCliDeviceTokenUseCase      deviceToken;
    private RevokeCliDeviceUseCase        revokeDevice;
    private RevokeCliSessionUseCase       revokeSession;

    private UUID userId;

    @BeforeEach
    void setUp() {
        start = new StartCliLoginSessionUseCase(sessionCache);
        // Stand-in for whatever APP_FRONTEND_URL is in a real environment — the test
        // asserts the URL shape, not a particular deployed host.
        ReflectionTestUtils.setField(start, "frontendUrl", FRONTEND_URL);

        approve       = new ApproveCliLoginSessionUseCase(sessionCache, userRepository, authPort);
        deviceStatus  = new GetCliDeviceStatusUseCase(sessionCache);
        deviceToken   = new GetCliDeviceTokenUseCase(sessionCache);
        revokeDevice  = new RevokeCliDeviceUseCase(sessionCache);
        revokeSession = new RevokeCliSessionUseCase(sessionCache);

        userId = UUID.randomUUID();
        // active must be set explicitly — the builder bypasses the field initializer,
        // and approval rejects a deactivated account.
        User user = User.builder()
                .id(userId)
                .username("tester")
                .email(new Email("tester@example.com"))
                .active(true)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(authPort.generateAccessToken(any(User.class))).thenReturn(JWT);
    }

    // ── starting a login ──────────────────────────────────────────────────────

    @Test
    @DisplayName("carries the caller's device id onto the pending session and echoes it back")
    void startKeepsCallerDeviceId() {
        CliSessionResponse response = start.execute(request(DEVICE_ID));

        assertThat(response.getDeviceId()).isEqualTo(DEVICE_ID);

        ArgumentCaptor<CliAuthSession> saved = ArgumentCaptor.forClass(CliAuthSession.class);
        verify(sessionCache).save(saved.capture(), any(Duration.class));
        assertThat(saved.getValue().deviceId()).isEqualTo(DEVICE_ID);
        assertThat(saved.getValue().status()).isEqualTo(CliAuthSessionCachePort.STATUS_PENDING);
    }

    @Test
    @DisplayName("mints a device id when the caller sends none (older CLI)")
    void startMintsDeviceIdWhenMissing() {
        assertThat(start.execute(request(null)).getDeviceId()).isNotBlank();
        assertThat(start.execute(request("   ")).getDeviceId()).isNotBlank();
    }

    @Test
    @DisplayName("builds the login URL on the configured frontend, with exactly one locale segment")
    void startBuildsDeployedLoginUrl() {
        CliSessionResponse response = start.execute(request(DEVICE_ID));

        assertThat(response.getLoginUrl())
                .startsWith(FRONTEND_URL + "/en/login?cli=")
                .doesNotContain("/en/en/");
    }

    // ── approving ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("approval links the device to the minted JWT, with the token's own TTL")
    void approveWritesDeviceBinding() {
        when(sessionCache.find(SESSION_ID)).thenReturn(Optional.of(pendingSession(DEVICE_ID)));

        approve.execute(SESSION_ID, userId);

        ArgumentCaptor<CliDeviceAuth> device = ArgumentCaptor.forClass(CliDeviceAuth.class);
        ArgumentCaptor<Duration>      ttl    = ArgumentCaptor.forClass(Duration.class);
        verify(sessionCache).saveDevice(device.capture(), ttl.capture());

        assertThat(device.getValue().deviceId()).isEqualTo(DEVICE_ID);
        assertThat(device.getValue().accessToken()).isEqualTo(JWT);
        assertThat(device.getValue().userId()).isEqualTo(userId.toString());
        assertThat(device.getValue().sessionId()).isEqualTo(SESSION_ID);
        assertThat(ttl.getValue())
                .isEqualTo(Duration.ofSeconds(ApproveCliLoginSessionUseCase.APPROVED_TTL_SECONDS));
    }

    @Test
    @DisplayName("session key and device key hold the same token, so neither can outlive the other")
    void approveKeepsBothKeysConsistent() {
        when(sessionCache.find(SESSION_ID)).thenReturn(Optional.of(pendingSession(DEVICE_ID)));

        approve.execute(SESSION_ID, userId);

        ArgumentCaptor<CliAuthSession> session = ArgumentCaptor.forClass(CliAuthSession.class);
        ArgumentCaptor<CliDeviceAuth>  device  = ArgumentCaptor.forClass(CliDeviceAuth.class);
        verify(sessionCache).save(session.capture(), eq(Duration.ofSeconds(3600)));
        verify(sessionCache).saveDevice(device.capture(), eq(Duration.ofSeconds(3600)));

        assertThat(session.getValue().accessToken()).isEqualTo(device.getValue().accessToken());
        assertThat(session.getValue().deviceId()).isEqualTo(device.getValue().deviceId());
        assertThat(session.getValue().status()).isEqualTo(CliAuthSessionCachePort.STATUS_APPROVED);
    }

    @Test
    @DisplayName("a session with no device id approves without writing a device key")
    void approveSkipsBindingWhenSessionHasNoDevice() {
        when(sessionCache.find(SESSION_ID)).thenReturn(Optional.of(pendingSession(null)));

        approve.execute(SESSION_ID, userId);

        verify(sessionCache).save(any(CliAuthSession.class), any(Duration.class));
        verify(sessionCache, never()).saveDevice(any(), any());
    }

    // ── the check the CLI runs before every query ─────────────────────────────

    @Test
    @DisplayName("unknown device → has_jwt false, which is what sends the user to log in")
    void statusReportsNoJwtForUnknownDevice() {
        when(sessionCache.findDevice(DEVICE_ID)).thenReturn(Optional.empty());

        CliDeviceStatusResponse status = deviceStatus.execute(DEVICE_ID);

        assertThat(status.isAuthenticated()).isFalse();
        assertThat(status.isHasJwt()).isFalse();
        assertThat(status.getStatus()).isEqualTo("NO_DEVICE_SESSION");
        assertThat(status.getExpiresIn()).isZero();
    }

    @Test
    @DisplayName("linked device → has_jwt true, with the remaining TTL and the owner's email")
    void statusReportsJwtForLinkedDevice() {
        when(sessionCache.findDevice(DEVICE_ID)).thenReturn(Optional.of(binding(JWT)));
        when(sessionCache.deviceTtlSeconds(DEVICE_ID)).thenReturn(3400L);

        CliDeviceStatusResponse status = deviceStatus.execute(DEVICE_ID);

        assertThat(status.isAuthenticated()).isTrue();
        assertThat(status.isHasJwt()).isTrue();
        assertThat(status.getStatus()).isEqualTo("AUTHENTICATED");
        assertThat(status.getExpiresIn()).isEqualTo(3400L);
        assertThat(status.getEmail()).isEqualTo("tester@example.com");
    }

    @Test
    @DisplayName("a binding cached without a token is not authenticated")
    void statusRejectsBindingWithoutToken() {
        when(sessionCache.findDevice(DEVICE_ID)).thenReturn(Optional.of(binding("  ")));

        assertThat(deviceStatus.execute(DEVICE_ID).isHasJwt()).isFalse();
    }

    // ── picking the token back up ─────────────────────────────────────────────

    @Test
    @DisplayName("a CLI that lost its local token can collect it again with its device id")
    void tokenEndpointReturnsCachedJwt() {
        when(sessionCache.findDevice(DEVICE_ID)).thenReturn(Optional.of(binding(JWT)));
        when(sessionCache.deviceTtlSeconds(DEVICE_ID)).thenReturn(1800L);

        CliSessionTokenResponse token = deviceToken.execute(DEVICE_ID);

        assertThat(token.getAccessToken()).isEqualTo(JWT);
        assertThat(token.getTokenType()).isEqualTo("Bearer");
        assertThat(token.getExpiresIn()).isEqualTo(1800L);
    }

    @Test
    @DisplayName("no binding → no_device_session rather than a null token")
    void tokenEndpointRejectsUnknownDevice() {
        when(sessionCache.findDevice(DEVICE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> deviceToken.execute(DEVICE_ID))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("no_device_session");
    }

    // ── logout ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("unlinking a device also drops the session its token came from")
    void revokingDeviceClearsBothKeys() {
        when(sessionCache.findDevice(DEVICE_ID)).thenReturn(Optional.of(binding(JWT)));

        revokeDevice.execute(DEVICE_ID);

        verify(sessionCache).deleteDevice(DEVICE_ID);
        verify(sessionCache).delete(SESSION_ID);
    }

    @Test
    @DisplayName("revoking a session also unlinks its device — otherwise the CLI stays logged in")
    void revokingSessionClearsBothKeys() {
        when(sessionCache.find(SESSION_ID)).thenReturn(Optional.of(pendingSession(DEVICE_ID)));

        revokeSession.execute(SESSION_ID);

        verify(sessionCache).delete(SESSION_ID);
        verify(sessionCache).deleteDevice(DEVICE_ID);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CliSessionRequest request(String deviceId) {
        CliSessionRequest request = new CliSessionRequest();
        request.setClientId("smartmoney-cli");
        request.setDeviceId(deviceId);
        request.setLocale("en");
        return request;
    }

    private CliAuthSession pendingSession(String deviceId) {
        return CliAuthSession.pending(SESSION_ID, "smartmoney-cli", deviceId);
    }

    private CliDeviceAuth binding(String token) {
        return new CliDeviceAuth(DEVICE_ID, "smartmoney-cli", SESSION_ID,
                userId.toString(), "tester@example.com", token);
    }
}
