package com.example.smartmoney.application.usecases.userprofile;

import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.infrastructure.persistence.entities.UserFinancialProfileEntity;
import com.example.smartmoney.infrastructure.persistence.repositories.JpaUserFinancialProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class DeleteUserFinancialProfileUseCase {

    private final JpaUserFinancialProfileRepository repository;

    public DeleteUserFinancialProfileUseCase(JpaUserFinancialProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void execute(String userId) {
        UUID uid = UUID.fromString(userId);

        UserFinancialProfileEntity entity = repository.findByUserId(uid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User financial profile not found"
                ));

        repository.delete(entity);
    }
}