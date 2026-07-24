package com.example.smartmoney.infrastructure.worker;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Qualifier;

import com.example.smartmoney.application.services.NotificationService;
import com.example.smartmoney.domain.models.notification.Notification;

import java.util.concurrent.ExecutorService;
import java.time.Duration;
import java.util.*;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class NotificationWorker {

    @Qualifier("coreRedisTemplate")
    private final RedisTemplate<String, Object> redisTemplate;
    private final NotificationService notificationService;
    private final ExecutorService executor;

    private final ObjectMapper objectMapper;

    private static final String STREAM = "notification_stream";
    private static final String GROUP = "notification_group";

    private static final int CONSUMER_COUNT = 1;
    private static final int BATCH_SIZE = 10;

    private volatile boolean running = true;
    private volatile boolean groupReady = false;

    private void ensureConsumerGroup() {
        try {
            // Tạo stream nếu chưa có
            redisTemplate.opsForStream().add(STREAM, Map.of("init", "true"));

            var groups = redisTemplate.opsForStream().groups(STREAM);

            boolean exists = groups.stream()
                    .anyMatch(g -> GROUP.equals(g.groupName()));

            if (!exists) {
                redisTemplate.opsForStream().createGroup(STREAM, GROUP);
                System.out.println("Created consumer group: " + GROUP);
            }

        } catch (Exception e) {
            throw new RuntimeException("Cannot ensure notification consumer group", e);
        }
    }

    @PostConstruct
    public void init() {
        ensureConsumerGroup();
        groupReady = true;

        for (int i = 0; i < CONSUMER_COUNT; i++) {
            String consumerName = "consumer-" + i;
            executor.submit(() -> runWorker(consumerName));
        }
    }

    private void runWorker(String consumerName) {
        while (running) {
            try {
                @SuppressWarnings("unchecked")
                List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                        Consumer.from(GROUP, consumerName),
                        StreamReadOptions.empty()
                                .count(BATCH_SIZE)
                                // Must stay below CoreRedisConfig's 10s client-side commandTimeout,
                                // or every idle poll throws RedisCommandTimeoutException.
                                .block(Duration.ofSeconds(8)),
                        StreamOffset.create(STREAM, ReadOffset.lastConsumed()));

                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                for (MapRecord<String, Object, Object> msg : messages) {
                    try {
                        Notification notification = objectMapper.convertValue(msg.getValue(), Notification.class);
                        notificationService.process(notification, 40);

                        // ✅ ACK sau khi xử lý xong
                        redisTemplate.opsForStream()
                                .acknowledge(STREAM, GROUP, msg.getId());

                        redisTemplate.opsForStream().delete(STREAM, msg.getId());
                    } catch (Exception e) {
                        e.printStackTrace();
                        // ❌ không ack → sẽ retry
                    }
                }

            } catch (Exception e) {

                String message = e.getMessage();

                if (message != null && message.contains("NOGROUP")) {
                    System.out.println("Notification consumer group missing, recreating...");

                    groupReady = false;
                    ensureConsumerGroup();
                    groupReady = true;

                    continue;
                }

                e.printStackTrace();

                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        executor.shutdown();
    }
}