package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.suggestion.SuggestionTopicStat;

import java.util.Optional;
import java.util.UUID;

public interface SuggestionTopicStatRepository {

    /** The accumulated dismissal stat for a (user, dedup topic), if any has been recorded. */
    Optional<SuggestionTopicStat> find(UUID userId, String dedupKey);

    SuggestionTopicStat save(SuggestionTopicStat stat);
}
