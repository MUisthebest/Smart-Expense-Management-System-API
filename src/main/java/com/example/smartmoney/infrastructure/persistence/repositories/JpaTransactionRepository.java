package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.infrastructure.persistence.entities.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
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
           "LIMIT :size OFFSET :offset",
           nativeQuery = true)
    List<TransactionEntity> findByUserIdWithFiltersPaginatedUnsorted(
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
    
    @Query(value = "SELECT * FROM transactions t WHERE t.user_id = CAST(:userId AS uuid) " +
           "AND (CAST(:minAmount AS numeric) IS NULL OR t.amount >= CAST(:minAmount AS numeric)) " +
           "AND (CAST(:maxAmount AS numeric) IS NULL OR t.amount <= CAST(:maxAmount AS numeric)) " +
           "AND (CAST(:startDate AS timestamp) IS NULL OR t.date >= CAST(:startDate AS timestamp)) " +
           "AND (CAST(:endDate AS timestamp) IS NULL OR t.date <= CAST(:endDate AS timestamp)) " +
           "AND (CAST(:category AS varchar) IS NULL OR t.category = CAST(:category AS varchar)) " +
           "AND (CAST(:type AS varchar) IS NULL OR t.type = CAST(:type AS varchar))",
           nativeQuery = true)
    List<TransactionEntity> findByUserIdWithFiltersUnsorted(
        @Param("userId") UUID userId,
        @Param("minAmount") BigDecimal minAmount,
        @Param("maxAmount") BigDecimal maxAmount,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate,
        @Param("category") String category,
        @Param("type") String type
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

    @Query(value = "SELECT t.category, COUNT(*) FROM transactions t WHERE t.user_id = CAST(:userId AS uuid) AND EXTRACT(YEAR FROM t.date) = :year AND EXTRACT(MONTH FROM t.date) = :month GROUP BY t.category", nativeQuery = true)
    List<Object[]> getTransactionProportionByCategory(
            @Param("userId") UUID userId,
            @Param("year") int year,
            @Param("month") int month
    );

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
            ((EXTRACT(DAY FROM t.date)::int - 1) / 7) + 1 AS week_of_month,
            COUNT(*) AS total_rows
        FROM transactions t
        WHERE t.user_id = CAST(:userId AS uuid)
          AND EXTRACT(MONTH FROM t.date) = :month
          AND EXTRACT(YEAR FROM t.date) = :year
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
    List<Object[]> getTransactionProportionByTypeForYear(
            @Param("userId") UUID userId,
            @Param("year") int year,
            @Param("month") int month
    );

    @Query(value = "SELECT " +
            "COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0) AS total_income, " +
            "COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) AS total_expense, " +
            "COUNT(t.id) AS total_transactions " +
            "FROM transactions t " +
            "WHERE t.user_id = CAST(:userId AS uuid) " +
            "AND EXTRACT(YEAR FROM t.date) = :year " +
            "AND EXTRACT(MONTH FROM t.date) = :month",
            nativeQuery = true)
    List<Object[]> getMonthlyTotals(
            @Param("userId") UUID userId,
            @Param("year") int year,
            @Param("month") int month
    );

    @Query(value = "SELECT COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0) " +
            "FROM transactions t " +
            "WHERE t.user_id = CAST(:userId AS uuid) " +
            "AND t.date >= CAST(:start AS timestamp) " +
            "AND t.date <= CAST(:end AS timestamp) " +
            "AND t.type = 'INCOME'",
            nativeQuery = true)
    BigDecimal getTotalIncomeBetween(
            @Param("userId") UUID userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    @Query(value = "SELECT COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) " +
            "FROM transactions t " +
            "WHERE t.user_id = CAST(:userId AS uuid) " +
            "AND t.date >= CAST(:start AS timestamp) " +
            "AND t.date <= CAST(:end AS timestamp) " +
            "AND t.type = 'EXPENSE'",
            nativeQuery = true)
    BigDecimal getTotalExpenseBetween(
            @Param("userId") UUID userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    @Query(value = "SELECT COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0) AS total_income, " +
            "COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) AS total_expense " +
            "FROM transactions t " +
            "WHERE t.user_id = CAST(:userId AS uuid) " +
            "AND t.date >= CAST(:start AS timestamp) " +
            "AND t.date <= CAST(:end AS timestamp)",
            nativeQuery = true)
    List<Object[]> getTotalIncomeAndExpenseBetween(
            @Param("userId") UUID userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    @Query(value = "SELECT COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0) AS total_income, " +
            "COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0) AS total_expense " +
            "FROM transactions t " +
            "WHERE t.user_id = CAST(:userId AS uuid) " +
            "AND EXTRACT(YEAR FROM t.date) = :year",
            nativeQuery = true)
    List<Object[]> getTotalIncomeAndExpenseForYear(
            @Param("userId") UUID userId,
            @Param("year") int year
    );

    @Query(value = """
        SELECT t.category,
            COALESCE(SUM(t.amount), 0)
        FROM transactions t
        WHERE t.user_id = CAST(:userId AS uuid)
        AND t.type = 'EXPENSE'
        AND t.date >= :start
        AND t.date < :end
        GROUP BY t.category
        """, nativeQuery = true)
    List<Object[]> getExpenseByCategoryBetween(
            @Param("userId") UUID userId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );
}
