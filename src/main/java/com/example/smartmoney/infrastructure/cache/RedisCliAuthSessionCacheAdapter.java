package com.example.smartmoney.infrastructure.cache;

import com.example.smartmoney.application.ports.cli.CliAuthSessionCachePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RedisCliAuthSessionCacheAdapter implements CliAuthSessionCachePort {

    private static final String CLI_SESSION_PREFIX = "cli:session:";
    private static final String CLI_DEVICE_PREFIX  = "cli:device:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCliAuthSessionCacheAdapter(
            @Qualifier("coreStringRedisTemplate") RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper  = objectMapper;
    }

    // ---- login session ----

    @Override
    public void save(CliAuthSession session, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(
                    CLI_SESSION_PREFIX + session.sessionId(),
                    objectMapper.writeValueAsString(session),
                    ttl);
        } catch (Exception e) {
            log.error("Failed to save CLI auth session", e);
            throw new RuntimeException("Failed to save CLI auth session", e);
        }
    }

    @Override
    public Optional<CliAuthSession> find(String sessionId) {
        try {
            String json = redisTemplate.opsForValue().get(CLI_SESSION_PREFIX + sessionId);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, CliAuthSession.class));
        } catch (Exception e) {
            log.error("Failed to read CLI auth session: {}", sessionId, e);
            return Optional.empty();
        }
    }

    @Override
    public long ttlSeconds(String sessionId) {
        Long ttl = redisTemplate.getExpire(CLI_SESSION_PREFIX + sessionId, TimeUnit.SECONDS);
        // -2 = no such key, -1 = key without expiry (shouldn't happen — every write sets a TTL)
        return (ttl == null || ttl < 0) ? 0L : ttl;
    }

    @Override
    public void delete(String sessionId) {
        redisTemplate.delete(CLI_SESSION_PREFIX + sessionId);
    }

    // ---- device binding ----

    @Override
    public void saveDevice(CliDeviceAuth device, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(
                    CLI_DEVICE_PREFIX + device.deviceId(),
                    objectMapper.writeValueAsString(device),
                    ttl);
        } catch (Exception e) {
            log.error("Failed to save CLI device binding", e);
            throw new RuntimeException("Failed to save CLI device binding", e);
        }
    }

    @Override
    public Optional<CliDeviceAuth> findDevice(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return Optional.empty();
        try {
            String json = redisTemplate.opsForValue().get(CLI_DEVICE_PREFIX + deviceId);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, CliDeviceAuth.class));
        } catch (Exception e) {
            log.error("Failed to read CLI device binding: {}", deviceId, e);
            return Optional.empty();
        }
    }

    @Override
    public long deviceTtlSeconds(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return 0L;
        Long ttl = redisTemplate.getExpire(CLI_DEVICE_PREFIX + deviceId, TimeUnit.SECONDS);
        return (ttl == null || ttl < 0) ? 0L : ttl;
    }

    @Override
    public void deleteDevice(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return;
        redisTemplate.delete(CLI_DEVICE_PREFIX + deviceId);
    }
}
