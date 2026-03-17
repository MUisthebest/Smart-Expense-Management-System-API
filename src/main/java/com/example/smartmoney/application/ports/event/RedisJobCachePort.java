package com.example.smartmoney.application.ports.event;

import java.util.List;
import java.util.Optional;
import com.example.smartmoney.application.dtos.responses.JobResponse;

public interface RedisJobCachePort {

    void addJobForUser(String tokenId, String jobId);

    List<String> findJobsByUser(String tokenId);

    void storeResult(String jobId, JobResponse<?> response);

    Optional<JobResponse<?>> findResult(String jobId);

    void delete(String jobId);
}