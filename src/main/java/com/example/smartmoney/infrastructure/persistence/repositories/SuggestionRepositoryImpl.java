package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.suggestion.Suggestion;
import com.example.smartmoney.domain.models.suggestion.SuggestionStatus;
import com.example.smartmoney.domain.repositories.SuggestionRepository;
import com.example.smartmoney.infrastructure.persistence.entities.SuggestionEntity;
import com.example.smartmoney.infrastructure.persistence.mappers.SuggestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SuggestionRepositoryImpl implements SuggestionRepository {

    private final JpaSuggestionRepository jpaSuggestionRepository;
    private final SuggestionMapper suggestionMapper;

    @Override
    public Suggestion save(Suggestion suggestion) {
        SuggestionEntity entity = suggestionMapper.toEntity(suggestion);
        SuggestionEntity saved = jpaSuggestionRepository.save(entity);
        return suggestionMapper.toDomain(saved);
    }

    @Override
    public Optional<Suggestion> findById(UUID id) {
        return jpaSuggestionRepository.findById(id).map(suggestionMapper::toDomain);
    }

    @Override
    public Optional<Suggestion> findOpenByUserIdAndDedupKey(UUID userId, String dedupKey) {
        return jpaSuggestionRepository
                .findFirstByUserIdAndDedupKeyAndStatus(userId, dedupKey, SuggestionStatus.PENDING)
                .map(suggestionMapper::toDomain);
    }

    @Override
    public List<Suggestion> findByUserIdAndStatus(UUID userId, SuggestionStatus status) {
        return jpaSuggestionRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, status).stream()
                .map(suggestionMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public int countByUserIdAndCreatedAtAfter(UUID userId, Instant since) {
        return jpaSuggestionRepository.countByUserIdAndCreatedAtGreaterThanEqual(userId, since);
    }

    @Override
    public int markDecidedIfPending(UUID id, UUID userId, SuggestionStatus newStatus, Instant decidedAt) {
        return jpaSuggestionRepository.markDecidedIfPending(id, userId, newStatus, decidedAt);
    }
}
