package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.infrastructure.persistence.entities.BudgetEntity;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.springframework.stereotype.Component;

@Component
@Mapper(componentModel = "spring")
public abstract class BudgetMapper {

    // Domain -> Entity
    public abstract BudgetEntity toEntity(Budget domain);

    // Entity -> Domain
    public abstract Budget toDomain(BudgetEntity entity);

    @org.mapstruct.AfterMapping
    protected void afterMappingToEntity(@MappingTarget BudgetEntity entity, Budget domain) {
        if (domain != null && entity != null) {
            entity.setId(domain.getId());
            entity.setUserId(domain.getUserId());
            entity.setCategory(domain.getCategory());
            entity.setAmountLimit(domain.getAmountLimit());
            entity.setSpent(domain.getSpent());
            entity.setMonth(domain.getMonth());
            entity.setYear(domain.getYear());
            entity.setCreatedAt(domain.getCreatedAt());
            entity.setUpdatedAt(domain.getUpdatedAt());
        }
    }

    @org.mapstruct.AfterMapping
    protected void afterMappingToDomain(@MappingTarget Budget domain, BudgetEntity entity) {
        if (domain != null && entity != null) {
            domain.setId(entity.getId());
            domain.setUserId(entity.getUserId());
            domain.setCategory(entity.getCategory());
            domain.setAmountLimit(entity.getAmountLimit());
            domain.setSpent(entity.getSpent());
            domain.setMonth(entity.getMonth());
            domain.setYear(entity.getYear());
            domain.setCreatedAt(entity.getCreatedAt());
            domain.setUpdatedAt(entity.getUpdatedAt());
        }
    }
}
