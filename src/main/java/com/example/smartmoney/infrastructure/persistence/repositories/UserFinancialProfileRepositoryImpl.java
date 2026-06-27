package com.example.smartmoney.infrastructure.persistence.repositories;

// Disabled: user_financial_profile table removed.
/*
import com.example.smartmoney.domain.models.userprofile.UserFinancialProfile;
import com.example.smartmoney.domain.repositories.UserFinancialProfileRepository;
import com.example.smartmoney.infrastructure.persistence.entities.UserFinancialProfileEntity;
import com.example.smartmoney.infrastructure.persistence.repositories.JpaUserFinancialProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserFinancialProfileRepositoryImpl implements UserFinancialProfileRepository {

    private final JpaUserFinancialProfileRepository jpaRepository;

    @Override
    public Optional<UserFinancialProfile> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId)
                .map(this::toDomain);
    }

    @Override
    public boolean existsByUserId(UUID userId) {
        return jpaRepository.existsByUserId(userId);
    }

    @Override
    public UserFinancialProfile save(UserFinancialProfile profile) {
        UserFinancialProfileEntity entity = toEntity(profile);
        UserFinancialProfileEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public void deleteByUserId(UUID userId) {
        jpaRepository.deleteByUserId(userId);
    }

    private UserFinancialProfile toDomain(UserFinancialProfileEntity entity) {
        return UserFinancialProfile.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .role(entity.getRole())
                .livingStatus(entity.getLivingStatus())
                .incomeLevel(entity.getIncomeLevel())
                .transportMode(entity.getTransportMode())
                .spendingStyle(entity.getSpendingStyle())
                .workStyle(entity.getWorkStyle())
                .familyStatus(entity.getFamilyStatus())
                .studyIntensity(entity.getStudyIntensity())
                .healthNeed(entity.getHealthNeed())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private UserFinancialProfileEntity toEntity(UserFinancialProfile profile) {
        UserFinancialProfileEntity entity = new UserFinancialProfileEntity();

        entity.setId(profile.getId());
        entity.setUserId(profile.getUserId());

        entity.setRole(profile.getRole());
        entity.setLivingStatus(profile.getLivingStatus());
        entity.setIncomeLevel(profile.getIncomeLevel());
        entity.setTransportMode(profile.getTransportMode());
        entity.setSpendingStyle(profile.getSpendingStyle());
        entity.setWorkStyle(profile.getWorkStyle());
        entity.setFamilyStatus(profile.getFamilyStatus());
        entity.setStudyIntensity(profile.getStudyIntensity());
        entity.setHealthNeed(profile.getHealthNeed());

        entity.setCreatedAt(profile.getCreatedAt());
        entity.setUpdatedAt(profile.getUpdatedAt());

        return entity;
    }
}
*/