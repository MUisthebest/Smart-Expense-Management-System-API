package com.example.smartmoney.application.ports.event;

import java.util.Optional;
import java.util.Map;

public interface RedisJobCachePort {

    Map<String, Object> getJobResult(String jobId);

    void saveJobResult(String jobId, Map<String, Object> data);


    void addJobForUser(String tokenId, String jobId, String payload);

    void delete(String tokenId, String jobId);

    Optional<String> getDedup(String key);

    void setDedup(String key, String jobId);

}