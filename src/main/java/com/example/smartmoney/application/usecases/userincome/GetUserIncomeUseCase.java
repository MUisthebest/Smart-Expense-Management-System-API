package com.example.smartmoney.application.usecases.userincome;

import com.example.smartmoney.application.dtos.responses.userincome.UserIncomeResponse;
import com.example.smartmoney.domain.exceptions.EntityNotFoundException;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetUserIncomeUseCase {

    private final UserIncomeRepository userIncomeRepository;

    @Transactional(readOnly = true)
    public UserIncomeResponse execute(String actorId) {
        UUID userId = UUID.fromString(actorId);

        return userIncomeRepository.findById(userId)
                .map(UserIncomeResponse::fromDomain)
                .orElseThrow(() -> new EntityNotFoundException("UserIncome", actorId));
    }
}
