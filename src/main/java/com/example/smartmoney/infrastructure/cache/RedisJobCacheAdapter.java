package com.example.smartmoney.infrastructure.cache;

import com.example.smartmoney.application.dtos.requests.JobEvent;
import com.example.smartmoney.application.ports.event.RedisJobCachePort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.expression.spel.ast.QualifiedIdentifier;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class RedisJobCacheAdapter implements RedisJobCachePort {

    @Qualifier("aiRedisTemplate")
    private final RedisTemplate<String, Object> template;
    private static final long TTL = 300;

    private final ObjectMapper objectMapper;

    public RedisJobCacheAdapter(@Qualifier("aiRedisTemplate") RedisTemplate<String, Object> template, ObjectMapper objectMapper) {
        this.template = template;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> getJobResult(String jobId) {
        String key = "job:" + jobId;

        Object value = template.opsForValue().get(key);

        if (value == null) return null;

        return (Map<String, Object>) value;
    }

    @Override
    public void saveJobResult(String jobId, Map<String, Object> data) {
        String key = "job:" + jobId;

        template.opsForValue().set(
            key,
            data, // ❌ bỏ objectMapper.writeValueAsString
            Duration.ofMinutes(10)
        );
    }

    @Override
    public void addJobForUser(String tokenId, String jobId, String payload) {

        JobEvent job = new JobEvent(jobId, tokenId, payload, "PENDING");

        template.opsForValue().set(jobId, job, TTL, TimeUnit.SECONDS);

        template.opsForSet().add(tokenId, jobId);

        template.expire(tokenId, TTL, TimeUnit.SECONDS);
    }

    @Override
    public void delete(String tokenId, String jobId) {

        template.delete((jobId));

        template.opsForSet().remove(tokenId, jobId);
    }



    @Override
    public Optional<String> getDedup(String key) {

        Object value = template.opsForValue().get(key);

        return value == null ? Optional.empty() : Optional.of(value.toString());
    }

    @Override
    public void setDedup(String key, String jobId) {

        template.opsForValue().set(key, jobId, TTL, TimeUnit.SECONDS);
    }
}