package com.example.smartmoney.domain.services;

import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.exceptions.DomainException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TransactionDomainService {
    private final TransactionRepository transactionRepository;

    public TransactionDomainService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public Transaction createTransaction(Transaction transaction) {
        return transactionRepository.save(transaction);
    }

    public Transaction getTransactionById(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new DomainException("Transaction not found"));
    }

    public List<Transaction> getTransactionsByUserId(UUID userId) {
        return transactionRepository.findByUserId(userId);
    }

    public Transaction updateTransaction(Transaction transaction) {
        if (transaction.getId() == null) {
            throw new DomainException("Transaction ID cannot be null for update");
        }
        if (!transactionRepository.findById(transaction.getId()).isPresent()) {
            throw new DomainException("Transaction not found");
        }
        return transactionRepository.save(transaction);
    }

    public void deleteTransaction(Long id) {
        if (!transactionRepository.findById(id).isPresent()) {
            throw new DomainException("Transaction not found");
        }
        transactionRepository.deleteById(id);
    }
}
