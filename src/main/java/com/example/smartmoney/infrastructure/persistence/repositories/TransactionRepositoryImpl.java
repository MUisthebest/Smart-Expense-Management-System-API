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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.UUID;

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
        
        return jpaTransactionRepository.findByUserIdWithFiltersPaginated(
                userId, minAmount, maxAmount, startDate, endDate, categoryStr, typeStr, size, offset
        ).stream()
                .map(transactionMapper::toDomain)
                .collect(Collectors.toList());
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
    public List<Object[]> getTransactionProportionByCategory(int year, int month) {
        return jpaTransactionRepository.getTransactionProportionByCategory(year, month);
    }

    @Override
    public List<Object[]> getTransactionsByMonth(int year, int month) {
        return jpaTransactionRepository.getTransactionProportionByTypeForYear(year, month);

    }


}
