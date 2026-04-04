package com.example.smartmoney.infrastructure.stream;

import io.lettuce.core.RedisClient;
import io.lettuce.core.XTrimArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisStreamCommands;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StreamTTLScheduler {

    private final StatefulRedisConnection<String, String> connection;
    private final RedisStreamCommands<String, String> streamCommands;
    private final int streamGroupCount;
    private final long ttlSeconds;

    public StreamTTLScheduler(RedisConnectionFactory connectionFactory,
                              @Value("${REDIS_STREAMGROUP_COUNT}") int streamGroupCount,
                              @Value("${REDIS_STREAM_TTL_SECONDS}") long ttlSeconds) {
        RedisClient redisClient = (RedisClient) ((LettuceConnectionFactory) connectionFactory).getNativeClient();
        this.connection = redisClient.connect(StringCodec.UTF8);
        this.streamCommands = connection.sync();
        connection.sync().ping(); //Warm-up connection
        this.streamGroupCount = streamGroupCount;
        this.ttlSeconds = ttlSeconds;
    }

    @Scheduled(fixedDelayString = "${REDIS_STREAM_TTL_CHECK_INTERVAL_MS:30000}")
    public void evictExpiredMessages() {
        long minTimestamp = System.currentTimeMillis() - (ttlSeconds * 1000);
        String minId = minTimestamp + "-0";

        RedisStreamCommands<String, String> streamCommands = connection.sync();
        for (int i = 0; i < streamGroupCount; i++) {
            String streamKey = "stream:jobs:" + i;

            XTrimArgs trimArgs = XTrimArgs.Builder
                    .minId(minId)
                    .approximateTrimming();

            streamCommands.xtrim(streamKey, trimArgs);
            System.out.println("Trimmed " + streamKey + " minId: " + minId);
        }
    }
}