package com.example.smartmoney.infrastructure.persistence.repositories;
import com.example.smartmoney.domain.models.message.MessageRecord;
import com.example.smartmoney.domain.repositories.MessageRepository;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class InMemoryMessageRepository implements MessageRepository {
    private final Map<String, MessageRecord<?>> storage = new HashMap<>();

    @Override
    public Optional<MessageRecord<?>> findByTokenId(String tokenId) {
        return storage.values()
                .stream()
                .filter(r -> r.getTokenId().equals(tokenId))
                .findFirst();
    }

    @Override
    public Optional<MessageRecord<?>> findByJobId(String jobId) {
        return Optional.ofNullable(storage.get(jobId));
    }

    @Override
    public void save(MessageRecord<?> record) {
        storage.put(record.getJobId(), record);
    }

    @Override
    public void delete(MessageRecord<?> record) {
        storage.remove(record.getJobId());
    }

    @Override
    public void update(MessageRecord<?> record) {
        storage.put(record.getJobId(), record);
    }
}
