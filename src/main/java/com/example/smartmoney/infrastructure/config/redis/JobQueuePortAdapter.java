package com.example.smartmoney.infrastructure.config.redis;

import com.example.smartmoney.application.dtos.requests.JobEvent;
import com.example.smartmoney.application.ports.event.JobQueuePort;
import com.example.smartmoney.domain.exceptions.DuplicateJobException;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.api.sync.RedisStreamCommands;
import io.lettuce.core.codec.StringCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JobQueuePortAdapter implements JobQueuePort {

    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> streamCommands;
    private final int streamGroupCount;
    private final long maxJobsPerStream;
    private final long messageTTLSeconds;
    private static final String DEDUP_KEY_PREFIX = "dedup";

    public JobQueuePortAdapter(RedisConnectionFactory connectionFactory,
                               @Value("${REDIS_STREAMGROUP_COUNT}") int streamGroupCount,
                               @Value("${REDIS_MAX_STREAMGROUP_SIZE}") long maxJobsPerStream,
                               @Value("${REDIS_STREAM_TTL_SECONDS}") long ttlSeconds,
                               @Value("${REDIS_MESSAGE_TTL_SECONDS}") long messageTTLSeconds) {
        RedisClient redisClient = (RedisClient) ((LettuceConnectionFactory) connectionFactory).getNativeClient();
        this.connection = redisClient.connect(StringCodec.UTF8);
        this.streamCommands = connection.sync();
        connection.sync().ping();
        this.streamGroupCount = streamGroupCount;
        this.maxJobsPerStream = maxJobsPerStream;
        this.messageTTLSeconds = messageTTLSeconds;

        System.out.println("streamGroupCount: " + streamGroupCount);
        System.out.println("maxJobsPerStream: " + maxJobsPerStream);
        System.out.println("ttlSeconds: " + ttlSeconds);
    }

    @Override
    public void PublishJob(String jobId, JobEvent jobEvent) {
        //Create unique dedup key for checking existence of the same fileUrl and same user
        String dedupKey = DEDUP_KEY_PREFIX + ":" + jobEvent.getUserId() + ":" + jobEvent.getPayload();

        //SET NX EX
        String result = streamCommands.set(
                dedupKey,
                jobId,
                SetArgs.Builder.nx().ex(messageTTLSeconds)
        );
        if (result == null) {
            throw new DuplicateJobException("Duplicate job for userId: " + jobEvent.getUserId() + " with same data already exists.");
        }

        //Add job into stream
        int selectedPartition = (jobId.hashCode() & Integer.MAX_VALUE) % streamGroupCount;
        String streamKey = "stream:jobs:" + selectedPartition;

        RedisStreamCommands<String, String> streamCommands = connection.sync();

        XAddArgs args = XAddArgs.Builder
                .maxlen(maxJobsPerStream)
                .approximateTrimming(true);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("jobId",     jobEvent.getJobId());
        body.put("userId",    jobEvent.getUserId());
        body.put("data",   jobEvent.getPayload());
        body.put("duty",      jobEvent.getDuty());
        body.put("createdAt", String.valueOf(System.currentTimeMillis()));

        String recordId = streamCommands.xadd(streamKey, args, body);
        System.out.println("xadd success, recordId: " + recordId);
    }
}