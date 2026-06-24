package com.example.smartmoney.application.usecases.userprofile;

import com.example.smartmoney.domain.exceptions.EntityNotFoundException;
import com.example.smartmoney.domain.repositories.UserFinancialProfileRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeleteUserFinancialProfileUseCase {

    private final UserFinancialProfileRepository userFinancialProfileRepository;
    private final UserRepository userRepository;

    @Transactional
    public void execute(String userId) {
        UUID uid = UUID.fromString(userId);

        if (userFinancialProfileRepository.findByUserId(uid).isEmpty()) {
            throw new EntityNotFoundException("UserFinancialProfile", userId);
        }

        userFinancialProfileRepository.deleteByUserId(uid);

        userRepository.markFinancialSetupCompleted(uid, false);
    }
}