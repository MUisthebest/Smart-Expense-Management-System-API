package com.example.smartmoney.application.usecases.budget;

import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteBudgetUseCase {
    private final BudgetRepository budgetRepository;

    public void execute(UUID budgetId, UUID userId) {
        if (budgetId == null) {
            throw new DomainException("Budget ID is required");
        }
        if (userId == null) {
            throw new DomainException("User ID is required");
        }

        // Verify budget exists and belongs to the user
        var budget = budgetRepository.findById(budgetId)
            .orElseThrow(() -> new DomainException("Budget not found with id: " + budgetId));

        // Verify the budget belongs to the user
        if (!budget.getUserId().equals(userId)) {
            throw new DomainException("Budget does not belong to the authenticated user");
        }

        budgetRepository.deleteById(budgetId);
        
        log.info("Budget deleted: id={}, userId={}", budgetId, userId);
    }
}
