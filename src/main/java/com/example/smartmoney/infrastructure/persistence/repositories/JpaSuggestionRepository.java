package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.suggestion.SuggestionStatus;
import com.example.smartmoney.infrastructure.persistence.entities.SuggestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaSuggestionRepository extends JpaRepository<SuggestionEntity, UUID> {

    Optional<SuggestionEntity> findFirstByUserIdAndDedupKeyAndStatus(
            UUID userId, String dedupKey, SuggestionStatus status);

    List<SuggestionEntity> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, SuggestionStatus status);

    /**
     * Count of this user's suggestions created at/after {@code since} whose status is not
     * {@code excludedStatus} — backs the P3-A daily cap. Callers exclude ACCEPTED so that acting
     * on a suggestion (positive engagement) doesn't burn the day's push budget.
     */
    int countByUserIdAndCreatedAtGreaterThanEqualAndStatusNot(
            UUID userId, Instant since, SuggestionStatus excludedStatus);

    /**
     * Conditional, lock-free transition out of PENDING. The WHERE clause makes this
     * idempotent: a second concurrent or repeated call updates 0 rows.
     */
    @Modifying
    @Query("UPDATE SuggestionEntity s SET s.status = :newStatus, s.decidedAt = :decidedAt "
            + "WHERE s.id = :id AND s.userId = :userId AND s.status = "
            + "com.example.smartmoney.domain.models.suggestion.SuggestionStatus.PENDING")
    int markDecidedIfPending(@Param("id") UUID id,
                             @Param("userId") UUID userId,
                             @Param("newStatus") SuggestionStatus newStatus,
                             @Param("decidedAt") Instant decidedAt);
}
