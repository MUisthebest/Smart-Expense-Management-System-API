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
public interface JpaTransactionRepository extends JpaRepository<TransactionEntity, Long> {
    Optional<TransactionEntity> findById(Long id);
    List<TransactionEntity> findByUserId(UUID userId);
    
    @Query("SELECT t FROM TransactionEntity t WHERE t.userId = :userId " +
           "AND (:minAmount IS NULL OR t.amount >= :minAmount) " +
           "AND (:maxAmount IS NULL OR t.amount <= :maxAmount) " +
           "AND (:startDate IS NULL OR t.date >= :startDate) " +
           "AND (:endDate IS NULL OR t.date <= :endDate) " +
           "AND (:category IS NULL OR t.category = :category) " +
           "AND (:type IS NULL OR t.type = :type)")
    List<TransactionEntity> findByUserIdWithFilters(
        @Param("userId") UUID userId,
        @Param("minAmount") BigDecimal minAmount,
        @Param("maxAmount") BigDecimal maxAmount,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("category") Category category,
        @Param("type") TransactionType type
    );
    
    void deleteById(Long id);
}
