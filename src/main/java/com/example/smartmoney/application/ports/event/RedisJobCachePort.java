package com.example.smartmoney.application.ports.event;

import java.util.Optional;

public interface RedisJobCachePort {

    void addJobForUser(String tokenId, String jobId, String payload);

    void delete(String tokenId, String jobId);

    Optional<String> getDedup(String key);

    void setDedup(String key, String jobId);

}