package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.suggestion.SuggestionTopicStat;
import com.example.smartmoney.domain.repositories.SuggestionTopicStatRepository;
import com.example.smartmoney.infrastructure.persistence.entities.SuggestionTopicStatEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SuggestionTopicStatRepositoryImpl implements SuggestionTopicStatRepository {

    private final JpaSuggestionTopicStatRepository jpa;

    @Override
    public Optional<SuggestionTopicStat> find(UUID userId, String dedupKey) {
        return jpa.findByUserIdAndDedupKey(userId, dedupKey).map(this::toDomain);
    }

    @Override
    public SuggestionTopicStat save(SuggestionTopicStat stat) {
        return toDomain(jpa.save(toEntity(stat)));
    }

    private SuggestionTopicStatEntity toEntity(SuggestionTopicStat domain) {
        SuggestionTopicStatEntity entity = new SuggestionTopicStatEntity();
        entity.setId(domain.getId());
        entity.setUserId(domain.getUserId());
        entity.setDedupKey(domain.getDedupKey());
        entity.setDismissCount(domain.getDismissCount());
        entity.setLastDismissedAt(domain.getLastDismissedAt());
        return entity;
    }

    private SuggestionTopicStat toDomain(SuggestionTopicStatEntity entity) {
        return SuggestionTopicStat.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .dedupKey(entity.getDedupKey())
                .dismissCount(entity.getDismissCount())
                .lastDismissedAt(entity.getLastDismissedAt())
                .build();
    }
}
