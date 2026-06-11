package com.example.smartmoney.application.usecases.userincome;

import com.example.smartmoney.application.dtos.requests.userincome.UpdateUserIncomeRequest;
import com.example.smartmoney.application.dtos.responses.userincome.UserIncomeResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.exceptions.EntityNotFoundException;
import com.example.smartmoney.domain.models.user.UserIncome;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import com.example.smartmoney.domain.services.SafeSpendingService;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;

@Service
@RequiredArgsConstructor
public class UpdateUserIncomeUseCase {

    private final UserIncomeRepository userIncomeRepository;
    private final SafeSpendingService safeSpendingService;

    @Transactional
    public UserIncomeResponse execute(String actorId, UpdateUserIncomeRequest req) {
        UUID userId = UUID.fromString(actorId);

        UserIncome current = userIncomeRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException("UserIncome", actorId));

        Integer nextNetIncome = req.getNetIncome() != null ? req.getNetIncome() : current.getNetIncome();
        Integer nextUsableIncome = req.getUsableIncome() != null ? req.getUsableIncome() : current.getUsableIncome();
        if (nextNetIncome != null && nextUsableIncome != null && nextUsableIncome >= nextNetIncome) {
            throw new DomainException("usableIncome must be less than netIncome", "USER_INCOME_INVALID_VALUE");
        }

        current.update(
                req.getNetIncome(),
                req.getUsableIncome(),
                req.getCurrency() != null ? req.getCurrency().trim() : null,
                req.getCalculationNote(),
                req.getAutoInvestSurplus()
        );
        UserIncome saved = userIncomeRepository.save(current);
        safeSpendingService.recalculateAndSaveSafeSpending(UserId.of(actorId));

        return UserIncomeResponse.fromDomain(saved);
    }
}
