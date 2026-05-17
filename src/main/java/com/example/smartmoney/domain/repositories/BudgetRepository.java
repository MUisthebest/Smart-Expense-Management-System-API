package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.transaction.enums.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository {
    Budget save(Budget budget);
    Optional<Budget> findById(UUID id);
    Optional<Budget> findByUserIdCategoryMonthYear(UUID userId, Category category, Integer month, Integer year);
    List<Budget> findByUserIdMonthYear(UUID userId, Integer month, Integer year);
    List<Budget> findByUserId(UUID userId);
    void deleteById(UUID id);
}
