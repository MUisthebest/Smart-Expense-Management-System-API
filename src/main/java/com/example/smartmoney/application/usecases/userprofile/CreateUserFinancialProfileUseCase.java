package com.example.smartmoney.application.usecases.userprofile;

import com.example.smartmoney.application.dtos.requests.userfinancialprofile.CreateUserFinancialProfileRequest;
import com.example.smartmoney.application.dtos.responses.UserFinancialProfileResponse;
import com.example.smartmoney.domain.exceptions.BusinessException;
import com.example.smartmoney.domain.exceptions.CommonErrorCode;
import com.example.smartmoney.infrastructure.persistence.entities.UserFinancialProfileEntity;
import com.example.smartmoney.infrastructure.persistence.repositories.JpaUserFinancialProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class CreateUserFinancialProfileUseCase {

    private final JpaUserFinancialProfileRepository repository;

    public CreateUserFinancialProfileUseCase(JpaUserFinancialProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UserFinancialProfileResponse execute(String userId, CreateUserFinancialProfileRequest request) {
        UUID uid = UUID.fromString(userId);

        if (repository.existsByUserId(uid)) {
            throw new BusinessException(
                    "User financial profile already exists",
                    CommonErrorCode.RESOURCE_ALREADY_EXISTS
            );
        }

        UserFinancialProfileEntity entity = UserFinancialProfileEntity.builder()
                .userId(uid)
                .role(request.getRole())
                .livingStatus(request.getLivingStatus())
                .incomeLevel(request.getIncomeLevel())
                .transportMode(request.getTransportMode())
                .spendingStyle(request.getSpendingStyle())
                .workStyle(request.getWorkStyle())
                .familyStatus(request.getFamilyStatus())
                .studyIntensity(request.getStudyIntensity())
                .healthNeed(request.getHealthNeed())
                .build();

        UserFinancialProfileEntity saved = repository.save(entity);

        return UserFinancialProfileResponse.fromEntity(saved);
    }
}