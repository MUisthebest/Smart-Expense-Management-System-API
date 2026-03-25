package com.example.smartmoney.infrastructure.config.redis;

import com.example.smartmoney.application.dtos.responses.JobResponse;
import com.example.smartmoney.application.ports.event.RedisJobCachePort;
import com.example.smartmoney.domain.models.message.enums.MessageStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class RedisJobCacheAdapter implements RedisJobCachePort {

    private final RedisTemplate<String, Object> template;

    private static final long TTL = 300;

    public RedisJobCacheAdapter(RedisTemplate<String, Object> template) {
        this.template = template;
    }

    @Override
    public void addJobForUser(String tokenId, String jobId, String payload) {

        JobResponse job = new JobResponse(
                jobId,
                tokenId,
                payload,
                MessageStatus.PENDING
        );

        template.opsForValue().set(jobId, job, TTL, TimeUnit.SECONDS);

        template.opsForSet().add(tokenId, jobId);

        template.expire(tokenId, TTL, TimeUnit.SECONDS);
    }

    public List<JobResponse> findJobsByUser(String tokenId) {

        Set<Object> jobIds = template.opsForSet().members(tokenId);

        if (jobIds == null || jobIds.isEmpty()) {
            return Collections.emptyList();
        }

        return jobIds.stream()
                .map(id -> template.opsForValue().get((id.toString())))
                .filter(Objects::nonNull)
                .map(obj -> (JobResponse) obj)
                .collect(Collectors.toList());
    }

    public Optional<JobResponse> findResult(String jobId) {

        Object result = template.opsForValue().get(jobId);

        if (result == null) {
            return Optional.empty();
        }

        return Optional.of((JobResponse) result);
    }

    @Override
    public void delete(String tokenId, String jobId) {

        template.delete((jobId));

        template.opsForSet().remove(tokenId, jobId);
    }

    public void storeResult(String jobId, JobResponse response) {

        template.opsForValue().set((jobId), response, TTL, TimeUnit.SECONDS);
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