package com.example.smartmoney.domain.repositories;

import com.example.smartmoney.domain.models.userprofile.UserFinancialProfile; // domain class
import java.util.Optional;
import java.util.UUID;

public interface UserFinancialProfileRepository {
    Optional<UserFinancialProfile> findByUserId(UUID userId);
}