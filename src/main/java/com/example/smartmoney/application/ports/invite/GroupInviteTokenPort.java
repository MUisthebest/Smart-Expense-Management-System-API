package com.example.smartmoney.application.ports.invite;

import java.util.Optional;
import java.util.UUID;

/**
 * Port for short-lived group-invitation tokens backed by Redis (7-day TTL).
 * Tokens are one-time use and deleted on first redemption.
 *
 * Each token encodes both the target group and the specific invited user so
 * that no other authenticated user can redeem a token that was not meant for them.
 */
public interface GroupInviteTokenPort {

    record InvitePayload(UUID groupId, UUID invitedUserId) {}

    void save(String token, UUID groupId, UUID invitedUserId);

    Optional<InvitePayload> find(String token);

    void delete(String token);
}
