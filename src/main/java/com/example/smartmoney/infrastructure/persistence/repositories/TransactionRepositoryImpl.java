package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.infrastructure.persistence.entities.TransactionEntity;
import com.example.smartmoney.infrastructure.persistence.mappers.TransactionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
    public Optional<Transaction> findById(Long id) {
        return jpaTransactionRepository.findById(id)
                .map(transactionMapper::toDomain);
    }

    @Override
    public List<Transaction> findByUserId(Long userId) {
        return jpaTransactionRepository.findByUserId(userId).stream()
                .map(transactionMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(Long id) {
        jpaTransactionRepository.deleteById(id);
    }
}
