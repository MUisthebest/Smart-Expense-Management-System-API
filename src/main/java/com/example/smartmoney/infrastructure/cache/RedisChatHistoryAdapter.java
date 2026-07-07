package com.example.smartmoney.infrastructure.cache;

import com.example.smartmoney.application.dtos.requests.chat.ChatMessageDto;
import com.example.smartmoney.application.ports.chat.ChatHistoryPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class RedisChatHistoryAdapter implements ChatHistoryPort {

    private static final String KEY_PREFIX = "chat:history:";
    private static final Duration TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisChatHistoryAdapter(
            @Qualifier("aiStringRedisTemplate") StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ChatMessageDto> load(UUID userId) {
        String raw;
        try {
            raw = redisTemplate.opsForValue().get(key(userId));
        } catch (Exception e) {
            log.warn("Failed to load chat history for user {}, continuing without history: {}",
                    userId, e.getMessage());
            return Collections.emptyList();
        }
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(raw, new TypeReference<List<ChatMessageDto>>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize chat history for user {}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void save(UUID userId, List<ChatMessageDto> history) {
        try {
            String json = objectMapper.writeValueAsString(history);
            redisTemplate.opsForValue().set(key(userId), json, TTL);
        } catch (Exception e) {
            log.warn("Failed to persist chat history for user {}: {}", userId, e.getMessage());
        }
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }
}
