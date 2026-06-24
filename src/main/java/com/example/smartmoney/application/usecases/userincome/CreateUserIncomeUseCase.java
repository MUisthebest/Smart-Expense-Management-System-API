package com.example.smartmoney.application.usecases.userincome;

import com.example.smartmoney.application.dtos.requests.userincome.CreateUserIncomeRequest;
import com.example.smartmoney.application.dtos.responses.userincome.UserIncomeResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.user.UserIncome;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import com.example.smartmoney.domain.services.SafeSpendingService;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;

@Service
@RequiredArgsConstructor
public class CreateUserIncomeUseCase {

    private final UserIncomeRepository userIncomeRepository;
    private final UserRepository userRepository;
    private final SafeSpendingService safeSpendingService;

    @Transactional
    public UserIncomeResponse execute(String actorId, CreateUserIncomeRequest req) {
        UUID userId = UUID.fromString(actorId);

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException("User not found", "USER_NOT_FOUND"));

        if (userIncomeRepository.findByUserId(userId).isPresent()) {
            throw new DomainException("User income already exists", "USER_INCOME_ALREADY_EXISTS");
        }

        if (req.getUsableIncome() >= req.getNetIncome()) {
            throw new DomainException("usableIncome must be less than netIncome", "USER_INCOME_INVALID_VALUE");
        }

        UserIncome created = UserIncome.createNew(
                userId,
                req.getNetIncome(),
                req.getUsableIncome(),
                req.getCurrency() != null ? req.getCurrency().trim() : "VND",
                req.getCalculationNote(),
                req.getAutoInvestSurplus()
        );

        UserIncome saved = userIncomeRepository.save(created);
        user.setIncomeSetupCompleted(true);
        userRepository.save(user);
        safeSpendingService.recalculateAndSaveSafeSpending(UserId.of(actorId));

        return UserIncomeResponse.fromDomain(saved);
    }
}
