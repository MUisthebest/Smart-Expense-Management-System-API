package com.example.smartmoney.application.ports.event;
import java.util.Optional;

public interface RedisJobCachePort {
    void store (String JobId, String tokenId, Object payload);

    Optional<Object>find (String jobId);

    void delete (String jobId);
}
