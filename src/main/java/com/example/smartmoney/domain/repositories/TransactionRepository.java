package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository {
    Transaction save(Transaction transaction);
    Optional<Transaction> findById(UUID id);
    List<Transaction> findByUserId(UUID userId);
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


    List<Object[]> getTransactionsByMonth(int year, int month );
    List<Object[]> getTransactionProportionByCategory(int year, int month);
}
