package com.example.smartmoney.application.usecases.queue;
import com.example.smartmoney.application.ports.event.RedisJobCachePort;

import com.example.smartmoney.domain.exceptions.DuplicateJobException;
import com.example.smartmoney.domain.models.message.MessageRecord;
import com.example.smartmoney.infrastructure.integration.cloudinary.CloudinaryService;
import org.springframework.stereotype.Service;


@Service
public class ProcessQueueRequestUseCase {

    private final RedisJobCachePort redisJobCachePort;
    private final JobAsyncService jobAsyncService;
    private final CloudinaryService cloudinaryService;

    public ProcessQueueRequestUseCase(
            RedisJobCachePort redisJobCachePort,
            JobAsyncService jobAsyncService, CloudinaryService cloudinaryService) {

        this.redisJobCachePort = redisJobCachePort;
        this.jobAsyncService = jobAsyncService;
        this.cloudinaryService = cloudinaryService;
    }

    public MessageRecord execute(String data, String duty, String userId) {
        /*
        String hashKey = hashFile.hashFileMD5();
        String dedupKey = "dedup:" + userId + ":" + hashKey;

        // Check if the keyString exists in Redis
        Optional<String> existingKey = redisJobCachePort.getDedup(dedupKey);
        if (existingKey.isPresent()) {
            throw new RuntimeException("Duplicate request detected. A similar file has already been processed.");
        }
         */
        MessageRecord newRecord = new MessageRecord(data, userId, duty);
        jobAsyncService.process(data, userId, newRecord.getJobId(), duty);
        return newRecord;
    }
}