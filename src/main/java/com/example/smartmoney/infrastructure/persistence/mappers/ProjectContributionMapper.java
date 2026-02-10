package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.project.ProjectContribution;
import com.example.smartmoney.domain.models.project.valueobjects.ContributionId;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectContributionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@Mapper(componentModel = "spring")
public abstract class ProjectContributionMapper {

    public abstract ProjectContributionEntity toEntity(ProjectContribution domain);

    @Mapping(target = "id", expression = "java(mapContributionIdToDomain(entity.getId()))")
    @Mapping(target = "userId", expression = "java(mapUserIdToDomain(entity.getUserId()))")
    @Mapping(target = "money", expression = "java(mapMoneyToDomain(entity.getAmount(), entity.getCurrency()))")
    public abstract ProjectContribution toDomain(ProjectContributionEntity entity);

    protected UUID mapContributionIdToEntity(ContributionId id) {
        return id != null ? id.value() : null;
    }

    protected ContributionId mapContributionIdToDomain(UUID id) {
        return id != null ? new ContributionId(id) : null;
    }

    protected Long mapUserIdToEntity(UserId id) {
        return id != null ? id.value() : null;
    }

    protected UserId mapUserIdToDomain(Long id) {
        return id != null ? new UserId(id) : null;
    }

    protected BigDecimal mapMoneyAmountToEntity(Money money) {
        return money != null ? money.amount() : null;
    }

    protected String mapMoneyCurrencyToEntity(Money money) {
        return money != null ? money.currency() : null;
    }

    protected Money mapMoneyToDomain(BigDecimal amount, String currency) {
        if (amount == null || currency == null) return null;
        return new Money(amount, currency);
    }

    @org.mapstruct.AfterMapping
    protected void afterMappingToEntity(@MappingTarget ProjectContributionEntity entity, ProjectContribution domain) {
        if (entity != null && domain != null) {
            entity.setId(mapContributionIdToEntity(domain.getId()));
            entity.setUserId(mapUserIdToEntity(domain.getUserId()));
            entity.setAmount(mapMoneyAmountToEntity(domain.getMoney()));
            entity.setCurrency(mapMoneyCurrencyToEntity(domain.getMoney()));
            entity.setContributionDate(domain.getContributionDate());
            entity.setNote(domain.getNote());
            entity.setCreatedAt(domain.getCreatedAt());
        }
    }

    public ProjectContributionEntity toEntity(ProjectContribution domain, UUID projectId) {
        ProjectContributionEntity e = toEntity(domain);
        e.setProjectId(projectId);
        return e;
    }
}
