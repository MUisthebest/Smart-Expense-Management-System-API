package com.example.smartmoney.application.dtos.requests.job;

public class JobEvent {

    private String jobId;

    private String tokenId;

    private Object payload;

    public JobEvent() {}

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
