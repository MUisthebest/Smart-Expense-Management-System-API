package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.queue.MessageRecord;
import java.util.Optional;

public interface MessageRepository {
    Optional<MessageRecord> findByPayloadHash(String payloadHash);

    Optional<MessageRecord> findByJobId(String jobId);

    void save(MessageRecord record);

    void delete(MessageRecord record);

    void update(MessageRecord record);
}
