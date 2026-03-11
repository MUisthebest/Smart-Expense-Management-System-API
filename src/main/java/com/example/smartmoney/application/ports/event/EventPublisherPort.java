package com.example.smartmoney.application.ports.event;

public interface EventPublisherPort {
    void publishJobCreated(String jobId, String tokenId, Object payload);
    void publishJobFailed(String jobId, String tokenId, Object payload);
    void publishJobSuccess (String jobId, String tokenId, Object payload);
}
