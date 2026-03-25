package com.example.smartmoney.application.usecases.queue;
import com.example.smartmoney.application.dtos.requests.JobEvent;
import com.example.smartmoney.application.ports.event.EventPublisherPort;
import com.example.smartmoney.application.ports.event.RedisJobCachePort;
import com.example.smartmoney.domain.models.message.MessageRecord;
import com.example.smartmoney.domain.repositories.MessageRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class JobAsyncService {
    private final MessageRepository messageRepository;
    private final RedisJobCachePort redisJobCachePort;
    private final EventPublisherPort eventPublisherPort;

    public JobAsyncService(
            MessageRepository messageRepository,
            RedisJobCachePort redisJobCachePort,
            EventPublisherPort eventPublisherPort) {

        this.messageRepository = messageRepository;
        this.redisJobCachePort = redisJobCachePort;
        this.eventPublisherPort = eventPublisherPort;
    }

    @Async
    public <T> void process(String url, String jobKey, String tokenId, String jobId) {

        //messageRepository.save(record);
        redisJobCachePort.setDedup(jobKey, jobId);

        redisJobCachePort.addJobForUser(tokenId, jobId, url);

        /*
        JobEvent event = new JobEvent(
                record.getJobId(),
                tokenId,
                payload
        );
        */

        //eventPublisherPort.publishJob(record.getJobId(), event);
    }
}
