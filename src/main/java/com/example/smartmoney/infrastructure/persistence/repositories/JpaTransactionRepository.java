package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.infrastructure.persistence.entities.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface JpaTransactionRepository extends JpaRepository<TransactionEntity, Long> {
    Optional<TransactionEntity> findById(Long id);
    List<TransactionEntity> findByUserId(UUID userId);
    void deleteById(Long id);
}
