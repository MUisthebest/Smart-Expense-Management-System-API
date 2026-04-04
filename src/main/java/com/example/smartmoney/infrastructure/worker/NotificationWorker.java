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

    private static final int CONSUMER_COUNT = 10;
    private static final int BATCH_SIZE = 10;

    private volatile boolean running = true;

    // 👉 tạo group (chỉ 1 lần)
    @PostConstruct
    public void initGroup() {
        try {
            redisTemplate.opsForStream().createGroup(
                STREAM,
                ReadOffset.latest(),
                GROUP
            );
        } catch (Exception ignored) {
            // group đã tồn tại
        }
    }

    @PostConstruct
    public void init() {
        for (int i = 0; i < CONSUMER_COUNT; i++) {
            String consumerName = "consumer-" + i;
            executor.submit(() -> runWorker(consumerName));
        }
    }

    private void runWorker(String consumerName) {
        while (running) {
            try {
                @SuppressWarnings("unchecked")
                List<MapRecord<String, Object, Object>> messages =
                    redisTemplate.opsForStream().read(
                        Consumer.from(GROUP, consumerName),
                        StreamReadOptions.empty()
                            .count(BATCH_SIZE)
                            .block(Duration.ofSeconds(2)),
                        StreamOffset.create(STREAM, ReadOffset.lastConsumed())
                    );

                if (messages == null || messages.isEmpty()) {
                    continue;
                }

                for (MapRecord<String, Object, Object> msg : messages) {
                    try {
                        Notification notification =
                            objectMapper.convertValue(msg.getValue(), Notification.class);
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
                e.printStackTrace();

                try {
                    Thread.sleep(500); // 🔥 tránh spam Redis
                } catch (InterruptedException ignored) {}
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        executor.shutdown();
    }
}