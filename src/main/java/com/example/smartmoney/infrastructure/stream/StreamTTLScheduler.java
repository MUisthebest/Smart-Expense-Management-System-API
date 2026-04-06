package com.example.smartmoney.infrastructure.stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Qualifier;

@Component
public class StreamTTLScheduler {

    private final StringRedisTemplate redisTemplate;
    private final int streamGroupCount;
    private final long ttlSeconds;

    public StreamTTLScheduler(
            @Qualifier("aiStringRedisTemplate") StringRedisTemplate redisTemplate,
            @Value("${REDIS_STREAMGROUP_COUNT}") int streamGroupCount,
            @Value("${REDIS_STREAM_TTL_SECONDS}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.streamGroupCount = streamGroupCount;
        this.ttlSeconds = ttlSeconds;
    }

    @Scheduled(fixedDelayString = "${REDIS_STREAM_TTL_CHECK_INTERVAL_MS:30000}")
    public void evictExpiredMessages() {

        long minTimestamp = System.currentTimeMillis() - (ttlSeconds * 1000);
        String minId = minTimestamp + "-0";

        for (int i = 0; i < streamGroupCount; i++) {
            String streamKey = "stream:jobs:" + i;

        redisTemplate.execute((RedisCallback<Void>) connection -> {
            byte[] key = streamKey.getBytes();

            byte[] command = ("XTRIM " + streamKey + " MINID ~ " + minId).getBytes();

            connection.execute("XTRIM", 
                streamKey.getBytes(),
                "MINID".getBytes(),
                "~".getBytes(),
                minId.getBytes()
            );

            return null;
        });

            System.out.println("Trimmed " + streamKey + " minId: " + minId);
        }
    }
}