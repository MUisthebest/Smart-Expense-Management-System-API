package com.example.smartmoney.application.dtos.requests;

public class JobEvent {

    private final String jobId;

    private final String tokenId;

    private final Object payload;


    public JobEvent(String jobId, String tokenId, Object payload) {
        this.jobId = jobId;
        this.tokenId = tokenId;
        this.payload = payload;
    }

    public String getJobId() {
        return jobId;
    }

    public String getTokenId() {
        return tokenId;
    }

    public Object getPayload() {
        return payload;
    }
}
