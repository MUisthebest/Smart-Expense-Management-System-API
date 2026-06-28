package com.example.smartmoney.domain.services;

import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SafeSpendingService {

    /*
     * Disabled: user_income table removed.
     * Previously depended on UserIncomeRepository, ProjectRepository, ProjectTrackingRepository.
     * Formula was: safe_spending = usable_income - SUM(monthly_saving) - SUM(money_owed)
     */
    @Transactional
    public void recalculateAndSaveSafeSpending(UserId ownerId) {
        // no-op
    }
}
