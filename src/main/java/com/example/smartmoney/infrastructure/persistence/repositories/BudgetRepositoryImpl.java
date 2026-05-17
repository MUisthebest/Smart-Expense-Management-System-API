package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.infrastructure.persistence.entities.BudgetEntity;
import com.example.smartmoney.infrastructure.persistence.mappers.BudgetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class BudgetRepositoryImpl implements BudgetRepository {
    private final JpaBudgetRepository jpaBudgetRepository;
    private final BudgetMapper budgetMapper;

    @Override
    public Budget save(Budget budget) {
        BudgetEntity entity = budgetMapper.toEntity(budget);
        BudgetEntity savedEntity = jpaBudgetRepository.save(entity);
        return budgetMapper.toDomain(savedEntity);
    }

    @Override
    public Optional<Budget> findById(UUID id) {
        return jpaBudgetRepository.findById(id)
                .map(budgetMapper::toDomain);
    }

    @Override
    public Optional<Budget> findByUserIdCategoryMonthYear(UUID userId, Category category, Integer month, Integer year) {
        return jpaBudgetRepository.findByUserIdAndCategoryAndMonthAndYear(userId, category, month, year)
                .map(budgetMapper::toDomain);
    }

    @Override
    public List<Budget> findByUserIdMonthYear(UUID userId, Integer month, Integer year) {
        return jpaBudgetRepository.findByUserIdAndMonthAndYear(userId, month, year).stream()
                .map(budgetMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<Budget> findByUserId(UUID userId) {
        return jpaBudgetRepository.findByUserId(userId).stream()
                .map(budgetMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(UUID id) {
        jpaBudgetRepository.deleteById(id);
    }
}
