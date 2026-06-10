package com.example.smartmoney.infrastructure.cache;

import com.example.smartmoney.application.ports.invite.InviteTokenPort;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-backed adapter for project invite tokens.
 * Uses the core Redis instance (coreStringRedisTemplate).
 * Key schema: {@code invite:<token>}  →  {@code "<projectId>:<isAdmin>"}
 * TTL: 7 days.
 */
@Component
public class RedisInviteTokenAdapter implements InviteTokenPort {

    private static final String PREFIX = "invite:";
    private static final Duration TTL = Duration.ofDays(7);

    private final RedisTemplate<String, String> redisTemplate;

    public RedisInviteTokenAdapter(@Qualifier("coreStringRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String token, UUID projectId, boolean isAdmin) {
        String key = PREFIX + token;
        String value = projectId.toString() + ":" + isAdmin;
        redisTemplate.opsForValue().set(key, value, TTL);
    }

    @Override
    public Optional<InviteTokenPayload> find(String token) {
        String key = PREFIX + token;
        String raw = redisTemplate.opsForValue().get(key);
        if (raw == null) return Optional.empty();

        String[] parts = raw.split(":", 2);
        if (parts.length != 2) return Optional.empty();

        UUID projectId = UUID.fromString(parts[0]);
        boolean isAdmin = Boolean.parseBoolean(parts[1]);
        return Optional.of(new InviteTokenPayload(projectId, isAdmin));
    }

    @Override
    public void delete(String token) {
        redisTemplate.delete(PREFIX + token);
    }
}
