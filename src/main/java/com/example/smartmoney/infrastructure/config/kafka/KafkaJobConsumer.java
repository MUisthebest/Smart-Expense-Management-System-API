package com.example.smartmoney.infrastructure.config.kafka;

import com.example.smartmoney.application.dtos.requests.job.JobEvent;
import com.example.smartmoney.application.dtos.responses.JobResponse;
import com.example.smartmoney.application.ports.event.EventConsumerPort;
import com.example.smartmoney.application.ports.event.RedisJobCachePort;
import com.example.smartmoney.domain.models.message.enums.MessageStatus;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class KafkaJobConsumer implements EventConsumerPort {

    private final RedisJobCachePort redisJobCachePort;

    public KafkaJobConsumer (RedisJobCachePort redisJobCachePort) {
        this.redisJobCachePort = redisJobCachePort;
    }

    @KafkaListener(topics = "ai-jobs-results", groupId = "smartmoney-groupid")
    public void listenJobResult (JobEvent jobEvent) {
        JobResponse<Object> response = new JobResponse<>();
        response.setJobId(jobEvent.getJobId());
        response.setTokenId(jobEvent.getTokenId());
        response.setPayload(jobEvent.getPayload());
        response.setStatus(MessageStatus.COMPLETED);
        redisJobCachePort.store(jobEvent.getJobId(), response);    }
    // Method từ interface EventConsumerPort
    @Override
    public Optional<JobResponse<?>> consume(String jobId) {
        return redisJobCachePort.find(jobId);
    }
}