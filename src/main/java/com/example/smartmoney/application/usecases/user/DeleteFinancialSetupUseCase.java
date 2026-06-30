package com.example.smartmoney.application.usecases.user;

import com.example.smartmoney.application.dtos.responses.FinancialSetupResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeleteFinancialSetupUseCase {

    private final UserRepository userRepository;

    @Transactional
    public FinancialSetupResponse execute(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException("User not found"));

        user.clearFinancialSetup();

        User savedUser = userRepository.save(user);

        return FinancialSetupResponse.fromDomain(savedUser);
    }
}