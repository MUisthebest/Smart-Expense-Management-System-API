package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.userprofile.UserFinancialProfile;

import java.util.Optional;
import java.util.UUID;

public interface UserFinancialProfileRepository {

    Optional<UserFinancialProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    UserFinancialProfile save(UserFinancialProfile profile);

    void deleteByUserId(UUID userId);
}