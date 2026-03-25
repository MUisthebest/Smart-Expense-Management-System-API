package com.example.smartmoney.application.usecases.queue;


import com.example.smartmoney.application.dtos.responses.JobResponse;
import com.example.smartmoney.application.helper.hashFile;
import com.example.smartmoney.application.ports.event.RedisJobCachePort;
import com.example.smartmoney.application.usecases.queue.JobAsyncService;

import com.example.smartmoney.domain.models.message.MessageRecord;
import com.example.smartmoney.domain.models.message.enums.MessageStatus;
import com.example.smartmoney.infrastructure.integration.cloudinary.CloudinaryService;
import jakarta.mail.Multipart;
import org.apache.commons.codec.cli.Digest;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class ProcessQueueRequestUseCase<T> {

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

    public MessageRecord<T> execute(MultipartFile file, String InputType, String userId) {
        // Generate keyString from the payload file by reading bytes
        String hashKey = hashFile.hashFileMD5(file);
        String dedupKey = "dedup:" + userId + ":" + hashKey;

        // Check if the keyString exists in Redis
        Optional<String> existingKey = redisJobCachePort.getDedup(dedupKey);
        if (existingKey.isPresent()) {
            throw new RuntimeException("Duplicate request detected. A similar file has already been processed.");
        }

        // Save the image/video to cloudinary
        Map<String, String> fileUpload = cloudinaryService.uploadVideoImage(file, "AI-Input");
        String publicId = fileUpload.get("publicId");
        String url = fileUpload.get("url");


        MessageRecord<T> newRecord = new MessageRecord<>(url, userId, InputType);
        jobAsyncService.process(url, dedupKey, userId, newRecord.getJobId());
        return newRecord;
    }
}