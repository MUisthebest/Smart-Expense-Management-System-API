package com.example.smartmoney.application.services;

import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.GroupRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the new anti-hallucination surface of {@link UserFinancialDataService}:
 * <ul>
 *   <li>{@link UserFinancialDataService#classifyCashFlow} — the single-source-of-truth SURPLUS/
 *       BALANCED/DEFICIT/UNKNOWN classifier used by both {@code buildChatContext} (for prompt
 *       labelling) and {@code ChatWithAIUseCase.monthInDeficit} (for the proactive-suggestion
 *       gate).</li>
 *   <li>{@link UserFinancialDataService#buildChatContext} — the labelled 4-section text block
 *       that lands in the LLM prompt. Tests assert the presence and correctness of the new
 *       {@code linkedToGroup}, cash-flow status, and safe_spending formula annotations.</li>
 * </ul>
 */
@DisplayName("UserFinancialDataService cash-flow + context labelling")
class UserFinancialDataServiceCashFlowTest {

    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final BudgetRepository budgetRepository = mock(BudgetRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    // Group lookups are mocked to return nothing: these tests assert on the cash-flow block, and
    // an empty group list simply omits the "=== Nhóm ===" section entirely.
    private final GroupRepository groupRepository = mock(GroupRepository.class);
    private final GroupProjectRepository groupProjectRepository = mock(GroupProjectRepository.class);

    private final UserFinancialDataService service = new UserFinancialDataService(
            transactionRepository, projectRepository, budgetRepository, userRepository,
            groupRepository, groupProjectRepository);

    private final UUID userId = UUID.randomUUID();

    // ── Test data helpers ────────────────────────────────────────────────────

    private Transaction income(long amount) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .amount(new Amount(BigDecimal.valueOf(amount)))
                .type(TransactionType.INCOME)
                .category(Category.SALARY)
                .description("test")
                .date(LocalDateTime.of(2026, 6, 15, 10, 0))
                .build();
    }

    private Transaction expense(long amount) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .amount(new Amount(BigDecimal.valueOf(amount)))
                .type(TransactionType.EXPENSE)
                .category(Category.FOOD)
                .description("test")
                .date(LocalDateTime.of(2026, 6, 15, 10, 0))
                .build();
    }

    private User onboardedUser(BigDecimal monthlyIncome) {
        User user = mock(User.class);
        when(user.getIncome()).thenReturn(monthlyIncome);
        when(user.hasCompletedFinancialSetup()).thenReturn(true);
        when(user.getSavingPace()).thenReturn(Saving_Pace.BALANCED);
        when(user.getFocusMode()).thenReturn(Focus_Mode.SAVE_MORE);
        when(user.getInterventionLevel()).thenReturn(Intervention_Level.GENTLE);
        when(user.isAutoInvestSurplus()).thenReturn(false);
        when(user.calculateSafeSpending(any()))
                .thenAnswer(inv -> monthlyIncome.subtract((BigDecimal) inv.getArgument(0)).max(BigDecimal.ZERO));
        return user;
    }

    private Project standaloneProject(String name) {
        return projectWith(name, /*subPersonal=*/false);
    }

    private Project subPersonalProject(String name) {
        return projectWith(name, /*subPersonal=*/true);
    }

    private Project projectWith(String name, boolean subPersonal) {
        ProjectId projectId = ProjectId.newId();
        ProjectTracking tracking = new ProjectTracking(
                UUID.randomUUID(), projectId,
                /*moneySaved*/ BigDecimal.valueOf(1_000_000), /*currentMonth*/ 1,
                /*monthlySaving*/ BigDecimal.valueOf(500_000), /*monthLeft*/ 12,
                /*moneyOwed*/ BigDecimal.ZERO, /*penalty*/ 0,
                Instant.now(), Instant.now());
        UUID groupProjectId = subPersonal ? UUID.randomUUID() : null;
        return Project.rehydrate(
                projectId, UserId.of(userId.toString()), ProjectType.PERSONAL,
                ProjectStatus.ACTIVE, ProjectPriority.HIGH, name, "desc",
                new Money(BigDecimal.valueOf(20_000_000), "VND"),
                LocalDate.now().plusMonths(12), groupProjectId, List.of(), tracking, Instant.now());
    }

    // ── §A: classifyCashFlow — the single source of truth ────────────────────

    @Nested
    @DisplayName("classifyCashFlow")
    class ClassifyCashFlow {

        @Test
        @DisplayName("returns UNKNOWN when there is no recorded income this month")
        void unknownWhenNoIncome() {
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(expense(5_000_000L)));

            assertThat(service.classifyCashFlow(userId, 6, 2026))
                    .isEqualTo(UserFinancialDataService.CashFlowStatus.UNKNOWN);
        }

        @Test
        @DisplayName("returns UNKNOWN when there are no transactions at all")
        void unknownWhenNoTransactions() {
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            assertThat(service.classifyCashFlow(userId, 6, 2026))
                    .isEqualTo(UserFinancialDataService.CashFlowStatus.UNKNOWN);
        }

        @Test
        @DisplayName("returns SURPLUS when delta > 10% of income (income 10M, expense 8M → +20%)")
        void surplusAboveTenPercentBand() {
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(income(10_000_000L), expense(8_000_000L)));

            assertThat(service.classifyCashFlow(userId, 6, 2026))
                    .isEqualTo(UserFinancialDataService.CashFlowStatus.SURPLUS);
        }

        @Test
        @DisplayName("returns BALANCED when delta is within ±10% (income 10M, expense 9.5M → +5%)")
        void balancedWithinPositiveBand() {
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(income(10_000_000L), expense(9_500_000L)));

            assertThat(service.classifyCashFlow(userId, 6, 2026))
                    .isEqualTo(UserFinancialDataService.CashFlowStatus.BALANCED);
        }

        @Test
        @DisplayName("returns BALANCED when delta is within ±10% (income 10M, expense 10.5M → -5%)")
        void balancedWithinNegativeBand() {
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(income(10_000_000L), expense(10_500_000L)));

            assertThat(service.classifyCashFlow(userId, 6, 2026))
                    .isEqualTo(UserFinancialDataService.CashFlowStatus.BALANCED);
        }

        @Test
        @DisplayName("returns DEFICIT when expense exceeds income by more than 10% (income 10M, expense 12M → -20%)")
        void deficitBelowNegativeBand() {
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(income(10_000_000L), expense(12_000_000L)));

            assertThat(service.classifyCashFlow(userId, 6, 2026))
                    .isEqualTo(UserFinancialDataService.CashFlowStatus.DEFICIT);
        }

        @Test
        @DisplayName("returns SURPLUS when expense is 0 (income 5M, expense 0 → +100%)")
        void surplusWhenZeroExpense() {
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(income(5_000_000L)));

            assertThat(service.classifyCashFlow(userId, 6, 2026))
                    .isEqualTo(UserFinancialDataService.CashFlowStatus.SURPLUS);
        }

        @Test
        @DisplayName("boundary: exactly +10% of income counts as BALANCED (rule: strict > for SURPLUS)")
        void exactPositiveBoundaryIsBalanced() {
            // income 10M, expense 9M → delta 1M = exactly 10% band, should NOT be SURPLUS.
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(income(10_000_000L), expense(9_000_000L)));

            assertThat(service.classifyCashFlow(userId, 6, 2026))
                    .isEqualTo(UserFinancialDataService.CashFlowStatus.BALANCED);
        }

        @Test
        @DisplayName("boundary: exactly -10% of income counts as BALANCED (rule: strict < for DEFICIT)")
        void exactNegativeBoundaryIsBalanced() {
            // income 10M, expense 11M → delta -1M = exactly -10% band, should NOT be DEFICIT.
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(income(10_000_000L), expense(11_000_000L)));

            assertThat(service.classifyCashFlow(userId, 6, 2026))
                    .isEqualTo(UserFinancialDataService.CashFlowStatus.BALANCED);
        }
    }

    // ── §B: buildChatContext — anti-hallucination labelling ─────────────────

    @Nested
    @DisplayName("buildChatContext labels")
    class BuildChatContext {

        @Test
        @DisplayName("emits the SURPLUS/BALANCED/DEFICIT status line so the LLM can read it back")
        void cashFlowStatusLinePresent() {
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(income(10_000_000L), expense(12_000_000L)));
            when(budgetRepository.findByUserIdMonthYear(any(), any(), any())).thenReturn(List.of());
            when(projectRepository.findAllByUser(any())).thenReturn(List.of());
            User user = onboardedUser(BigDecimal.valueOf(10_000_000));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            String context = service.buildChatContext(userId, 6, 2026);

            assertThat(context)
                    .contains("Trạng thái dòng tiền tháng: DEFICIT")
                    .contains("Chênh lệch");
        }

        @Test
        @DisplayName("safe_spending line carries the explicit formula and 'not-actual-cash' warning")
        void safeSpendingCarriesFormulaAnnotation() {
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(income(10_000_000L)));
            when(budgetRepository.findByUserIdMonthYear(any(), any(), any())).thenReturn(List.of());
            when(projectRepository.findAllByUser(any())).thenReturn(List.of());
            User user = onboardedUser(BigDecimal.valueOf(10_000_000));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            String context = service.buildChatContext(userId, 6, 2026);

            assertThat(context)
                    .contains("safe_spending")
                    .contains("Công thức:")
                    .contains("KHÔNG trừ chi tiêu thực tế đã phát sinh");
        }

        @Test
        @DisplayName("standalone project appears with linkedToGroup=false")
        void standaloneProjectMarkedFalse() {
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(income(10_000_000L)));
            when(budgetRepository.findByUserIdMonthYear(any(), any(), any())).thenReturn(List.of());
            when(projectRepository.findAllByUser(any())).thenReturn(List.of(standaloneProject("Mua nhà")));
            User user = onboardedUser(BigDecimal.valueOf(10_000_000));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            String context = service.buildChatContext(userId, 6, 2026);

            assertThat(context).contains("Mua nhà:").contains("linkedToGroup=false");
        }

        @Test
        @DisplayName("sub-personal project appears with linkedToGroup=true so the LLM knows to skip it per §5")
        void subPersonalProjectMarkedTrue() {
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(income(10_000_000L)));
            when(budgetRepository.findByUserIdMonthYear(any(), any(), any())).thenReturn(List.of());
            when(projectRepository.findAllByUser(any())).thenReturn(List.of(subPersonalProject("Team Retreat")));
            User user = onboardedUser(BigDecimal.valueOf(10_000_000));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            String context = service.buildChatContext(userId, 6, 2026);

            assertThat(context).contains("Team Retreat:").contains("linkedToGroup=true");
        }

        @Test
        @DisplayName("mixed personal + sub-personal are labelled independently per row")
        void mixedProjectsLabelledPerRow() {
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(income(10_000_000L)));
            when(budgetRepository.findByUserIdMonthYear(any(), any(), any())).thenReturn(List.of());
            when(projectRepository.findAllByUser(any())).thenReturn(List.of(
                    standaloneProject("Mua xe"),
                    subPersonalProject("Team Retreat")));
            User user = onboardedUser(BigDecimal.valueOf(10_000_000));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            String context = service.buildChatContext(userId, 6, 2026);

            // Each row must carry its own linkedToGroup=... — if we accidentally emitted the
            // marker at container level or shared a value between rows, one of these fails.
            String muaXeLine = extractLine(context, "Mua xe:");
            String teamLine = extractLine(context, "Team Retreat:");
            assertThat(muaXeLine).contains("linkedToGroup=false");
            assertThat(teamLine).contains("linkedToGroup=true");
        }

        @Test
        @DisplayName("budgets carry the alertLevel status the prompt refers to by name")
        void budgetsCarryAlertLevel() {
            Budget budget = Budget.builder()
                    .id(UUID.randomUUID())
                    .category(Category.FOOD)
                    .amountLimit(BigDecimal.valueOf(3_000_000))
                    .spent(BigDecimal.valueOf(2_800_000))  // 93% → WARNING per Budget.getAlertLevel
                    .month(6)
                    .year(2026)
                    .build();
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(income(10_000_000L)));
            when(budgetRepository.findByUserIdMonthYear(any(), any(), any())).thenReturn(List.of(budget));
            when(projectRepository.findAllByUser(any())).thenReturn(List.of());
            User user = onboardedUser(BigDecimal.valueOf(10_000_000));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            String context = service.buildChatContext(userId, 6, 2026);

            assertThat(context)
                    .contains("category=FOOD")
                    .contains("status=");  // exact enum name depends on Budget.getAlertLevel thresholds
        }

        private String extractLine(String haystack, String needle) {
            for (String line : haystack.split("\n")) {
                if (line.contains(needle)) return line;
            }
            throw new AssertionError("Line containing '" + needle + "' not found in context");
        }
    }

    // ── §C: source-of-truth invariant — classifier and context agree ────────

    @Test
    @DisplayName("SOURCE OF TRUTH: buildChatContext status and classifyCashFlow return the same value for the same month")
    void contextAndClassifierAgree() {
        when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(income(10_000_000L), expense(12_000_000L)));
        when(budgetRepository.findByUserIdMonthYear(any(), any(), any())).thenReturn(List.of());
        when(projectRepository.findAllByUser(any())).thenReturn(List.of());
        User user = onboardedUser(BigDecimal.valueOf(10_000_000));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        String context = service.buildChatContext(userId, 6, 2026);
        var classifierResult = service.classifyCashFlow(userId, 6, 2026);

        // Both must say the same thing. If either drifts (e.g. buildChatContext gets a new
        // threshold and classifyCashFlow doesn't, or vice versa), the LLM would see a different
        // state than what monthInDeficit() gates on — the exact class of hallucination the fix
        // is meant to prevent.
        assertThat(context).contains("Trạng thái dòng tiền tháng: " + classifierResult.name());
    }
}