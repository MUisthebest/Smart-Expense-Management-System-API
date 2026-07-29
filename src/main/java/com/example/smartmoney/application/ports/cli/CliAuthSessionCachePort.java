package com.example.smartmoney.application.ports.cli;

import java.time.Duration;
import java.util.Optional;

/**
 * Port for the CLI / MCP browser-login session held in Redis.
 *
 * Replaces the OAuth Device Flow (device_code + user_code) with a single opaque
 * session id that the CLI keeps to itself: the browser never has to show or type
 * a code, it just opens the normal web login page with ?cli={sessionId}.
 *
 * Two keys are stored:
 *   cli:session:{sessionId} → CliAuthSession JSON   — one login attempt
 *   cli:device:{deviceId}   → CliDeviceAuth JSON    — the standing "this device is logged in" record
 *
 * The session key is per-login and short-lived by nature: it exists so the browser has
 * something to approve and the CLI has something to poll. The device key is what the CLI
 * actually authenticates against afterwards — it holds the deviceId→JWT link, so a CLI
 * that only remembers its own device id (and no token at all) can ask "is there a JWT
 * for me?" and either carry on or be sent to log in.
 *
 * TTL is the whole point of this port. While PENDING the session key lives only long
 * enough for the user to finish logging in; once APPROVED both keys are written with a
 * TTL equal to the access token's own lifetime (1 hour), so the JWT can never outlive its
 * Redis entry — and the CLI, which treats these keys as the source of truth, asks the
 * user to log in again the moment they expire.
 */
public interface CliAuthSessionCachePort {

    // ---- login session (cli:session:{sessionId}) ----

    /** Persist (or overwrite) a session with an explicit TTL. */
    void save(CliAuthSession session, Duration ttl);

    /** Look up a session by its id. Empty once the TTL has elapsed. */
    Optional<CliAuthSession> find(String sessionId);

    /** Remaining TTL in seconds, or 0 when the key is gone / has no expiry. */
    long ttlSeconds(String sessionId);

    /** Drop the session — used by logout and by an explicit revoke. */
    void delete(String sessionId);

    // ---- device binding (cli:device:{deviceId}) ----

    /**
     * Link a device to the JWT minted for it, with the same TTL as the token. Overwrites
     * any previous binding, so logging in again from the same device replaces the old JWT
     * rather than leaving two live entries behind.
     */
    void saveDevice(CliDeviceAuth device, Duration ttl);

    /** The JWT (and who it belongs to) currently linked to this device, if any. */
    Optional<CliDeviceAuth> findDevice(String deviceId);

    /** Remaining TTL in seconds on the device binding, 0 when there isn't one. */
    long deviceTtlSeconds(String deviceId);

    /** Unlink the device — logout, or an explicit revoke. */
    void deleteDevice(String deviceId);

    String STATUS_PENDING  = "PENDING";
    String STATUS_APPROVED = "APPROVED";

    // ---- inner value objects ----
    record CliAuthSession(
            String sessionId,
            String clientId,
            String deviceId,      // the device this login was started from — carried through to approval
            String status,        // PENDING | APPROVED
            String userId,        // null until approved
            String userEmail,     // null until approved
            String accessToken    // null until approved
    ) {
        public static CliAuthSession pending(String sessionId, String clientId, String deviceId) {
            return new CliAuthSession(sessionId, clientId, deviceId, STATUS_PENDING, null, null, null);
        }
    }

    /**
     * The standing deviceId→JWT link. Written at approval time and read by every
     * subsequent CLI call, which is why it — not the local credentials file — decides
     * whether the CLI counts as authenticated.
     */
    record CliDeviceAuth(
            String deviceId,
            String clientId,
            String sessionId,     // the login this token came from, for tracing
            String userId,
            String userEmail,
            String accessToken
    ) {}
}
