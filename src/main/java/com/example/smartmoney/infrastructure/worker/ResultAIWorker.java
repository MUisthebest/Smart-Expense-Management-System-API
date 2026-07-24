package com.example.smartmoney.infrastructure.worker;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.LinkedHashMap;
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
        private final String streamKey;
        private volatile boolean groupReady = false;

        public ResultAIWorker(
                        @Qualifier("aiStringRedisTemplate") RedisTemplate<String, String> redisTemplate,
                        SimpMessagingTemplate messagingTemplate,
                        ObjectMapper objectMapper,
                        @Value("${REDIS_RESULT_STREAM_KEY:result_stream}") String streamKey) {
                this.redisTemplate = redisTemplate;
                this.messagingTemplate = messagingTemplate;
                this.objectMapper = objectMapper;
                this.streamKey = streamKey;
        }

        private static final Logger logger = LoggerFactory.getLogger(ResultAIWorker.class);

        private static final String GROUP = "result_group";
        private static final String CONSUMER = "backend-consumer";

        private final AtomicBoolean running = new AtomicBoolean(true);

        private Thread workerThread;
        /*
         * Type Budget Worker trả về.
         */
        private static final String BUDGET_RESULT_TYPE = "BUDGET_ALLOCATION_RESULT";

        private static final Duration RESULT_TTL = Duration.ofMinutes(10);

        @PostConstruct
        public void init() {
                ensureConsumerGroup();
                groupReady = true;
                workerThread = new Thread(
                                this::consume,
                                "ResultAIWorker-Consumer");

                workerThread.setDaemon(true);
                workerThread.start();

                logger.info("ResultAIWorker started stream={}", streamKey);
        }

        private void ensureConsumerGroup() {
                try {
                        // tạo stream nếu chưa có
                        redisTemplate.opsForStream().add(streamKey, Map.of("init", "true"));

                        var groups = redisTemplate.opsForStream().groups(streamKey);

                        boolean exists = groups.stream()
                                        .anyMatch(g -> GROUP.equals(g.groupName()));

                        if (!exists) {
                                redisTemplate.opsForStream().createGroup(streamKey, GROUP);
                                logger.info("Created consumer group {} for {}", GROUP, streamKey);
                        }

                } catch (RedisSystemException e) {
                        if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                                return;
                        }
                        throw e;
                } catch (Exception e) {
                        throw new RuntimeException("Cannot ensure Redis consumer group", e);
                }
        }

        @PreDestroy
        public void shutdown() {
                running.set(false);

                if (workerThread != null) {
                        workerThread.interrupt();
                }

                logger.info("ResultAIWorker stopped");
        }

        private void consume() {
                if (!groupReady) {
                        ensureConsumerGroup();
                        groupReady = true;
                }
                while (running.get()) {
                        try {
                                List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream().read(
                                                Consumer.from(
                                                                GROUP,
                                                                CONSUMER),
                                                StreamReadOptions.empty()
                                                                .count(10)
                                                                .block(Duration.ofSeconds(2)),
                                                StreamOffset.create(
                                                                streamKey,
                                                                ReadOffset.lastConsumed()));

                                if (messages == null || messages.isEmpty()) {
                                        continue;
                                }

                                for (MapRecord<String, Object, Object> message : messages) {
                                        processMessage(message);
                                }

                        } catch (Exception exception) {

                                String message = exception.getMessage();

                                if (message != null && message.contains("NOGROUP")) {
                                        logger.warn("Consumer group missing, recreating...");

                                        groupReady = false;
                                        ensureConsumerGroup();
                                        groupReady = true;

                                        continue;
                                }

                                logger.error("Error consuming AI result stream", exception);

                                try {
                                        Thread.sleep(500);
                                } catch (InterruptedException e) {
                                        Thread.currentThread().interrupt();
                                        break;
                                }
                        }
                }
        }

        private void processMessage(
                        MapRecord<String, Object, Object> message) {
                try {
                        Map<Object, Object> redisPayload = message.getValue();

                        logger.warn(
                                        "BE RAW RESULT STREAM messageId={} payload={}",
                                        message.getId(),
                                        objectMapper.writeValueAsString(redisPayload));

                        String jobId = getStringValue(
                                        redisPayload,
                                        "jobId");

                        String userId = getStringValue(
                                        redisPayload,
                                        "userId");

                        String type = getStringValue(
                                        redisPayload,
                                        "type");

                        /*
                         * Message init chỉ dùng để tạo stream.
                         */
                        if (redisPayload.containsKey("init")) {
                                acknowledgeAndDelete(message);
                                return;
                        }

                        if (jobId == null || jobId.isBlank()) {
                                logger.warn(
                                                "Missing jobId in AI result, messageId={}",
                                                message.getId());

                                acknowledgeAndDelete(message);
                                return;
                        }

                        if (userId == null || userId.isBlank()) {
                                logger.warn(
                                                "Missing userId in AI result, jobId={}",
                                                jobId);

                                acknowledgeAndDelete(message);
                                return;
                        }

                        /*
                         * Mặc định gửi nguyên payload Redis.
                         *
                         * Transaction, voice và image không bị chỉnh sửa.
                         */
                        Object payloadToSend = redisPayload;

                        /*
                         * Chỉ xử lý riêng kết quả Budget AI.
                         *
                         * Python gửi:
                         * result = "{\"totalBudget\":13900000,...}"
                         *
                         * Backend chuyển thành:
                         * result = {
                         * "totalBudget": 13900000,
                         * ...
                         * }
                         */
                        if (BUDGET_RESULT_TYPE.equalsIgnoreCase(type)) {
                                payloadToSend = buildBudgetPayload(
                                                redisPayload);
                                logger.warn(
                                                "BE PARSED BUDGET RESULT messageId={} jobId={} payload={}",
                                                message.getId(),
                                                jobId,
                                                objectMapper.writeValueAsString(payloadToSend));
                        }

                        /*
                         * Lưu kết quả để frontend có thể lấy lại
                         * nếu bị mất message WebSocket.
                         */
                        saveResult(
                                        jobId,
                                        payloadToSend);

                        /*
                         * Gửi kết quả lên frontend.
                         */
                        sendResultToUser(
                                        userId,
                                        String.valueOf(message.getId()),
                                        payloadToSend);

                        /*
                         * Chỉ ACK và DELETE sau khi lưu và gửi thành công.
                         */
                        acknowledgeAndDelete(message);

                        logger.info(
                                        "Processed AI result jobId={} userId={} type={}",
                                        jobId,
                                        userId,
                                        type);

                } catch (Exception exception) {
                        /*
                         * Không ACK khi lỗi hệ thống.
                         * Message vẫn nằm trong pending list để kiểm tra/retry.
                         */
                        logger.error(
                                        "Error processing AI result messageId={} payload={}",
                                        message.getId(),
                                        message.getValue(),
                                        exception);
                }
        }

        /**
         * Chỉ dùng cho BUDGET_ALLOCATION_RESULT.
         *
         * Copy toàn bộ field từ Redis, sau đó parse riêng
         * field "result" nếu nó đang là JSON string.
         */
        Map<String, Object> buildBudgetPayload(
                        Map<Object, Object> redisPayload) {
                Map<String, Object> budgetPayload = new LinkedHashMap<>();

                redisPayload.forEach((key, value) -> budgetPayload.put(
                                String.valueOf(key),
                                value));

                Object rawResult = budgetPayload.get("result");

                if (!(rawResult instanceof String resultJson)) {
                        /*
                         * Nếu result đã là Map/Object thì giữ nguyên.
                         */
                        return budgetPayload;
                }

                if (resultJson.isBlank()) {
                        return budgetPayload;
                }

                try {
                        Object parsedResult = objectMapper.readValue(
                                        resultJson,
                                        Object.class);

                        budgetPayload.put(
                                        "result",
                                        parsedResult);

                        logger.info(
                                        "Parsed Budget AI result successfully, jobId={}",
                                        budgetPayload.get("jobId"));

                } catch (Exception exception) {
                        /*
                         * Nếu parse thất bại, vẫn giữ result JSON string
                         * để không làm mất kết quả AI.
                         */
                        logger.warn(
                                        "Cannot parse Budget AI result, jobId={}, rawResult={}",
                                        budgetPayload.get("jobId"),
                                        resultJson,
                                        exception);
                }

                return budgetPayload;
        }

        private void saveResult(
                        String jobId,
                        Object payload) throws Exception {
                String key = "ai:job:" + jobId;
                String legacyKey = "job:" + jobId;

                String jsonPayload = objectMapper.writeValueAsString(payload);

                redisTemplate.opsForValue().set(
                                key,
                                jsonPayload,
                                RESULT_TTL);
                redisTemplate.opsForValue().set(
                                legacyKey,
                                jsonPayload,
                                RESULT_TTL);
        }

        private void sendResultToUser(
                        String userId,
                        String messageId,
                        Object payload) throws Exception {
                String destination = "/topic/ai/user/" + userId;

                logger.warn(
                                "BE SENDING RESULT messageId={} jobId={} destination={} payload={}",
                                messageId,
                                extractJobId(payload),
                                destination,
                                objectMapper.writeValueAsString(payload));

                /*
                 * payload được khai báo là Object để Java chọn đúng overload:
                 * convertAndSend(String destination, Object payload)
                 */
                messagingTemplate.convertAndSend(
                                destination,
                                payload);
        }

        private void acknowledgeAndDelete(
                        MapRecord<String, Object, Object> message) {
                redisTemplate.opsForStream().acknowledge(
                                streamKey,
                                GROUP,
                                message.getId());

                redisTemplate.opsForStream().delete(
                                streamKey,
                                message.getId());
        }

        private String extractJobId(Object payload) {
                if (payload instanceof Map<?, ?> map) {
                        Object value = map.get("jobId");
                        return value == null ? null : String.valueOf(value);
                }
                return null;
        }

        private String getStringValue(
                        Map<Object, Object> payload,
                        String key) {
                Object value = payload.get(key);

                if (value == null) {
                        return null;
                }

                String stringValue = String.valueOf(value);

                return stringValue.isBlank()
                                ? null
                                : stringValue;
        }
}
