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
 * Key stored:
 *   cli:session:{sessionId} → CliAuthSession JSON
 *
 * TTL is the whole point of this port. While PENDING the key lives only long
 * enough for the user to finish logging in; once APPROVED the key is rewritten
 * with a TTL equal to the access token's own lifetime (1 hour), so the JWT can
 * never outlive its Redis entry — and Claude Desktop, which treats this key as
 * the source of truth, asks the user to log in again the moment it expires.
 */
public interface CliAuthSessionCachePort {

    /** Persist (or overwrite) a session with an explicit TTL. */
    void save(CliAuthSession session, Duration ttl);

    /** Look up a session by its id. Empty once the TTL has elapsed. */
    Optional<CliAuthSession> find(String sessionId);

    /** Remaining TTL in seconds, or 0 when the key is gone / has no expiry. */
    long ttlSeconds(String sessionId);

    /** Drop the session — used by logout and by an explicit revoke. */
    void delete(String sessionId);

    String STATUS_PENDING  = "PENDING";
    String STATUS_APPROVED = "APPROVED";

    // ---- inner value object ----
    record CliAuthSession(
            String sessionId,
            String clientId,
            String status,        // PENDING | APPROVED
            String userId,        // null until approved
            String userEmail,     // null until approved
            String accessToken    // null until approved
    ) {
        public static CliAuthSession pending(String sessionId, String clientId) {
            return new CliAuthSession(sessionId, clientId, STATUS_PENDING, null, null, null);
        }
    }
}
