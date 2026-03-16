package com.example.smartmoney.application.usecases.queue;

import com.example.smartmoney.application.ports.event.EventPublisherPort;
import com.example.smartmoney.application.ports.event.RedisJobCachePort;
import com.example.smartmoney.application.ports.event.EventConsumerPort;
import com.example.smartmoney.domain.models.message.MessageRecord;
import com.example.smartmoney.domain.models.message.enums.MessageStatus;
import com.example.smartmoney.domain.repositories.MessageRepository;
import com.example.smartmoney.application.dtos.responses.JobResponse;
import com.example.smartmoney.domain.models.message.enums.MessageStatus;



public class ProcessJobResultUseCase {
    private final MessageRepository messageRepository;
    private final RedisJobCachePort redisJobCachePort;
    private final EventPublisherPort eventPublisherPort;
    private  final EventConsumerPort eventConsumerPort;

    public ProcessJobResultUseCase(MessageRepository messageRepository, RedisJobCachePort redisJobCachePort, EventPublisherPort eventPublisherPort, EventConsumerPort eventConsumerPort) {
        this.messageRepository = messageRepository;
        this.redisJobCachePort = redisJobCachePort;
        this.eventPublisherPort = eventPublisherPort;
        this.eventConsumerPort = eventConsumerPort;
    }

    public void processJobResult (String jobId) {
        JobResponse response = eventConsumerPort.consume(jobId).orElseThrow(() -> new RuntimeException("Job result not found for jobId: " + jobId));

        MessageRecord record = messageRepository.findByJobId(jobId)
                .orElseThrow(() -> new RuntimeException("No record for jobId: " + jobId));

        MessageStatus status = MessageStatus.valueOf(response.getStatus().name());

        if (status == MessageStatus.COMPLETED) {
            record.completeProcessing("COMPLETED");
        } else {
            record.failProcessing("FAILED");
        }

        messageRepository.update(record);
        //Delete the job from cache
        redisJobCachePort.delete(jobId);
    }
}