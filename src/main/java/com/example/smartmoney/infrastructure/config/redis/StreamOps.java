package com.example.smartmoney.infrastructure.config.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class StreamOps {

    @Value("${REDIS_STREAMGROUP_COUNT}")
    private int streamGroupCount;

    private static final String STREAM_PREFIX = "stream:jobs:";
    private static final String GROUP_NAME = "workers";

    @Bean(name = "initStreamOpsRunner")
    public CommandLineRunner initStreamOps(RedisTemplate<String, Object> redisTemplate) {
        return args -> {
            for (int i = 0; i < streamGroupCount; i++) {
                String streamKey = STREAM_PREFIX + i;
                try {
                    redisTemplate.opsForStream().createGroup(streamKey, GROUP_NAME);
                    System.out.println("Created stream group: " + streamKey);
                } catch (Exception e) {
                    System.out.println("Skipping (already exists): " + streamKey);
                }
            }
        };
    }
}