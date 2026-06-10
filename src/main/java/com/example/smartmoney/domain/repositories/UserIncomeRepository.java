package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.user.UserIncome;

import java.util.Optional;
import java.util.UUID;

public interface UserIncomeRepository {
    UserIncome save(UserIncome userIncome);
    Optional<UserIncome> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
}
