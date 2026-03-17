package com.example.smartmoney.infrastructure.kafka;

import com.example.smartmoney.application.ports.event.EventPublisherPort;
import com.example.smartmoney.application.dtos.requests.job.JobEvent;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaJobProducer implements EventPublisherPort {

    private final KafkaTemplate<String, byte[]> kafkaTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String JOB_TOPIC = "ai-jobs";

    public KafkaJobProducer(KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishJob(String jobId, JobEvent jobEvent) {

        try {
            byte[] json = objectMapper.writeValueAsBytes(jobEvent);

            kafkaTemplate.send(
                    JOB_TOPIC,
                    jobId,
                    json
            );

        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize job event", e);
        }
    }
}