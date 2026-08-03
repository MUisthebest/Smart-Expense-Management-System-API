package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository {
    Transaction save(Transaction transaction);
    Optional<Transaction> findById(UUID id);
    List<Transaction> findByUserId(UUID userId);

    /**
     * Every ledger row carrying this {@code projectId} — the PROJECT_CONTRIBUTION expenses written by
     * press-to-contribute and any PROJECT_REFUND income written back on cancellation. Used by
     * {@code ProjectRefundService} to net the two, so a refund is computed from what actually hit the
     * ledger rather than from {@code sum(contributions)} (which also includes settlement auto-savings
     * that never wrote a row).
     */
    List<Transaction> findByProjectId(UUID projectId);

    List<Transaction> findByUserIdSorted(UUID userId, String sortBy, String sortOrder, int page, int size);
    List<Transaction> findByUserIdWithFilters(
        UUID userId,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Category category,
        TransactionType type
    );
    List<Transaction> findByUserIdWithFilters(
        UUID userId,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Category category,
        TransactionType type,
        int page,
        int size
    );
    List<Transaction> findByUserIdWithFilters(
        UUID userId,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Category category,
        TransactionType type,
        String sortBy,
        String sortOrder,
        int page,
        int size
    );
    long countByUserIdWithFilters(
        UUID userId,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        LocalDateTime startDate,
        LocalDateTime endDate,
        Category category,
        TransactionType type
    );
    void deleteById(UUID id);


    List<Object[]> getTransactionsByMonth(UUID userId, int year, int month );
    List<Object[]> getTransactionProportionByCategory(UUID userId, int year, int month);
    List<Object[]> getMonthlyTotals(UUID userId, int year, int month);

    BigDecimal getTotalIncomeBetween(
            UUID userId,
            LocalDate start,
            LocalDate end
    );

    BigDecimal getTotalExpenseBetween(
            UUID userId,
            LocalDate start,
            LocalDate end
    );

    List<Object[]> getExpenseByCategoryBetween(
            UUID userId,
            LocalDate start,
            LocalDate end
    );

    public List<Object[]> getDailyTotals(UUID userId, int year, int month);

    public List<Object[]> getTransactionsByYear(UUID userId, int year);

    public List<Object[]> getTransactionProportionByCategoryForYear(UUID userId, int year);

     public List<Object[]> getYearlyTotals(UUID userId, int year);
}
