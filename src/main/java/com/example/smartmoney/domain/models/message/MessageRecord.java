package com.example.smartmoney.domain.models.message;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import com.example.smartmoney.domain.models.message.enums.MessageStatus;

public class MessageRecord<T> {

    private final String jobId;
    private final T payload;
    private final String tokenId;

    private MessageStatus status;

    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private int retryCount;
    private String result;
    private String errorMessage;

    private static final int MAX_RETRY = 3;

    public MessageRecord(T payload, String tokenId) {
        this.jobId = UUID.randomUUID().toString();
        this.payload = payload;
        this.tokenId = tokenId;

        this.status = MessageStatus.PENDING;

        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;

        this.retryCount = 0;
    }

    public void startProcessing() {
        updateStatus(MessageStatus.PROCESSING);
    }

    public void completeProcessing(String result) {
        this.result = result;
        updateStatus(MessageStatus.COMPLETED);
    }

    public void failProcessing(String error) {
        this.errorMessage = error;
        this.retryCount++;
        updateStatus(MessageStatus.FAILED);
    }

    public boolean canRetry() {
        return retryCount < MAX_RETRY;
    }

    private void updateStatus(MessageStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isTimeout(Duration timeout) {
        return Duration
                .between(updatedAt, LocalDateTime.now())
                .compareTo(timeout) > 0;
    }

    // -------- GETTERS --------

    public String getJobId() {
        return jobId;
    }

    public T getPayload() {
        return payload;
    }

    public String getTokenId() {
        return tokenId;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public String getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}