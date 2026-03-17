package com.example.smartmoney.infrastructure.config.redis;

import com.example.smartmoney.application.dtos.responses.JobResponse;
import com.example.smartmoney.application.ports.event.RedisJobCachePort;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class RedisJobCacheAdapter implements RedisJobCachePort {

    private final RedisTemplate<String, Object> template;

    private static final long TTL = 90;

    public RedisJobCacheAdapter(RedisTemplate<String, Object> template) {
        this.template = template;
    }

    private String jobKey(String jobId) {
        return "job:" + jobId;
    }

    private String userJobsKey(String tokenId) {
        return "user:" + tokenId + ":jobs";
    }

    @Override
    public void addJobForUser(String tokenId, String jobId) {

        template.opsForList().rightPush(
                userJobsKey(tokenId),
                jobId
        );

        template.expire(userJobsKey(tokenId), TTL, TimeUnit.SECONDS);
    }

    @Override
    public List<String> findJobsByUser(String tokenId) {

        List<Object> jobs = template.opsForList().range(
                userJobsKey(tokenId),
                0,
                -1
        );

        return jobs.stream()
                .map(Object::toString)
                .toList();
    }

    @Override
    public void storeResult(String jobId, JobResponse<?> response) {

        template.opsForValue().set(
                jobKey(jobId),
                response,
                TTL,
                TimeUnit.SECONDS
        );
    }

    @Override
    public Optional<JobResponse<?>> findResult(String jobId) {

        Object result = template.opsForValue().get(jobKey(jobId));

        if (result instanceof JobResponse<?> jobResponse) {
            return Optional.of(jobResponse);
        }

        return Optional.empty();
    }

    @Override
    public void delete(String jobId) {

        template.delete(jobKey(jobId));
    }
}