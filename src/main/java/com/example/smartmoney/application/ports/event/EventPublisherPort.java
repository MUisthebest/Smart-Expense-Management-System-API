package com.example.smartmoney.application.ports.event;
import com.example.smartmoney.application.dtos.requests.job.JobEvent;


public interface EventPublisherPort {
    void publishJob(String jobId, JobEvent jobEvent);
}
