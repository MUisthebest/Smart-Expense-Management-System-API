package com.example.smartmoney.application.ports.event;
import com.example.smartmoney.application.dtos.requests.JobEvent;

public interface JobQueuePort {
        void PublishJob(String JobId, JobEvent jobEvent);
}
