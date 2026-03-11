package com.example.smartmoney.application.usecases.queue;

import com.example.smartmoney.domain.models.queue.MessageRecord;
import com.example.smartmoney.domain.repositories.MessageRepository;
import com.example.smartmoney.application.ports.event.RedisJobCachePort;
import com.example.smartmoney.application.ports.event.EventPublisherPort;


public class ProcessJobRResultUseCase {
    private final MessageRepository messageRepository;
    private final RedisJobCachePort redisJobCachePort;
    private final EventPublisherPort eventPublisherPort;

    public ProcessJobRResultUseCase(MessageRepository messageRepository, RedisJobCachePort redisJobCachePort, EventPublisherPort eventPublisherPort) {
        this.messageRepository = messageRepository;
        this.redisJobCachePort = redisJobCachePort;
        this.eventPublisherPort = eventPublisherPort;
    }

    public void processJobResult (String jobId, Object payload) {
        MessageRecord record = messageRepository.findByJobId(jobId).orElseThrow(() -> new RuntimeException("No record for jobId: " + jobId));

        // Update the record status to completed
        record.completeProcessing();

        messageRepository.update(record);

        redisJobCachePort.delete(jobId);

        eventPublisherPort.publishJobSuccess(jobId, record.getIdempotencyKey(), payload);

    }
}