package com.example.smartmoney.application.usecases.budget;

import com.example.smartmoney.application.dtos.requests.project.ProjectFilterRequest;
import com.example.smartmoney.application.dtos.responses.budgets.BudgetAllocationPlanResponse;
import com.example.smartmoney.domain.exceptions.BudgetErrorCode;
import com.example.smartmoney.domain.exceptions.DomainException;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.services.BudgetAllocationCalculator;
import com.example.smartmoney.domain.services.BudgetAllocationCalculator.BudgetAllocationInputs;
import com.example.smartmoney.domain.services.BudgetAllocationCalculator.BudgetAllocationPlan;
import com.example.smartmoney.domain.services.SpendingInsightAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Synchronous, deterministic budget allocation — the pure-code alternative to the async AI
 * endpoint ({@code POST /api/v1/ai/budget/generate}). Loads the same envelope inputs as
 * {@link GenerateBudgetAllocationPlanUseCase} and the same trailing history as
 * {@code GenerateInsightsUseCase}, then runs {@link BudgetAllocationCalculator}. Read-only: it
 * proposes a plan; creating budgets from it is a separate step.
 *
 * <p>See docs/deterministic-budget-allocation-plan.md.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ComputeBudgetAllocationUseCase {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final ProjectRepository projectRepository;
    private final SpendingInsightAnalyzer analyzer;
    private final BudgetAllocationCalculator calculator;

    /** Compute for the user's current month (as of {@code LocalDate.now()}). */
    @Transactional(readOnly = true)
    public BudgetAllocationPlanResponse execute(UUID userId) {
        return execute(userId, LocalDate.now());
    }

    /** Overload taking an explicit reference date — keeps the use case deterministic for tests. */
    @Transactional(readOnly = true)
    public BudgetAllocationPlanResponse execute(UUID userId, LocalDate asOf) {
        if (userId == null) {
            throw new DomainException("User ID is required", BudgetErrorCode.USER_NOT_FOUND);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DomainException("User not found", BudgetErrorCode.USER_NOT_FOUND));

        if (!user.hasCompletedFinancialSetup()) {
            throw new DomainException(
                    "Please complete financial setup before generating a budget",
                    BudgetErrorCode.FINANCIAL_SETUP_REQUIRED);
        }

        YearMonth period = YearMonth.from(asOf);

        // ---- Stage 0: the spendable envelope (needs the User/Project models, so computed here) ----
        List<Project> activeProjects = activeProjects(new UserId(userId));
        BigDecimal commitment = activeProjects.stream()
                .map(Project::calculateMonthlyCommitment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal safeSpending = user.calculateSafeSpending(commitment);
        BigDecimal savingCapacity = user.getSavingCapacity();
        // Projects are press-to-contribute savings, so they count toward the savings ambition:
        // only the shortfall between the trait target and what projects already reserve is held back.
        BigDecimal targetSavings = savingCapacity.subtract(commitment).max(BigDecimal.ZERO);
        BigDecimal envelope = safeSpending.subtract(targetSavings).max(BigDecimal.ZERO);

        // ---- Load trailing history and derive per-category signals via the analyzer ----
        LocalDateTime windowStart = period.minusMonths(SpendingInsightAnalyzer.TRAILING_WINDOW)
                .atDay(1).atStartOfDay();
        LocalDateTime windowEnd = asOf.atTime(23, 59, 59);
        List<Transaction> history = transactionRepository.findByUserIdWithFilters(
                userId, null, null, windowStart, windowEnd, null, null);

        Map<Category, BigDecimal> median = new EnumMap<>(Category.class);
        Map<Category, BigDecimal> currentMonthSpent = new EnumMap<>(Category.class);
        for (Category c : BudgetAllocationCalculator.EXPENSE_CATEGORIES) {
            median.put(c, analyzer.trailing3MonthMedian(history, c, period));
            currentMonthSpent.put(c, analyzer.currentMonthTotal(history, c, period));
        }
        Map<Category, BigDecimal> recurring = analyzer.recurringCostByCategory(history, period);

        BudgetAllocationInputs inputs = new BudgetAllocationInputs(
                envelope, targetSavings, median, recurring, currentMonthSpent,
                user.getFocusMode(), user.getInterventionLevel(), !activeProjects.isEmpty(),
                monthsOfHistory(history, period));

        BudgetAllocationPlan plan = calculator.allocate(inputs);

        log.info("Computed deterministic budget allocation: userId={}, month={}, envelope={}, coldStart={}, overCommitted={}",
                userId, period, plan.envelope(), plan.coldStart(), plan.overCommitted());

        return toResponse(period, user, safeSpending, targetSavings, plan);
    }

    /** Distinct prior months (within the trailing window) that carry expense spend. */
    private int monthsOfHistory(List<Transaction> history, YearMonth period) {
        if (history == null) {
            return 0;
        }
        return (int) history.stream()
                .filter(tx -> tx != null && tx.getType() == TransactionType.EXPENSE
                        && tx.getDate() != null && tx.getAmount() != null)
                .map(tx -> YearMonth.from(tx.getDate()))
                .filter(m -> m.isBefore(period))
                .distinct()
                .count();
    }

    private List<Project> activeProjects(UserId userId) {
        ProjectFilterRequest activeProjectFilter = new ProjectFilterRequest(
                null, null, ProjectStatus.ACTIVE, null);
        return projectRepository.findAllByUser(userId, activeProjectFilter);
    }

    private BudgetAllocationPlanResponse toResponse(YearMonth period, User user, BigDecimal safeSpending,
                                                    BigDecimal targetSavings, BudgetAllocationPlan plan) {
        List<BudgetAllocationPlanResponse.CategoryAllocation> allocations = plan.allocations().stream()
                .map(a -> BudgetAllocationPlanResponse.CategoryAllocation.builder()
                        .category(a.category())
                        .amount(a.amount())
                        .recurring(a.recurring())
                        .basis(a.basis().name())
                        .build())
                .toList();

        // Mid-month floors the category squeeze couldn't absorb — plus any floored-category
        // top-up allowance the calculator billed to savings — are funded from the savings
        // ambition: the envelope grows by the drawn amount and targetSavings shrinks by it, so
        // Σ allocations + surplus still reconciles against the reported envelope.
        BigDecimal savingsDraw = plan.savingsShortfall().min(targetSavings).max(BigDecimal.ZERO);

        return BudgetAllocationPlanResponse.builder()
                .month(period.getMonthValue())
                .year(period.getYear())
                .income(user.getIncome())
                .safeSpending(safeSpending)
                .targetSavings(targetSavings.subtract(savingsDraw))
                .envelope(plan.envelope().add(savingsDraw))
                .surplusToSavings(plan.surplusToSavings())
                .coldStart(plan.coldStart())
                .overCommitted(plan.overCommitted())
                .allocations(allocations)
                .build();
    }
}
