package com.example.smartmoney.application.usecases.userprofile;

/*
import com.example.smartmoney.application.dtos.responses.UserFinancialProfileMeResponse;
import com.example.smartmoney.application.dtos.responses.UserFinancialProfileResponse;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.infrastructure.persistence.repositories.JpaUserFinancialProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetUserFinancialProfileMeUseCase {

    private final JpaUserFinancialProfileRepository repository;

    public GetUserFinancialProfileMeUseCase(JpaUserFinancialProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public UserFinancialProfileMeResponse execute(UserId userId) {
        return repository.findByUserId(userId.value())
                .map(entity -> UserFinancialProfileMeResponse.builder()
                        .configured(true)
                        .profile(UserFinancialProfileResponse.fromEntity(entity))
                        .build()
                )
                .orElseGet(() -> UserFinancialProfileMeResponse.builder()
                        .configured(false)
                        .profile(null)
                        .build()
                );
    }
}
*/
