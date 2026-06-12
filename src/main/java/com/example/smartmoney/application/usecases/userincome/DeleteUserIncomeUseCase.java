package com.example.smartmoney.application.usecases.userincome;

import com.example.smartmoney.domain.exceptions.EntityNotFoundException;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeleteUserIncomeUseCase {

    private final UserIncomeRepository userIncomeRepository;
    private final UserRepository userRepository;

    @Transactional
    public void execute(String actorId) {
        UUID userId = UUID.fromString(actorId);

        if (userIncomeRepository.findByUserId(userId).isEmpty()) {
            throw new EntityNotFoundException("UserIncome", actorId);
        }

        userIncomeRepository.deleteByUserId(userId);

        userRepository.markIncomeSetupCompleted(userId, false);
        userRepository.markFinancialSetupCompleted(userId, false);
    }
}