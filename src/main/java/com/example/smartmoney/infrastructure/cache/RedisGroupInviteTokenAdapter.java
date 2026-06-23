package com.example.smartmoney.infrastructure.cache;

import com.example.smartmoney.application.ports.invite.GroupInviteTokenPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class RedisGroupInviteTokenAdapter implements GroupInviteTokenPort {

    private static final String KEY_PREFIX = "group-invite:";
    private static final Duration TTL = Duration.ofDays(7);

    private final RedisTemplate<String, String> redisTemplate;

    public RedisGroupInviteTokenAdapter(
            @Qualifier("coreStringRedisTemplate") RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void save(String token, UUID groupId, UUID invitedUserId) {
        String value = groupId.toString() + ":" + invitedUserId.toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + token, value, TTL);
    }

    @Override
    public Optional<InvitePayload> find(String token) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + token);
        if (value == null) return Optional.empty();
        String[] parts = value.split(":", 2);
        if (parts.length != 2) return Optional.empty();
        return Optional.of(new InvitePayload(UUID.fromString(parts[0]), UUID.fromString(parts[1])));
    }

    @Override
    public void delete(String token) {
        redisTemplate.delete(KEY_PREFIX + token);
    }
}
