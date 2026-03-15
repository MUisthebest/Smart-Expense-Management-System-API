package com.example.smartmoney.infrastructure.config.redis;

import com.example.smartmoney.application.dtos.responses.JobResponse;
import com.example.smartmoney.application.dtos.requests.job.JobEvent;
import com.example.smartmoney.application.ports.event.RedisJobCachePort;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class RedisJobCacheAdapter implements RedisJobCachePort {

    private final RedisTemplate<String, Object> template_;

    private static final long TTL = 90;

    public RedisJobCacheAdapter(RedisTemplate<String, Object> template) {
        this.template_ = template;
    }

    @Override
    public void store(String jobId, JobEvent jobEvent) {

        template_.opsForValue().set(
                jobId,
                jobEvent,
                TTL,
                TimeUnit.SECONDS
        );
    }

    @Override
    public Optional<JobResponse<?>> find(String jobId) {

        Object result = template_.opsForValue().get(jobId);
        if (result instanceof JobResponse<?> jobResponse) {
            return Optional.of(jobResponse);
        }

        return Optional.empty();
    }

    @Override
    public void delete(String jobId) {
        template_.delete(jobId);
    }
}