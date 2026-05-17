package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.infrastructure.persistence.entities.BudgetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaBudgetRepository extends JpaRepository<BudgetEntity, UUID> {
    Optional<BudgetEntity> findByUserIdAndCategoryAndMonthAndYear(
            UUID userId, Category category, Integer month, Integer year);
    
    List<BudgetEntity> findByUserIdAndMonthAndYear(UUID userId, Integer month, Integer year);
    
    List<BudgetEntity> findByUserId(UUID userId);
}
