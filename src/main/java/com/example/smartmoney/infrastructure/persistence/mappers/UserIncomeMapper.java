package com.example.smartmoney.infrastructure.persistence.mappers;

/*
import com.example.smartmoney.domain.models.user.UserIncome;
import com.example.smartmoney.infrastructure.persistence.entities.UserIncomeEntity;
import org.springframework.stereotype.Component;

@Component
public class UserIncomeMapper {

    public UserIncome toDomain(UserIncomeEntity entity) {
        if (entity == null) {
            return null;
        }

        return new UserIncome(
                entity.getId(),
                entity.getUserId(),
                entity.getNetIncome(),
                entity.getUsableIncome(),
                entity.getSafeSpending(),
                entity.getCurrency(),
                entity.getCalculationNote(),
                entity.getAutoInvestSurplus() != null && entity.getAutoInvestSurplus(),
                entity.getLastCalculatedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    public UserIncomeEntity toEntity(UserIncome domain) {
        UserIncomeEntity entity = new UserIncomeEntity();
        entity.setId(domain.getId());
        entity.setUserId(domain.getUserId());
        entity.setNetIncome(domain.getNetIncome());
        entity.setUsableIncome(domain.getUsableIncome());
        entity.setSafeSpending(domain.getSafeSpending());
        entity.setCurrency(domain.getCurrency());
        entity.setCalculationNote(domain.getCalculationNote());
        entity.setAutoInvestSurplus(domain.getAutoInvestSurplus());
        entity.setLastCalculatedAt(domain.getLastCalculatedAt());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setUpdatedAt(domain.getUpdatedAt());
        return entity;
    }
}
*/
