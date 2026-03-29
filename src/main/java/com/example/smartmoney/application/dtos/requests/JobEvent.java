package com.example.smartmoney.application.dtos.requests;

public class JobEvent {

    private final String jobId;

    private final String userId;

    private final String payload;

    private final String duty;


    public JobEvent(String jobId, String userId, String payload, String duty) {
        this.jobId = jobId;
        this.userId = userId;
        this.duty = duty;
        this.payload = payload;
    }

    public String getDuty() {
        return duty;
    }

    public String getJobId() {
        return jobId;
    }

    public String getPayload() {
        return payload;
    }

    public String getUserId() {
        return userId;
    }
}
