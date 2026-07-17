package com.example.smartmoney.application.usecases.budget;

import com.example.smartmoney.application.dtos.responses.budgets.BudgetAllocationPlanResponse;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.services.BudgetAllocationCalculator;
import com.example.smartmoney.domain.services.SpendingInsightAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ComputeBudgetAllocationUseCase — deterministic allocation wiring")
class ComputeBudgetAllocationUseCaseTest {

    @Mock private UserRepository userRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private ProjectRepository projectRepository;

    private ComputeBudgetAllocationUseCase useCase;
    private final UUID userId = UUID.randomUUID();
    private static final LocalDate AS_OF = LocalDate.of(2026, 6, 15);

    @BeforeEach
    void setUp() {
        useCase = new ComputeBudgetAllocationUseCase(
                userRepository, transactionRepository, projectRepository,
                new SpendingInsightAnalyzer(), new BudgetAllocationCalculator());
    }

    private Transaction foodExpense(int month, BigDecimal amount, String description) {
        return Transaction.create(userId, new Amount(amount), TransactionType.EXPENSE, Category.FOOD,
                description, LocalDateTime.of(2026, month, 10, 12, 0));
    }

    /** income 20M, BALANCED pace, REDUCE_SPENDING focus → savingCapacity 0.20 = 4M. */
    private User fullySetUpUser() {
        return User.builder()
                .id(userId)
                .income(new BigDecimal("20000000"))
                .savingPace(Saving_Pace.BALANCED)
                .focusMode(Focus_Mode.REDUCE_SPENDING)
                .interventionLevel(Intervention_Level.GENTLE)
                .autoInvestSurplus(true)
                .build();
    }

    private BigDecimal amountOf(BudgetAllocationPlanResponse plan, Category c) {
        return plan.getAllocations().stream().filter(a -> a.getCategory() == c).findFirst()
                .map(BudgetAllocationPlanResponse.CategoryAllocation::getAmount).orElseThrow();
    }

    @Test
    @DisplayName("established user: envelope = income − targetSavings; FOOD funded from its median + buffer")
    void computesEnvelopeAndAllocation() {
        // 3 prior months of 5M FOOD spend (distinct descriptions → not detected as recurring).
        List<Transaction> history = new ArrayList<>();
        history.add(foodExpense(3, new BigDecimal("5000000"), "groceries-mar"));
        history.add(foodExpense(4, new BigDecimal("5000000"), "groceries-apr"));
        history.add(foodExpense(5, new BigDecimal("5000000"), "groceries-may"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(fullySetUpUser()));
        when(projectRepository.findAllByUser(any(), any())).thenReturn(List.<Project>of());
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(history);

        BudgetAllocationPlanResponse plan = useCase.execute(userId, AS_OF);

        assertEquals(6, plan.getMonth());
        assertEquals(2026, plan.getYear());
        assertEquals(0, new BigDecimal("20000000").compareTo(plan.getSafeSpending())); // no project commitment
        assertEquals(0, new BigDecimal("4000000").compareTo(plan.getTargetSavings())); // 20M × 0.20
        assertEquals(0, new BigDecimal("16000000").compareTo(plan.getEnvelope()));      // 20M − 4M
        assertFalse(plan.isColdStart());
        assertFalse(plan.isOverCommitted());
        // FOOD median 5M + GENTLE 10% buffer = 5.5M; the rest is surplus.
        assertEquals(0, new BigDecimal("5500000").compareTo(amountOf(plan, Category.FOOD)));
        assertEquals(0, new BigDecimal("10500000").compareTo(plan.getSurplusToSavings()));
    }

    @Test
    @DisplayName("no transaction history → cold-start template plan")
    void coldStartWhenNoHistory() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(fullySetUpUser()));
        when(projectRepository.findAllByUser(any(), any())).thenReturn(List.<Project>of());
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.<Transaction>of());

        BudgetAllocationPlanResponse plan = useCase.execute(userId, AS_OF);

        assertTrue(plan.isColdStart());
        assertEquals(0, new BigDecimal("16000000").compareTo(plan.getEnvelope()));
    }

    @Test
    @DisplayName("incomplete financial setup → domain exception")
    void rejectsIncompleteSetup() {
        User user = User.builder().id(userId).build(); // no income/traits
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(DomainException.class, () -> useCase.execute(userId, AS_OF));
    }
}
