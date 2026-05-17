package com.example.smartmoney.infrastructure.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class ResultAIWorker {

    private final RedisTemplate<String, String> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public ResultAIWorker(
            @Qualifier("aiStringRedisTemplate") RedisTemplate<String, String> redisTemplate,
            SimpMessagingTemplate messagingTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    private static final Logger logger = LoggerFactory.getLogger(ResultAIWorker.class);

    private static final String STREAM = "result_stream";
    private static final String GROUP = "result_group";
    private static final String CONSUMER = "backend-consumer";

    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread workerThread;

    @PostConstruct
    public void init() {
        try {
            // đảm bảo stream tồn tại trước khi tạo group
            redisTemplate.opsForStream().add(STREAM, Map.of("init", "true"));

            redisTemplate.opsForStream().createGroup(STREAM, GROUP);
            logger.info("Created Redis Stream group");
        } catch (Exception e) {
            logger.warn("Group may already exist: {}", e.getMessage());
        }

        workerThread = new Thread(this::consume, "ResultAIWorker-Consumer");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private void consume() {
        while (running.get()) {
            try {
                List<MapRecord<String, Object, Object>> messages =
                        redisTemplate.opsForStream().read(
                                Consumer.from(GROUP, CONSUMER),
                                StreamReadOptions.empty()
                                        .count(10)
                                        .block(Duration.ofSeconds(2)),
                                StreamOffset.create(STREAM, ReadOffset.lastConsumed())
                        );

                if (messages == null || messages.isEmpty()) continue;

                for (MapRecord<String, Object, Object> msg : messages) {
                    try {
                        Map<Object, Object> payload = msg.getValue();

                        String jobId = (String) payload.get("jobId");

                        if (jobId == null) {
                            logger.warn("Missing jobId in message: {}", payload);
                            continue;
                        }

                        // 🔥 PUSH WEBSOCKET
                        messagingTemplate.convertAndSend(
                                "/topic/ai/" + jobId,
                                payload
                        );

                        // ACK sau khi xử lý thành công
                        redisTemplate.opsForStream()
                                .acknowledge(STREAM, GROUP, msg.getId());

                        // optional delete
                        redisTemplate.opsForStream()
                                .delete(STREAM, msg.getId());

                    } catch (Exception ex) {
                        logger.error("Error processing message: {}", msg, ex);
                    }
                }

            } catch (Exception e) {
                logger.error("Error consuming AI result stream", e);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}