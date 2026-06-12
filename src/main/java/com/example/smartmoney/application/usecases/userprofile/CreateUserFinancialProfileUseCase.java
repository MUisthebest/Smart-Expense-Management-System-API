package com.example.smartmoney.application.usecases.userprofile;

import com.example.smartmoney.application.dtos.requests.userfinancialprofile.CreateUserFinancialProfileRequest;
import com.example.smartmoney.application.dtos.responses.UserFinancialProfileResponse;
import com.example.smartmoney.domain.exceptions.BusinessException;
import com.example.smartmoney.domain.exceptions.CommonErrorCode;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.userprofile.UserFinancialProfile;
import com.example.smartmoney.domain.repositories.UserFinancialProfileRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateUserFinancialProfileUseCase {

    private final UserFinancialProfileRepository userFinancialProfileRepository;
    private final UserRepository userRepository;

    @Transactional
    public UserFinancialProfileResponse execute(String userId, CreateUserFinancialProfileRequest request) {
        UUID uid = UUID.fromString(userId);

        userRepository.findById(uid)
                .orElseThrow(() -> new DomainException("User not found", "USER_NOT_FOUND"));

        if (userFinancialProfileRepository.existsByUserId(uid)) {
            throw new BusinessException(
                    "User financial profile already exists",
                    CommonErrorCode.RESOURCE_ALREADY_EXISTS
            );
        }


        UserFinancialProfile profile = UserFinancialProfile.create(
                uid,
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

        UserFinancialProfile saved = userFinancialProfileRepository.save(profile);

        userRepository.markFinancialSetupCompleted(uid, true);

        return UserFinancialProfileResponse.fromDomain(saved);
    }
}