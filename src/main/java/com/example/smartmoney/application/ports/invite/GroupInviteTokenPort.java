package com.example.smartmoney.application.ports.invite;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for short-lived group-invitation tokens backed by Redis (7-day TTL).
 * Tokens are one-time use and deleted on first redemption.
 */
public interface GroupInviteTokenPort {

    void save(String token, UUID groupId);

    Optional<UUID> find(String token);

    void delete(String token);
}
