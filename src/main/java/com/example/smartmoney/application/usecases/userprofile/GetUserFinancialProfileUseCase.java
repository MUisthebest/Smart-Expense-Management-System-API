package com.example.smartmoney.application.usecases.userprofile;

import com.example.smartmoney.application.dtos.responses.UserFinancialProfileResponse;
import com.example.smartmoney.domain.exceptions.EntityNotFoundException;
import com.example.smartmoney.domain.repositories.UserFinancialProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetUserFinancialProfileUseCase {

    private final UserFinancialProfileRepository userFinancialProfileRepository;

    public GetUserFinancialProfileUseCase(
            UserFinancialProfileRepository userFinancialProfileRepository
    ) {
        this.userFinancialProfileRepository = userFinancialProfileRepository;
    }

    @Transactional(readOnly = true)
    public UserFinancialProfileResponse execute(String userId) {
        UUID uid = UUID.fromString(userId);

        return userFinancialProfileRepository.findByUserId(uid)
                .map(UserFinancialProfileResponse::fromDomain)
                .orElseThrow(() -> new EntityNotFoundException(
                        "UserFinancialProfile",
                        userId
                ));
    }
}