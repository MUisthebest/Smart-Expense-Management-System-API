package com.example.smartmoney.application.usecases.queue;

import com.example.smartmoney.application.dtos.responses.JobResponse;
import com.example.smartmoney.domain.models.message.enums.MessageStatus;
import com.example.smartmoney.domain.models.message.MessageRecord;
import com.example.smartmoney.domain.repositories.MessageRepository;
import com.example.smartmoney.application.ports.event.RedisJobCachePort;
import com.example.smartmoney.application.ports.event.EventPublisherPort;
import com.example.smartmoney.application.dtos.requests.job.JobEvent;

import java.util.List;
import java.util.Optional;

public class ProcessQueueRequestUseCase {

    private final MessageRepository messageRepository;
    private final RedisJobCachePort redisJobCachePort;
    private final EventPublisherPort eventPublisherPort;

    public ProcessQueueRequestUseCase(
            MessageRepository messageRepository,
            RedisJobCachePort redisJobCachePort,
            EventPublisherPort eventPublisherPort) {

        this.messageRepository = messageRepository;
        this.redisJobCachePort = redisJobCachePort;
        this.eventPublisherPort = eventPublisherPort;
    }

    public MessageRecord execute(Object payload, String tokenId) {

        // ========================
        // CHECK EXISTING JOB
        // ========================
        List<String> jobIds = redisJobCachePort.findJobsByUser(tokenId);

        for (String jobId : jobIds) {

            Optional<JobResponse<?>> responseOpt = redisJobCachePort.find(jobId);

            if (responseOpt.isPresent()) {

                JobResponse<?> response = responseOpt.get();

                if (response.getStatus() == MessageStatus.PROCESSING) {
                    throw new RuntimeException("A job with this user is already processing");
                }
            }
        }

        // ========================
        // CREATE RECORD
        // ========================
        MessageRecord newRecord = new MessageRecord(payload, tokenId);
        messageRepository.save(newRecord);

        String jobId = newRecord.getJobId();

        // ========================
        // STORE JOB IN REDIS (PROCESSING)
        // ========================
        JobResponse<Object> jobResponse = new JobResponse<>(
                jobId,
                tokenId,
                null,
                MessageStatus.PROCESSING
        );

        redisJobCachePort.store(jobId, jobResponse);

        // ========================
        // ADD JOB TO USER
        // ========================
        redisJobCachePort.addJobForUser(tokenId, jobId);

        // ========================
        // PUBLISH EVENT (KAFKA)
        // ========================
        JobEvent event = new JobEvent(
                jobId,
                tokenId,
                payload
        );

        eventPublisherPort.publishJob(jobId, event);

        return newRecord;
    }
}