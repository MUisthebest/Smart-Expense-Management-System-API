package com.example.smartmoney.application.ports.event;
import com.example.smartmoney.application.dtos.responses.JobResponse;

public interface EventConsumerPort {
    Optional<JobResponse<?>>consume (String jobId);
}
