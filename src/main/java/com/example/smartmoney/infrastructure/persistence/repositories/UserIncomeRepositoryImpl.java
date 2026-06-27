package com.example.smartmoney.infrastructure.persistence.repositories;

/*
import com.example.smartmoney.domain.models.user.UserIncome;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
import com.example.smartmoney.infrastructure.persistence.mappers.UserIncomeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserIncomeRepositoryImpl implements UserIncomeRepository {

    private final JpaUserIncomeRepository jpaUserIncomeRepository;
    private final UserIncomeMapper userIncomeMapper;

    @Override
    public UserIncome save(UserIncome userIncome) {
        var entity = userIncomeMapper.toEntity(userIncome);
        var saved = jpaUserIncomeRepository.save(entity);
        return userIncomeMapper.toDomain(saved);
    }

    @Override
    public Optional<UserIncome> findByUserId(UUID userId) {
        return jpaUserIncomeRepository.findByUserId(userId)
                .map(userIncomeMapper::toDomain);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        jpaUserIncomeRepository.deleteByUserId(userId);
    }
}
*/
