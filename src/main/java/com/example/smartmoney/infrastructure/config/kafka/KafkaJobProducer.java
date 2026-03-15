package com.example.smartmoney.infrastructure.config.kafka;
import com.example.smartmoney.application.ports.event.EventPublisherPort;
import org.springframework.kafka.core.KafkaTemplate;
import com.example.smartmoney.application.dtos.requests.job.JobEvent;


public class KafkaJobProducer implements EventPublisherPort {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String JOB_TOPIC = "ai-jobs";
    public KafkaJobProducer (KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishJob(String jobId, JobEvent jobEvent) {
        kafkaTemplate.send(JOB_TOPIC, jobId, jobEvent);
    }
}

