package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.infrastructure.persistence.entities.UserFinancialProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaUserFinancialProfileRepository
        extends JpaRepository<UserFinancialProfileEntity, UUID> {

        Optional<UserFinancialProfileEntity> findByUserId(UUID userId);

        boolean existsByUserId(UUID userId);

        void deleteByUserId(UUID userId);
}