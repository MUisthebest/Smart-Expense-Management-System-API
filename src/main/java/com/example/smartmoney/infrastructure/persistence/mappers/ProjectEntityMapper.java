package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@Mapper(componentModel = "spring")
public abstract class ProjectEntityMapper {

    // Domain -> Entity
    @Mapping(target = "id", expression = "java(mapProjectIdToEntity(domain.getId()))")
    @Mapping(target = "ownerId", expression = "java(mapUserIdToEntity(domain.getOwnerId()))")
    @Mapping(target = "targetAmount", expression = "java(mapMoneyAmountToEntity(domain.getTargetAmount()))")
    @Mapping(target = "currency", expression = "java(mapMoneyCurrencyToEntity(domain.getTargetAmount()))")
    public abstract ProjectEntity toEntity(Project domain);

    // Update existing entity (không đụng createdAt)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "ownerId", expression = "java(mapUserIdToEntity(domain.getOwnerId()))")
    @Mapping(target = "targetAmount", expression = "java(mapMoneyAmountToEntity(domain.getTargetAmount()))")
    @Mapping(target = "currency", expression = "java(mapMoneyCurrencyToEntity(domain.getTargetAmount()))")
    public abstract void updateEntity(@MappingTarget ProjectEntity entity, Project domain);

    // ===== Helper methods =====
    protected UUID mapProjectIdToEntity(ProjectId id) {
        return id != null ? id.value() : null;
    }

    protected UUID mapUserIdToEntity(UserId id) {
        return id != null ? id.value() : null;
    }

    protected BigDecimal mapMoneyAmountToEntity(Money money) {
        return money != null ? money.amount() : null;
    }

    protected String mapMoneyCurrencyToEntity(Money money) {
        return money != null ? money.currency() : null;
    }
}
