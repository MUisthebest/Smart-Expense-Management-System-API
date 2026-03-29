package com.example.smartmoney.application.usecases.queue;

import com.example.smartmoney.application.dtos.requests.JobEvent;
import com.example.smartmoney.application.ports.event.JobQueuePort;
import com.example.smartmoney.infrastructure.config.redis.JobQueuePortAdapter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class JobAsyncService {
    private JobQueuePort jobQueuePort;

    public JobAsyncService( JobQueuePort jobQueuePort) {
        this.jobQueuePort = jobQueuePort;

    }

    @Async
    public void process(String url, String tokenId, String jobId, String duty) {
        JobEvent jobEvent = new JobEvent(jobId, tokenId, url, duty);
        jobQueuePort.PublishJob(jobId, jobEvent);
    }
}
