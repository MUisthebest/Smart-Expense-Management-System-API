package com.example.smartmoney.application.ports.invite;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for storing and retrieving short-lived project invitation tokens.
 * Tokens are backed by Redis with a 7-day TTL and deleted on first use
 * to prevent replay attacks.
 */
public interface InviteTokenPort {

    /** Persist a token mapping to the project and admin flag (TTL = 7 days). */
    void save(String token, UUID projectId, boolean isAdmin);

    /** Look up the invite payload for a token. Empty if expired or already used. */
    Optional<InviteTokenPayload> find(String token);

    /** Delete the token to prevent reuse. */
    void delete(String token);

    /** Simple value holder returned by {@link #find}. */
    record InviteTokenPayload(UUID projectId, boolean isAdmin) {}
}
