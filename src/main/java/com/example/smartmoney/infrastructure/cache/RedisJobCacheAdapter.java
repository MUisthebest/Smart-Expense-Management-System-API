package com.example.smartmoney.infrastructure.cache;

import com.example.smartmoney.application.dtos.requests.JobEvent;
import com.example.smartmoney.application.ports.event.RedisJobCachePort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class RedisJobCacheAdapter implements RedisJobCachePort {

    private final RedisTemplate<String, Object> template;

    private static final long TTL = 300;

    public RedisJobCacheAdapter(RedisTemplate<String, Object> template) {
        this.template = template;
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