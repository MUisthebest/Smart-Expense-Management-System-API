package com.example.smartmoney.application.usecases.userprofile;

import com.example.smartmoney.application.dtos.requests.userfinancialprofile.UpdateUserFinancialProfileRequest;
import com.example.smartmoney.application.dtos.responses.UserFinancialProfileResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.exceptions.EntityNotFoundException;
import com.example.smartmoney.domain.models.userprofile.UserFinancialProfile;
import com.example.smartmoney.domain.repositories.UserFinancialProfileRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UpdateUserFinancialProfileUseCase {

    private final UserFinancialProfileRepository userFinancialProfileRepository;
    private final UserRepository userRepository;

    public UpdateUserFinancialProfileUseCase(
            UserFinancialProfileRepository userFinancialProfileRepository,
            UserRepository userRepository
    ) {
        this.userFinancialProfileRepository = userFinancialProfileRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public UserFinancialProfileResponse execute(String userId, UpdateUserFinancialProfileRequest request) {
        UUID uid = UUID.fromString(userId);

        UserFinancialProfile current = userFinancialProfileRepository.findByUserId(uid)
                .orElseThrow(() -> new EntityNotFoundException("UserFinancialProfile", userId));

        current.update(
                request.getRole(),
                request.getLivingStatus(),
                request.getIncomeLevel(),
                request.getTransportMode(),
                request.getSpendingStyle(),
                request.getWorkStyle(),
                request.getFamilyStatus(),
                request.getStudyIntensity(),
                request.getHealthNeed()
        );

        UserFinancialProfile saved = userFinancialProfileRepository.save(current);

        userRepository.markFinancialSetupCompleted(uid, true);

        return UserFinancialProfileResponse.fromDomain(saved);
    }
}