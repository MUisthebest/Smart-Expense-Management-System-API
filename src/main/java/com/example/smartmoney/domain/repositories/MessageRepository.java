package com.example.smartmoney.domain.repositories;

import java.util.Optional;

import com.example.smartmoney.domain.models.message.MessageRecord;

public interface MessageRepository {
    Optional<MessageRecord<?>> findByTokenId(String tokenId);

    Optional<MessageRecord<?>> findByJobId(String jobId);

    void save(MessageRecord<?> record);

    void delete(MessageRecord<?> record);

    void update(MessageRecord<?> record);
}
