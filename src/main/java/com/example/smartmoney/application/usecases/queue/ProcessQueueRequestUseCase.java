package com.example.smartmoney.application.usecases.queue;

import com.example.smartmoney.domain.models.queue.MessageRecord;
import com.example.smartmoney.domain.services.QueueDomainService;
import com.example.smartmoney.domain.repositories.MessageRepository;
import com.example.smartmoney.application.ports.event.RedisJobCachePort;
import com.example.smartmoney.application.ports.event.EventPublisherPort;

import java.util.Optional;

public class ProcessQueueRequestUseCase {

    private final MessageRepository messageRepository;
    private final QueueDomainService queueDomainService;
    private final RedisJobCachePort redisJobCachePort;
    private final EventPublisherPort eventPublisherPort;

    public ProcessQueueRequestUseCase(
            MessageRepository messageRepository,
            QueueDomainService queueDomainService,
            RedisJobCachePort redisJobCachePort,
            EventPublisherPort eventPublisherPort) {

        this.messageRepository = messageRepository;
        this.queueDomainService = queueDomainService;
        this.redisJobCachePort = redisJobCachePort;
        this.eventPublisherPort = eventPublisherPort;
    }

    public MessageRecord execute(Object payload, String payloadHash, String tokenId) {

        Optional<MessageRecord> existing = messageRepository.findByPayloadHash(payloadHash);

        if (existing.isPresent()) {

            MessageRecord record = existing.get();

            if (queueDomainService.isStillProcessing(record)) {
                throw new RuntimeException("Request is still processing");
            }
        }

        MessageRecord newRecord = new MessageRecord(payload, payloadHash, tokenId);

        messageRepository.save(newRecord);

        redisJobCachePort.store(newRecord.getJobId(), tokenId, payload);
        eventPublisherPort.publishJobCreated(newRecord.getJobId(), tokenId, payload);

        return newRecord;
    }
}