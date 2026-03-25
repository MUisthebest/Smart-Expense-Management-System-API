package com.example.smartmoney.infrastructure.cache;
import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.example.smartmoney.application.ports.notification.NotificationQueuePort;
import com.example.smartmoney.domain.models.notification.Notification;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;


@Component
@RequiredArgsConstructor
public class RedisNotificationQueueAdapter implements NotificationQueuePort {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String STREAM = "notification_stream";
    private final ObjectMapper objectMapper;

    @Override
    public void enqueue(Notification notification) {
        try {
            Map<String, Object> map = objectMapper.convertValue(notification, Map.class);

        redisTemplate.opsForStream().add(STREAM, map);

        // 🔥 trim thủ công
        redisTemplate.opsForStream().trim(STREAM, 10000);


        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Serialize notification failed", e);
        }
    }
}