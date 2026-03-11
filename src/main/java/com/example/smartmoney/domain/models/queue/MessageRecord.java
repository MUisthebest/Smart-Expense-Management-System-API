package com.example.smartmoney.domain.models.queue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import com.example.smartmoney.domain.models.queue.enums.MessageStatus;

public class MessageRecord {

    private final String jobId;
    private final Object payload;
    private final String payloadHash;
    private final String idempotencyKey;

    private MessageStatus status;

    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private int retryCount;
    private String result;
    private String errorMessage;

    public MessageRecord(Object payload, String payloadHash, String idempotencyKey) {

        this.jobId = UUID.randomUUID().toString();
        this.payload = payload;
        this.payloadHash = payloadHash;
        this.idempotencyKey = idempotencyKey;

        this.status = MessageStatus.PENDING;

        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;

        this.retryCount = 0;
    }

    public void startProcessing() {
        updateStatus(MessageStatus.PROCESSING);
    }

    public void completeProcessing() {
        updateStatus(MessageStatus.COMPLETED);
    }

    public void failProcessing(String error) {
        this.errorMessage = error;
        this.retryCount++;
        updateStatus(MessageStatus.FAILED);
    }

    private void updateStatus(MessageStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isTimeout(Duration timeout) {
        return Duration.between(updatedAt, LocalDateTime.now())
                .compareTo(timeout) > 0;
    }

    public String getJobId() {
        return jobId;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public Object getPayload() {
        return payload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

}