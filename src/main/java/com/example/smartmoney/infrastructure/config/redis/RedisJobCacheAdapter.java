package com.example.smartmoney.infrastructure.config.redis;

import com.example.smartmoney.application.dtos.responses.JobResponse;
import com.example.smartmoney.application.dtos.requests.job.JobEvent;
import com.example.smartmoney.application.ports.event.RedisJobCachePort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class RedisJobCacheAdapter implements RedisJobCachePort {

    private final RedisTemplate<String, Object> template;

    private static final long TTL = 90;

    public RedisJobCacheAdapter(RedisTemplate<String, Object> template) {
        this.template = template;
    }

    // ========================
    // KEY GENERATOR
    // ========================
    private String jobKey(String jobId) {
        return "job:" + jobId;
    }

    private String userJobsKey(String tokenId) {
        return "user_jobs:" + tokenId;
    }

    // ========================
    // STORE JOB
    // ========================
    @Override
    public void store(String jobId, JobResponse<?> jobResponse) {
        template.opsForValue().set(
                jobKey(jobId),
                jobResponse,
                TTL,
                TimeUnit.SECONDS
        );
    }

    // ========================
    // GET JOB
    // ========================
    @Override
    public Optional<JobResponse<?>> find(String jobId) {

        Object result = template.opsForValue().get(jobKey(jobId));

        if (result instanceof JobResponse<?> jobResponse) {
            return Optional.of(jobResponse);
        }

        return Optional.empty();
    }

    // ========================
    // DELETE JOB
    // ========================
    @Override
    public void delete(String jobId) {
        template.delete(jobKey(jobId));
    }

    // ========================
    // ADD JOB TO USER
    // ========================
    @Override
    public void addJobForUser(String tokenId, String jobId) {

        template.opsForList().rightPush(
                userJobsKey(tokenId),
                jobId
        );

        template.expire(userJobsKey(tokenId), TTL, TimeUnit.SECONDS);
    }

    // ========================
    // GET JOBS BY USER
    // ========================
    @Override
    public List<String> findJobsByUser(String tokenId) {

        List<Object> result = template.opsForList().range(
                userJobsKey(tokenId),
                0,
                -1
        );

        if (result == null) return Collections.emptyList();

        return result.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
}