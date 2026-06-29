package com.example.smartmoney.application.usecases.insight;

import com.example.smartmoney.application.dtos.responses.insight.InsightResponse;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.insight.InsightType;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
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
@DisplayName("GenerateInsightsUseCase — focus ordering")
class GenerateInsightsUseCaseTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private BudgetRepository budgetRepository;
    @Mock private UserRepository userRepository;

    private GenerateInsightsUseCase useCase;
    private final UUID userId = UUID.randomUUID();
    private static final LocalDate AS_OF = LocalDate.of(2026, 6, 15);

    @BeforeEach
    void setUp() {
        useCase = new GenerateInsightsUseCase(
                transactionRepository, budgetRepository, userRepository, new SpendingInsightAnalyzer());
    }

    private Transaction expense(Category category, int month, BigDecimal amount) {
        return Transaction.create(userId, new Amount(amount), TransactionType.EXPENSE, category,
                "t", LocalDateTime.of(2026, month, 10, 12, 0));
    }

    /** FOOD history producing a WARN overspend anomaly (baseline mean 100/std 20, current z=2.25). */
    private List<Transaction> warnOverspendHistory() {
        List<Transaction> h = new ArrayList<>();
        h.add(expense(Category.FOOD, 5, new BigDecimal("80")));
        h.add(expense(Category.FOOD, 4, new BigDecimal("120")));
        h.add(expense(Category.FOOD, 3, new BigDecimal("80")));
        h.add(expense(Category.FOOD, 2, new BigDecimal("120")));
        h.add(expense(Category.FOOD, 6, new BigDecimal("145")));
        return h;
    }

    /** TRANSPORTATION budget producing a WARN pace insight (projected 1200 vs limit 1000). */
    private Budget warnPaceBudget() {
        return Budget.builder().id(UUID.randomUUID()).userId(userId).category(Category.TRANSPORTATION)
                .amountLimit(new BigDecimal("1000")).spent(new BigDecimal("600")).month(6).year(2026).build();
    }

    private void stubData(Focus_Mode focus) {
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(warnOverspendHistory());
        when(budgetRepository.findByUserIdMonthYear(eq(userId), eq(6), eq(2026)))
                .thenReturn(List.of(warnPaceBudget()));
        User user = User.builder().id(userId).focusMode(focus).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    @Test
    @DisplayName("REDUCE_SPENDING surfaces the overspend anomaly ahead of the budget-pace insight")
    void reduceSpendingPrioritizesOverspend() {
        stubData(Focus_Mode.REDUCE_SPENDING);

        List<InsightResponse> insights = useCase.execute(userId, AS_OF);

        assertEquals(2, insights.size());
        assertEquals(InsightType.OVERSPEND_ANOMALY, insights.get(0).getType());
        assertEquals(InsightType.BUDGET_PACE, insights.get(1).getType());
    }

    @Test
    @DisplayName("Without focus prioritization, equal-severity insights fall back to deterministic type order")
    void defaultFocusUsesDeterministicOrder() {
        stubData(Focus_Mode.TRACK_ONLY);

        List<InsightResponse> insights = useCase.execute(userId, AS_OF);

        assertEquals(2, insights.size());
        // No type prioritization → alphabetical type tie-break puts BUDGET_PACE first.
        assertEquals(InsightType.BUDGET_PACE, insights.get(0).getType());
        assertEquals(InsightType.OVERSPEND_ANOMALY, insights.get(1).getType());
    }
}
