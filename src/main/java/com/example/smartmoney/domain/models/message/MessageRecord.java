package com.example.smartmoney.domain.models.message;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import com.example.smartmoney.domain.models.message.enums.MessageStatus;

public class MessageRecord {

    private final String jobId;
    private final String  data;
    private final String tokenId;
    private final String inputType;
    private MessageStatus status;

    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private int retryCount;
    private String result;
    private String errorMessage;

    private static final int MAX_RETRY = 3;

    public MessageRecord(String data, String tokenId, String inputType) {
        this.jobId = UUID.randomUUID().toString();
        this.data = data;
        this.inputType = inputType;
        this.tokenId = tokenId;

        this.status = MessageStatus.PENDING;

        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;

        this.retryCount = 0;
    }



    // -------- GETTERS --------

    public String getJobId() {
        return jobId;
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

    public  String getdata() {
        return data;
    }

    public  String getInputType() {
        return inputType;
    }
}