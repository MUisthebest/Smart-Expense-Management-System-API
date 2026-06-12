package com.example.smartmoney.application.usecases.userprofile;

import com.example.smartmoney.application.dtos.responses.UserFinancialProfileMeResponse;
import com.example.smartmoney.application.dtos.responses.UserFinancialProfileResponse;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.UserFinancialProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetUserFinancialProfileMeUseCase {

    private final UserFinancialProfileRepository userFinancialProfileRepository;

    public GetUserFinancialProfileMeUseCase(
            UserFinancialProfileRepository userFinancialProfileRepository
    ) {
        this.userFinancialProfileRepository = userFinancialProfileRepository;
    }

    @Transactional(readOnly = true)
    public UserFinancialProfileMeResponse execute(UserId userId) {
        return userFinancialProfileRepository.findByUserId(userId.value())
                .map(profile -> UserFinancialProfileMeResponse.builder()
                        .configured(true)
                        .profile(UserFinancialProfileResponse.fromDomain(profile))
                        .build()
                )
                .orElseGet(() -> UserFinancialProfileMeResponse.builder()
                        .configured(false)
                        .profile(null)
                        .build()
                );
    }
}