package com.example.smartmoney.infrastructure.persistence.mappers;

import com.example.smartmoney.domain.models.project.ProjectContribution;
import com.example.smartmoney.domain.models.project.valueobjects.ContributionId;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.infrastructure.persistence.entities.ProjectContributionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@Mapper(componentModel = "spring")
public abstract class ProjectContributionMapper {

    @Mapping(target = "id", expression = "java(mapContributionIdToEntity(domain.getId()))")
    @Mapping(target = "projectId", ignore = true)
    @Mapping(target = "userId", expression = "java(mapUserIdToEntity(domain.getUserId()))")
    @Mapping(target = "amount", expression = "java(mapMoneyAmountToEntity(domain.getMoney()))")
    @Mapping(target = "currency", expression = "java(mapMoneyCurrencyToEntity(domain.getMoney()))")
    @Mapping(target = "note", source = "note")
    @Mapping(target = "createdAt", source = "createdAt")
    public abstract ProjectContributionEntity toEntity(ProjectContribution domain);

    public ProjectContribution toDomain(ProjectContributionEntity entity) {
        if (entity == null) {
            return null;
        }

        return new ProjectContribution(
                mapContributionIdToDomain(entity.getId()),
                mapUserIdToDomain(entity.getUserId()),
                mapMoneyToDomain(entity.getAmount(), entity.getCurrency()),
                entity.getNote(),
                entity.getCreatedAt()
        );
    }

    protected UUID mapContributionIdToEntity(ContributionId id) {
        return id != null ? id.value() : null;
    }

    protected ContributionId mapContributionIdToDomain(UUID id) {
        return id != null ? new ContributionId(id) : null;
    }

    protected UUID mapUserIdToEntity(UserId id) {
        return id != null ? id.value() : null;
    }

    protected UserId mapUserIdToDomain(UUID id) {
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

    public ProjectContributionEntity toEntity(ProjectContribution domain, UUID projectId) {
        ProjectContributionEntity e = toEntity(domain);
        e.setProjectId(projectId);
        return e;
    }
}
