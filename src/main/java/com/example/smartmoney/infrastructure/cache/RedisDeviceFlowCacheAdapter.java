package com.example.smartmoney.infrastructure.cache;

import com.example.smartmoney.application.ports.device.DeviceFlowCachePort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@Slf4j
public class RedisDeviceFlowCacheAdapter implements DeviceFlowCachePort {

    private static final String DEVICE_PREFIX   = "device:";
    private static final String USERCODE_PREFIX = "usercode:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisDeviceFlowCacheAdapter(
            @Qualifier("coreStringRedisTemplate") RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper  = objectMapper;
    }

    @Override
    public void saveSession(DeviceSession session) {
        try {
            String json = objectMapper.writeValueAsString(session);
            Duration ttl = Duration.ofSeconds(session.expiresIn());

            redisTemplate.opsForValue()
                    .set(DEVICE_PREFIX + session.deviceCode(), json, ttl);

            // Reverse lookup: userCode → deviceCode
            redisTemplate.opsForValue()
                    .set(USERCODE_PREFIX + session.userCode(), session.deviceCode(), ttl);

        } catch (Exception e) {
            log.error("Failed to save device session", e);
            throw new RuntimeException("Failed to save device session", e);
        }
    }

    @Override
    public Optional<DeviceSession> findByDeviceCode(String deviceCode) {
        try {
            String json = redisTemplate.opsForValue().get(DEVICE_PREFIX + deviceCode);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, DeviceSession.class));
        } catch (Exception e) {
            log.error("Failed to read device session for code: {}", deviceCode, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> findDeviceCodeByUserCode(String userCode) {
        String deviceCode = redisTemplate.opsForValue().get(USERCODE_PREFIX + userCode);
        return Optional.ofNullable(deviceCode);
    }

    @Override
    public void approve(String deviceCode, String userId) {
        findByDeviceCode(deviceCode).ifPresent(session -> {
            DeviceSession approved = new DeviceSession(
                    session.deviceCode(),
                    session.userCode(),
                    session.clientId(),
                    "APPROVED",
                    userId,
                    session.expiresIn()
            );
            try {
                // Preserve remaining TTL
                Long ttlMs = redisTemplate.getExpire(DEVICE_PREFIX + deviceCode,
                        java.util.concurrent.TimeUnit.MILLISECONDS);
                Duration ttl = (ttlMs != null && ttlMs > 0)
                        ? Duration.ofMillis(ttlMs)
                        : Duration.ofMinutes(5);

                redisTemplate.opsForValue()
                        .set(DEVICE_PREFIX + deviceCode,
                                objectMapper.writeValueAsString(approved),
                                ttl);
            } catch (Exception e) {
                log.error("Failed to approve device session", e);
                throw new RuntimeException("Failed to approve device session", e);
            }
        });
    }

    @Override
    public void delete(String deviceCode, String userCode) {
        redisTemplate.delete(DEVICE_PREFIX   + deviceCode);
        redisTemplate.delete(USERCODE_PREFIX + userCode);
    }
}