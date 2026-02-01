package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.infrastructure.persistence.entities.TransactionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.stereotype.Component;

@Component
@Mapper(componentModel = "spring")
public abstract class TransactionMapper {

    // Domain -> Entity
    @Mapping(target = "amount", expression = "java(mapAmountToEntity(domain.getAmount()))")
    public abstract TransactionEntity toEntity(Transaction domain);

    // Entity -> Domain
    @Mapping(target = "amount", expression = "java(mapAmountToDomain(entity.getAmount()))")
    public abstract Transaction toDomain(TransactionEntity entity);

    // Helper methods
    protected java.math.BigDecimal mapAmountToEntity(Amount amount) {
        return amount != null ? amount.getValue() : null;
    }

    protected Amount mapAmountToDomain(java.math.BigDecimal amount) {
        return amount != null ? new Amount(amount) : null;
    }

    @org.mapstruct.AfterMapping
    protected void afterMappingToDomain(@MappingTarget Transaction domain, TransactionEntity entity) {
        if (domain != null && entity != null) {
            domain.setId(entity.getId());
            domain.setUserId(entity.getUserId());
            domain.setType(entity.getType());
            domain.setCategory(entity.getCategory());
            domain.setDescription(entity.getDescription());
            domain.setDate(entity.getDate());
            domain.setCreatedAt(entity.getCreatedAt());
            domain.setUpdatedAt(entity.getUpdatedAt());
        }
    }

    @org.mapstruct.AfterMapping
    protected void afterMappingToEntity(@MappingTarget TransactionEntity entity, Transaction domain) {
        if (entity != null && domain != null) {
            entity.setId(domain.getId());
            entity.setUserId(domain.getUserId());
            entity.setType(domain.getType());
            entity.setCategory(domain.getCategory());
            entity.setDescription(domain.getDescription());
            entity.setDate(domain.getDate());
            entity.setCreatedAt(domain.getCreatedAt());
            entity.setUpdatedAt(domain.getUpdatedAt());
        }
    }
}
