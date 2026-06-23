package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.infrastructure.persistence.entities.TransactionEntity;
import com.example.smartmoney.infrastructure.persistence.mappers.TransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.Comparator;
import java.util.Collections;
import java.util.stream.Stream;

@Repository
@RequiredArgsConstructor
public class TransactionRepositoryImpl implements TransactionRepository {
    private final JpaTransactionRepository jpaTransactionRepository;
    private final TransactionMapper transactionMapper;

    @Override
    public Transaction save(Transaction transaction) {
        TransactionEntity entity = transactionMapper.toEntity(transaction);
        TransactionEntity savedEntity = jpaTransactionRepository.save(entity);
        return transactionMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Transaction> findById(UUID id) {
        return jpaTransactionRepository.findById(id)
                .map(transactionMapper::toDomain);
    }

    @Override
    public List<Transaction> findByUserId(UUID userId) {
        return jpaTransactionRepository.findByUserId(userId).stream()
                .map(transactionMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Transaction> findByUserIdSorted(UUID userId, String sortBy, String sortOrder, int page, int size) {
        // Get all transactions for user
        List<Transaction> allTransactions = findByUserId(userId);
        
        // Sort
        sortTransactions(allTransactions, sortBy, sortOrder);
        
        // Apply pagination
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, allTransactions.size());
        
        if (fromIndex < allTransactions.size()) {
            return allTransactions.subList(fromIndex, toIndex);
        } else {
            return List.of();
        }
    }

    @Override
    public List<Transaction> findByUserIdWithFilters(
            UUID userId,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Category category,
            TransactionType type) {
        // Convert enums to String for native query
        String categoryStr = category != null ? category.name() : null;
        String typeStr = type != null ? type.name() : null;
        
        return jpaTransactionRepository.findByUserIdWithFilters(
                userId, minAmount, maxAmount, startDate, endDate, categoryStr, typeStr
        ).stream()
                .map(transactionMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Transaction> findByUserIdWithFilters(
            UUID userId,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Category category,
            TransactionType type,
            int page,
            int size) {
        // Convert enums to String for native query
        String categoryStr = category != null ? category.name() : null;
        String typeStr = type != null ? type.name() : null;
        int offset = page * size;
        
        return jpaTransactionRepository.findByUserIdWithFiltersPaginatedUnsorted(
                userId, minAmount, maxAmount, startDate, endDate, categoryStr, typeStr, size, offset
        ).stream()
                .map(transactionMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Transaction> findByUserIdWithFilters(
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
            int size) {
        // Convert enums to String for native query
        String categoryStr = category != null ? category.name() : null;
        String typeStr = type != null ? type.name() : null;
        
        // Get all filtered transactions (without pagination first)
        List<TransactionEntity> entities = jpaTransactionRepository.findByUserIdWithFiltersUnsorted(
                userId, minAmount, maxAmount, startDate, endDate, categoryStr, typeStr
        );
        
        // Convert to domain objects
        List<Transaction> transactions = entities.stream()
                .map(transactionMapper::toDomain)
                .collect(Collectors.toList());
        
        // Sort in Java
        sortTransactions(transactions, sortBy, sortOrder);
        
        // Apply pagination
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, transactions.size());
        
        if (fromIndex < transactions.size()) {
            transactions = transactions.subList(fromIndex, toIndex);
        } else {
            transactions = List.of();
        }
        
        return transactions;
    }
    
    private void sortTransactions(List<Transaction> transactions, String sortBy, String sortOrder) {
        boolean ascending = sortOrder.equalsIgnoreCase("ASC");
        
        transactions.sort((t1, t2) -> {
            int comparison = 0;
            
            switch (sortBy.toLowerCase()) {
                case "amount":
                    comparison = t1.getAmount().getValue().compareTo(t2.getAmount().getValue());
                    break;
                case "category":
                    comparison = t1.getCategory().toString().compareTo(t2.getCategory().toString());
                    break;
                case "type":
                    comparison = t1.getType().toString().compareTo(t2.getType().toString());
                    break;
                case "description":
                    comparison = (t1.getDescription() != null ? t1.getDescription() : "")
                            .compareTo(t2.getDescription() != null ? t2.getDescription() : "");
                    break;
                case "date":
                default:
                    comparison = t1.getDate().compareTo(t2.getDate());
                    break;
            }
            
            return ascending ? comparison : -comparison;
        });
    }

    @Override
    public long countByUserIdWithFilters(
            UUID userId,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Category category,
            TransactionType type) {
        // Convert enums to String for native query
        String categoryStr = category != null ? category.name() : null;
        String typeStr = type != null ? type.name() : null;
        
        return jpaTransactionRepository.countByUserIdWithFilters(
                userId, minAmount, maxAmount, startDate, endDate, categoryStr, typeStr
        );
    }

    @Override
    public void deleteById(UUID id) {
        jpaTransactionRepository.deleteById(id);
    }

    @Override
    public List<Object[]> getTransactionProportionByCategory(UUID userId, int year, int month) {
        return jpaTransactionRepository.getTransactionProportionByCategory(userId, year, month);
    }

    @Override
    public List<Object[]> getTransactionsByMonth(UUID userId, int year, int month) {
        return jpaTransactionRepository.getTransactionProportionByTypeForYear(userId, year, month);
    }

    @Override
    public List<Object[]> getMonthlyTotals(UUID userId, int year, int month) {
        return jpaTransactionRepository.getMonthlyTotals(userId, year, month);
    }

    @Override
    public BigDecimal getTotalIncomeBetween(UUID userId, LocalDate start, LocalDate end) {
        return jpaTransactionRepository.getTotalIncomeBetween(userId, start, end);
    }

    @Override
    public BigDecimal getTotalExpenseBetween(UUID userId, LocalDate start, LocalDate end) {
        return jpaTransactionRepository.getTotalExpenseBetween(userId, start, end);
    }

    @Override
    public List<Object[]> getExpenseByCategoryBetween(UUID userId, LocalDate start, LocalDate end) {
        return jpaTransactionRepository.getExpenseByCategoryBetween(userId, start, end);
    }
    
    public List<Object[]> getDailyTotals(UUID userId, int year, int month) {
        return jpaTransactionRepository.getDailyTotals(userId, year, month);
    }

    @Override
    public List<Object[]> getTransactionsByYear(UUID userId, int year) {
        return jpaTransactionRepository.getTransactionsByYear(userId, year);
    }

    @Override
    public List<Object[]> getTransactionProportionByCategoryForYear(UUID userId, int year) {
        return jpaTransactionRepository.getTransactionProportionByCategoryForYear(userId, year);
    }

    @Override
    public List<Object[]> getYearlyTotals(UUID userId, int year) {
        return jpaTransactionRepository.getYearlyTotals(userId, year);
    }
}
