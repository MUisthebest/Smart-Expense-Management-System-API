package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.transaction.Transaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository {
    Transaction save(Transaction transaction);
    Optional<Transaction> findById(Long id);
    List<Transaction> findByUserId(UUID userId);
    void deleteById(Long id);
}
