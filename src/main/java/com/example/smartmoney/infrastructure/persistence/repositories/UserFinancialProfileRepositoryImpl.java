package com.example.smartmoney.infrastructure.persistence.repositories;

import com.example.smartmoney.domain.models.userprofile.UserFinancialProfile;
import com.example.smartmoney.domain.repositories.UserFinancialProfileRepository;
import com.example.smartmoney.infrastructure.persistence.entities.UserFinancialProfileEntity;
import com.example.smartmoney.infrastructure.persistence.mappers.UserFinancialProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserFinancialProfileRepositoryImpl implements UserFinancialProfileRepository {

    private final JpaUserFinancialProfileRepository jpaRepo;
    private final UserFinancialProfileMapper mapper;

    @Override
    public Optional<UserFinancialProfile> findByUserId(UUID userId) {
        return jpaRepo.findByUserId(userId).map(mapper::toDomain);
    }
}