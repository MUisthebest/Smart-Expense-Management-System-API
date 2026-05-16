package com.example.smartmoney.infrastructure.persistence.repositories;

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

    @Query(value = "SELECT t.category, COUNT(*) FROM transactions t WHERE EXTRACT(YEAR FROM t.created_at) = :year AND EXTRACT(MONTH FROM t.created_at) =:month GROUP BY t.category", nativeQuery = true)
    List<Object[]> getTransactionProportionByCategory(int year, int month);

    @Query(value = """
    WITH weeks AS (
        SELECT generate_series(1, 5) AS week_of_month
    ),
    types AS (
        SELECT unnest(ARRAY['INCOME', 'EXPENSE']) AS type
    ),
    stats AS (
        SELECT
            t.type,
            ((EXTRACT(DAY FROM t.created_at)::int - 1) / 7) + 1 AS week_of_month,
            COUNT(*) AS total_rows
        FROM transactions t
        WHERE EXTRACT(MONTH FROM t.created_at) = :month
          AND EXTRACT(YEAR FROM t.created_at) = :year
        GROUP BY t.type, week_of_month
    )
    SELECT
        types.type,
        weeks.week_of_month,
        COALESCE(stats.total_rows, 0) AS total_rows
    FROM weeks
    CROSS JOIN types
    LEFT JOIN stats
      ON stats.week_of_month = weeks.week_of_month
     AND stats.type = types.type
    ORDER BY weeks.week_of_month, types.type
    """, nativeQuery = true)
    List<Object[]> getTransactionProportionByTypeForYear(int year, int month);
}
