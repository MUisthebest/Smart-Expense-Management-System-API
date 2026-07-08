package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.infrastructure.persistence.entities.SuggestionTopicStatEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaSuggestionTopicStatRepository extends JpaRepository<SuggestionTopicStatEntity, UUID> {

    Optional<SuggestionTopicStatEntity> findByUserIdAndDedupKey(UUID userId, String dedupKey);
}
