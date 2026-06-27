package com.example.smartmoney.infrastructure.worker.NotificationJob;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Duration;
import java.util.Map;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;

@Component
@RequiredArgsConstructor
public class StreamReclaimJob {

    @Qualifier("coreRedisTemplate")
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String STREAM = "notification_stream";
    private static final String GROUP = "notification_group";
    private static final String DLQ = "notification_dlq";

    private static final int MAX_RETRY = 3;

    @Scheduled(fixedDelay = 60000)
    public void reclaim() {
        try {
            // 🔥 lấy danh sách pending message
            var pendingMessages = redisTemplate.opsForStream().pending(
                STREAM,
                GROUP,
                org.springframework.data.domain.Range.unbounded(),
                100
            );

            // 👉 lọc message idle > 30s
            var toClaimIds = pendingMessages.stream()
                .filter(p -> p.getElapsedTimeSinceLastDelivery().toSeconds() > 30)
                .map(p -> p.getId())
                .toList();

            if (toClaimIds.isEmpty()) return;

            // 🔥 CLAIM theo ID cụ thể
            var messages = redisTemplate.opsForStream().claim(
                STREAM,
                GROUP,                // 👈 group name
                "reclaimer",          // 👈 consumer name
                XClaimOptions.minIdle(Duration.ofSeconds(30))
                    .ids(toClaimIds.toArray(new RecordId[0]))
            );

            messages.forEach(msg -> {
                try {
                    Map<Object, Object> value = msg.getValue();

                    int retry = value.get("retry") == null
                        ? 0
                        : Integer.parseInt(value.get("retry").toString());

                    if (retry >= MAX_RETRY) {
                        // 💀 DLQ
                        redisTemplate.opsForStream().add(DLQ, value);

                        redisTemplate.opsForStream()
                            .acknowledge(STREAM, GROUP, msg.getId());

                        return;
                    }

                    // 👉 tăng retry
                    value.put("retry", retry + 1);

                    // 👉 xử lý lại
                    process(value);

                    // ✅ ACK
                    redisTemplate.opsForStream()
                        .acknowledge(STREAM, GROUP, msg.getId());

                } catch (Exception ex) {
                    ex.printStackTrace();
                    // ❗ không ACK → retry tiếp
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void process(Map<Object, Object> data) {
        System.out.println("Processing reclaimed: " + data);
    }
}