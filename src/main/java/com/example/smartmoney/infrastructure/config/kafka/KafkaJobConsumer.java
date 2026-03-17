package com.example.smartmoney.infrastructure.config.kafka;
import com.example.smartmoney.application.dtos.requests.JobEvent;
import com.example.smartmoney.application.dtos.responses.JobResponse;
import com.example.smartmoney.application.ports.event.EventConsumerPort;
import com.example.smartmoney.application.ports.event.RedisJobCachePort;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class KafkaJobConsumer implements EventConsumerPort {

    private final RedisJobCachePort redisJobCachePort;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KafkaJobConsumer(RedisJobCachePort redisJobCachePort) {
        this.redisJobCachePort = redisJobCachePort;
    }

    @KafkaListener(topics = "ai-jobs-results", groupId = "smartmoney-groupid")
    public void listenJobResult(byte[] message) {

        try {
            JobEvent jobEvent = objectMapper.readValue(message, JobEvent.class);

            JobResponse<Object> response = new JobResponse<>();
            response.setJobId(jobEvent.getJobId());
            response.setTokenId(jobEvent.getTokenId());
            response.setPayload(jobEvent.getPayload());

            redisJobCachePort.storeResult(jobEvent.getJobId(), response);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Optional<JobResponse<?>> consume(String jobId) {
        return redisJobCachePort.findResult(jobId);
    }
}