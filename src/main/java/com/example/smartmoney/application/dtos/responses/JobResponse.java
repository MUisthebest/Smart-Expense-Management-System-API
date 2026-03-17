package com.example.smartmoney.application.dtos.responses;
import com.example.smartmoney.domain.models.message.enums.MessageStatus;

public class JobResponse<T> {

    private String jobId;

    private String tokenId;

    private T payload;

    private MessageStatus status;

    public JobResponse() {}

    public JobResponse(String jobId, String tokenId, T payload, MessageStatus status) {
        this.jobId = jobId;
        this.tokenId = tokenId;
        this.payload = payload;
        this.status = status;
    }

    public String getJobId() {
        return jobId;
    }

    public String getTokenId() {
        return tokenId;
    }

    public T getPayload() {
        return payload;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public void setPayload(T payload) {
        this.payload = payload;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }
}
