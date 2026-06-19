package com.example.smartmoney.infrastructure.publisher;

import com.example.smartmoney.application.dtos.requests.JobEvent;
import com.example.smartmoney.application.ports.event.JobQueuePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JobQueuePublisher implements JobQueuePort {

    private final StringRedisTemplate redisTemplate;
    private final int streamGroupCount;
    private final long maxJobsPerStream;
    private final long ttlSeconds;

    public JobQueuePublisher(
            @Qualifier("aiStringRedisTemplate") StringRedisTemplate redisTemplate,
            @Value("${REDIS_STREAMGROUP_COUNT}") int streamGroupCount,
            @Value("${REDIS_MAX_STREAMGROUP_SIZE}") long maxJobsPerStream,
            @Value("${REDIS_STREAM_TTL_SECONDS}") long ttlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.streamGroupCount = streamGroupCount;
        this.maxJobsPerStream = maxJobsPerStream;
        this.ttlSeconds = ttlSeconds;
    }

    @Override
    public void PublishJob(String jobId, JobEvent jobEvent) {
        int selectedPartition = (jobId.hashCode() & Integer.MAX_VALUE) % streamGroupCount;
        String streamKey = "stream:jobs:" + selectedPartition;

        Map<String, String> body = new LinkedHashMap<>();
        body.put("jobId", jobEvent.getJobId());
        body.put("userId", jobEvent.getUserId());
        body.put("data", jobEvent.getPayload());
        body.put("duty", jobEvent.getDuty());
        body.put("createdAt", String.valueOf(System.currentTimeMillis()));

        redisTemplate.opsForStream().add(
            StreamRecords.mapBacked(body)
                .withStreamKey(streamKey),
            XAddOptions.maxlen(maxJobsPerStream)
                .approximateTrimming(true)
        );

//        System.out.println("xadd success");
//        System.out.println("Redis publisher received jobId = " + jobId);
        RecordId recordId =
                redisTemplate.opsForStream().add(
                        StreamRecords
                                .mapBacked(body)
                                .withStreamKey(streamKey),
                        XAddOptions
                                .maxlen(maxJobsPerStream)
                                .approximateTrimming(true)
                );

        System.out.println(
                "PUBLISHED AI JOB stream="
                        + streamKey
                        + " messageId="
                        + recordId
                        + " jobId="
                        + jobId
                        + " duty="
                        + jobEvent.getDuty()
        );
    }
}