package com.example.smartmoney.application.ports.event;
import com.example.smartmoney.application.dtos.responses.JobResponse;
import java.util.Optional;

public interface EventConsumerPort {
    Optional<JobResponse<?>>consume (String jobId);
}
