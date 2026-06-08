package com.example.smartmoney.infrastructure.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.*;

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
                        String duty = (String) payload.get("duty");
                        String type = (String) payload.get("type");

                        if (jobId == null || jobId.isBlank()) {
                            logger.warn("Missing jobId in AI result message: {}", msg);
                            redisTemplate.opsForStream()
                                    .acknowledge(STREAM, GROUP, msg.getId());
                            continue;
                        }

                        Map<String, Object> wsPayload = new LinkedHashMap<>();

                        payload.forEach((key, value) -> {
                            wsPayload.put(String.valueOf(key), value);
                        });

                        if ("BUDGET_ALLOCATION_PLAN".equals(duty)
                                || "BUDGET_ALLOCATION_RESULT".equals(type)) {

                            Map<String, Object> normalizedResult = normalizeBudgetResult(payload);

                            wsPayload.put("result", normalizedResult);
                        }

                        // 1. SAVE RESULT
                        String key = "job:" + jobId;

                        redisTemplate.opsForValue().set(
                                key,
                                objectMapper.writeValueAsString(wsPayload),
                                Duration.ofMinutes(10)
                        );

                        // 2. PUSH WS - GIỮ NGUYÊN TOPIC
                        messagingTemplate.convertAndSend(
                                "/topic/ai/" + jobId,
                                (Object) wsPayload
                        );

                        // 3. ACK + DELETE STREAM
                        redisTemplate.opsForStream()
                                .acknowledge(STREAM, GROUP, msg.getId());

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

    private Map<String, Object> normalizeBudgetResult(Map<Object, Object> payload) throws Exception {
        Object resultRaw = payload.get("result");

        if (resultRaw == null) {
            return Map.of();
        }

        Map<String, Object> result;

        if (resultRaw instanceof String resultString) {
            result = objectMapper.readValue(
                    resultString,
                    new TypeReference<Map<String, Object>>() {}
            );
        } else {
            result = objectMapper.convertValue(
                    resultRaw,
                    new TypeReference<Map<String, Object>>() {}
            );
        }

        Number totalBudgetNumber = (Number) result.get("totalBudget");
        long totalBudget = totalBudgetNumber.longValue();

        String currency = String.valueOf(result.getOrDefault("currency", "VND"));

        List<Map<String, Object>> categories =
                objectMapper.convertValue(
                        result.get("categories"),
                        new TypeReference<List<Map<String, Object>>>() {}
                );

        List<Map<String, Object>> normalizedCategories = new ArrayList<>();

        long allocated = 0;

        for (int i = 0; i < categories.size(); i++) {
            Map<String, Object> item = categories.get(i);

            String category = String.valueOf(item.get("category"));

            long amount;

            if (i == categories.size() - 1) {
                amount = totalBudget - allocated;
            } else {
                Number ratioNumber = (Number) item.get("ratio");

                BigDecimal ratio = BigDecimal.valueOf(ratioNumber.doubleValue());
                amount = BigDecimal.valueOf(totalBudget)
                        .multiply(ratio)
                        .setScale(0, RoundingMode.HALF_UP)
                        .longValue();

                allocated += amount;
            }

            Map<String, Object> normalizedItem = new LinkedHashMap<>();
            normalizedItem.put("category", category);
            normalizedItem.put("amount", amount);

            normalizedCategories.add(normalizedItem);
        }

        Map<String, Object> normalizedResult = new LinkedHashMap<>();
        normalizedResult.put("totalBudget", totalBudget);
        normalizedResult.put("currency", currency);
        normalizedResult.put("categories", normalizedCategories);

        return normalizedResult;
    }
}