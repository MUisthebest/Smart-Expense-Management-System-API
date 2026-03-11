package com.example.smartmoney.application.usecases.queue;
import com.example.smartmoney.domain.models.queue.MessageRecord;
import com.example.smartmoney.domain.services.QueueDomainService;
import com.example.smartmoney.domain.repositories.MessageRepository;
import com.example.smartmoney.application.ports.event.RedisJobCachePort;
import com.example.smartmoney.application.ports.event.EventPublisherPort;

//Publish the Event to the message broker

public class PublishJobEventUseCase {
    private final EventPublisherPort eventPublisherPort;
    private final RedisJobCachePort redisJobCachePort;

    public PublishJobEventUseCase(EventPublisherPort eventPublisherPort, RedisJobCachePort redisJobCachePort) {
        this.eventPublisherPort = eventPublisherPort;
        this.redisJobCachePort = redisJobCachePort;
    }

    public void executeRecord (MessageRecord record) {
        String jobId = record.getJobId();
        String tokenId = record.getIdempotencyKey();
        Object payload = record.getPayload();

        // Store in Redis before publish event to Kafka

        redisJobCachePort.store(jobId, tokenId, payload);
        eventPublisherPort.publishJobCreated(jobId, tokenId, payload);
    }

    
}
