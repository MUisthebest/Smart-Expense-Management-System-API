package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.infrastructure.persistence.entities.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface JpaTransactionRepository extends JpaRepository<TransactionEntity, UUID> {
    Optional<TransactionEntity> findById(UUID id);
    List<TransactionEntity> findByUserId(UUID userId);
    
    // Using nativeQuery with CAST function (Spring Data JPA compatible)
    @Query(value = "SELECT * FROM transactions t WHERE t.user_id = CAST(:userId AS uuid) " +
           "AND (CAST(:minAmount AS numeric) IS NULL OR t.amount >= CAST(:minAmount AS numeric)) " +
           "AND (CAST(:maxAmount AS numeric) IS NULL OR t.amount <= CAST(:maxAmount AS numeric)) " +
           "AND (CAST(:startDate AS timestamp) IS NULL OR t.date >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) IS NULL OR t.date <= CAST(:endDate AS timestamp)) " +
           "AND (CAST(:category AS varchar) IS NULL OR t.category = CAST(:category AS varchar)) " +
           "AND (CAST(:type AS varchar) IS NULL OR t.type = CAST(:type AS varchar))",
           nativeQuery = true)
    List<TransactionEntity> findByUserIdWithFilters(
        @Param("userId") UUID userId,
        @Param("minAmount") BigDecimal minAmount,
        @Param("maxAmount") BigDecimal maxAmount,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("category") String category,
        @Param("type") String type
    );
    
    @Query(value = "SELECT * FROM transactions t WHERE t.user_id = CAST(:userId AS uuid) " +
           "AND (CAST(:minAmount AS numeric) IS NULL OR t.amount >= CAST(:minAmount AS numeric)) " +
           "AND (CAST(:maxAmount AS numeric) IS NULL OR t.amount <= CAST(:maxAmount AS numeric)) " +
           "AND (CAST(:startDate AS timestamp) IS NULL OR t.date >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) IS NULL OR t.date <= CAST(:endDate AS timestamp)) " +
           "AND (CAST(:category AS varchar) IS NULL OR t.category = CAST(:category AS varchar)) " +
           "AND (CAST(:type AS varchar) IS NULL OR t.type = CAST(:type AS varchar)) " +
           "ORDER BY t.date DESC LIMIT :size OFFSET :offset",
           nativeQuery = true)
    List<TransactionEntity> findByUserIdWithFiltersPaginated(
        @Param("userId") UUID userId,
        @Param("minAmount") BigDecimal minAmount,
        @Param("maxAmount") BigDecimal maxAmount,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("category") String category,
        @Param("type") String type,
        @Param("size") int size,
        @Param("offset") int offset
    );
    
    @Query(value = "SELECT COUNT(*) FROM transactions t WHERE t.user_id = CAST(:userId AS uuid) " +
           "AND (CAST(:minAmount AS numeric) IS NULL OR t.amount >= CAST(:minAmount AS numeric)) " +
           "AND (CAST(:maxAmount AS numeric) IS NULL OR t.amount <= CAST(:maxAmount AS numeric)) " +
           "AND (CAST(:startDate AS timestamp) IS NULL OR t.date >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) IS NULL OR t.date <= CAST(:endDate AS timestamp)) " +
           "AND (CAST(:category AS varchar) IS NULL OR t.category = CAST(:category AS varchar)) " +
           "AND (CAST(:type AS varchar) IS NULL OR t.type = CAST(:type AS varchar))",
           nativeQuery = true)
    long countByUserIdWithFilters(
        @Param("userId") UUID userId,
        @Param("minAmount") BigDecimal minAmount,
        @Param("maxAmount") BigDecimal maxAmount,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("category") String category,
        @Param("type") String type
    );
    
    void deleteById(UUID id);
}
