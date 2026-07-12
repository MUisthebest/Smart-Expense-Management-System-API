package com.example.smartmoney.infrastructure.cache;

import com.example.smartmoney.application.dtos.chat.PendingChatAction;
import com.example.smartmoney.application.ports.chat.ChatPendingActionPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
public class RedisChatPendingActionAdapter implements ChatPendingActionPort {

    private static final String KEY_PREFIX = "chat:pending:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisChatPendingActionAdapter(
            @Qualifier("aiStringRedisTemplate") StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(UUID userId, String actionId, PendingChatAction action) {
        try {
            String json = objectMapper.writeValueAsString(action);
            redisTemplate.opsForValue().set(key(userId, actionId), json, TTL);
        } catch (Exception e) {
            log.warn("Failed to persist pending chat action {} for user {}: {}", actionId, userId, e.getMessage());
        }
    }

    @Override
    public Optional<PendingChatAction> load(UUID userId, String actionId) {
        try {
            String raw = redisTemplate.opsForValue().get(key(userId, actionId));
            if (raw == null || raw.isBlank()) return Optional.empty();
            return Optional.of(objectMapper.readValue(raw, PendingChatAction.class));
        } catch (Exception e) {
            log.warn("Failed to load pending chat action {} for user {}: {}", actionId, userId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void delete(UUID userId, String actionId) {
        try {
            redisTemplate.delete(key(userId, actionId));
        } catch (Exception e) {
            log.warn("Failed to delete pending chat action {} for user {}: {}", actionId, userId, e.getMessage());
        }
    }

    @Override
    public void saveLatestId(UUID userId, String actionId) {
        try {
            redisTemplate.opsForValue().set(latestKey(userId), actionId, TTL);
        } catch (Exception e) {
            log.warn("Failed to persist latest pending action pointer for user {}: {}", userId, e.getMessage());
        }
    }

    @Override
    public Optional<String> loadLatestId(UUID userId) {
        try {
            String raw = redisTemplate.opsForValue().get(latestKey(userId));
            return (raw == null || raw.isBlank()) ? Optional.empty() : Optional.of(raw);
        } catch (Exception e) {
            log.warn("Failed to load latest pending action pointer for user {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void clearLatestId(UUID userId) {
        try {
            redisTemplate.delete(latestKey(userId));
        } catch (Exception e) {
            log.warn("Failed to clear latest pending action pointer for user {}: {}", userId, e.getMessage());
        }
    }

    private String key(UUID userId, String actionId) {
        return KEY_PREFIX + userId + ":" + actionId;
    }

    private String latestKey(UUID userId) {
        return KEY_PREFIX + "latest:" + userId;
    }
}
