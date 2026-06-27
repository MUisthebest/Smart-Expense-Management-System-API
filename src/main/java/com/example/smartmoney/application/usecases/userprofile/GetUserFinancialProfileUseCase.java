package com.example.smartmoney.application.usecases.userprofile;

/*
import com.example.smartmoney.application.dtos.responses.UserFinancialProfileResponse;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.infrastructure.persistence.repositories.JpaUserFinancialProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GetUserFinancialProfileUseCase {

    private final JpaUserFinancialProfileRepository repository;

    public GetUserFinancialProfileUseCase(JpaUserFinancialProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public UserFinancialProfileResponse execute(String userId) {

        UUID uid = UUID.fromString(userId);

        return repository.findByUserId(uid)
                .map(UserFinancialProfileResponse::fromEntity)
                .orElse(null);
    }
}
*/
