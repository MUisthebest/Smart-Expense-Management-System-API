package com.example.smartmoney.application.usecases.userprofile;

/*
import com.example.smartmoney.application.dtos.requests.userfinancialprofile.UpdateUserFinancialProfileRequest;
import com.example.smartmoney.application.dtos.responses.UserFinancialProfileResponse;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.infrastructure.persistence.entities.UserFinancialProfileEntity;
import com.example.smartmoney.infrastructure.persistence.repositories.JpaUserFinancialProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateUserFinancialProfileUseCase {

    private final JpaUserFinancialProfileRepository repository;

    public UpdateUserFinancialProfileUseCase(JpaUserFinancialProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UserFinancialProfileResponse execute(String userId, UpdateUserFinancialProfileRequest request) {

        UUID uid = UUID.fromString(userId);

        UserFinancialProfileEntity entity = repository.findByUserId(uid)
                .orElseThrow(() -> new RuntimeException("User financial profile not found"));

        entity.setRole(request.getRole());
        entity.setLivingStatus(request.getLivingStatus());
        entity.setIncomeLevel(request.getIncomeLevel());
        entity.setTransportMode(request.getTransportMode());
        entity.setSpendingStyle(request.getSpendingStyle());
        entity.setWorkStyle(request.getWorkStyle());
        entity.setFamilyStatus(request.getFamilyStatus());
        entity.setStudyIntensity(request.getStudyIntensity());
        entity.setHealthNeed(request.getHealthNeed());

        UserFinancialProfileEntity saved = repository.save(entity);

        return UserFinancialProfileResponse.fromEntity(saved);
    }
}
*/
