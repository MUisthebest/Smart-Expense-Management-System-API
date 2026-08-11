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
import com.example.smartmoney.domain.services.EnvelopeCalculator;
import com.example.smartmoney.domain.services.SpendingInsightAnalyzer;
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

    // Both of these are pure computation over what the repositories return, so the real
    // implementations are used rather than mocks — a stubbed envelope would make the
    // "context and backend agree" assertions below vacuous, which is the whole point of them.
    private final EnvelopeCalculator envelopeCalculator = new EnvelopeCalculator();
    private final SpendingInsightAnalyzer insightAnalyzer = new SpendingInsightAnalyzer();

    private final UserFinancialDataService service = new UserFinancialDataService(
            transactionRepository, projectRepository, budgetRepository, userRepository,
            groupRepository, groupProjectRepository, envelopeCalculator, insightAnalyzer);

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
        // EnvelopeCalculator reads the pace-derived capacity directly off the user; a mock that
        // returns null there would NPE inside the real calculator the service now delegates to.
        when(user.getSavingCapacity()).thenReturn(monthlyIncome.multiply(BigDecimal.valueOf(0.20)));
        return user;
    }

    private Project standaloneProject(String name) {
        return projectWith(name, /*subPersonal=*/false);
    }

    private Project subPersonalProject(String name) {
        return projectWith(name, /*subPersonal=*/true);
    }

    private Project projectWith(String name, boolean subPersonal) {
        return projectWith(name, subPersonal, ProjectStatus.ACTIVE, LocalDate.now().plusMonths(12));
    }

    private Project projectWith(String name, boolean subPersonal,
                                ProjectStatus status, LocalDate deadline) {
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
                status, ProjectPriority.HIGH, name, "desc",
                new Money(BigDecimal.valueOf(20_000_000), "VND"),
                deadline, groupProjectId, List.of(), tracking, Instant.now());
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
        @DisplayName("safe-spending block carries the formula plus the precomputed remaining figure")
        void safeSpendingCarriesFormulaAnnotation() {
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(income(10_000_000L)));
            when(budgetRepository.findByUserIdMonthYear(any(), any(), any())).thenReturn(List.of());
            when(projectRepository.findAllByUser(any())).thenReturn(List.of());
            User user = onboardedUser(BigDecimal.valueOf(10_000_000));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            String context = service.buildChatContext(userId, 6, 2026);

            assertThat(context)
                    .contains("Chi tiêu an toàn hàng tháng theo kế hoạch")
                    .contains("Công thức:")
                    .contains("CHI TIÊU AN TOÀN CÒN LẠI THỰC TẾ")
                    // The old raw field name must not reach the prompt at all: §10 forbids it in
                    // the reply, and the surest way to stop the model echoing it is to never show
                    // it one.
                    .doesNotContain("safe_spending");
        }

        @Test
        @DisplayName("both incomes are labelled separately so neither can stand in for the other")
        void bothIncomesLabelledSeparately() {
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(income(7_000_000L), expense(2_000_000L)));
            when(budgetRepository.findByUserIdMonthYear(any(), any(), any())).thenReturn(List.of());
            when(projectRepository.findAllByUser(any())).thenReturn(List.of());
            User user = onboardedUser(BigDecimal.valueOf(10_000_000));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            String context = service.buildChatContext(userId, 6, 2026);

            assertThat(context)
                    .contains("Thu nhập THỰC TẾ đã ghi nhận trong tháng: 7,000,000 VND")
                    .contains("Thu nhập hàng tháng theo hồ sơ: 10,000,000 VND")
                    // 7M actual vs 10M planned — the gap must be stated, not left for the model
                    // to compute (or, worse, to overlook).
                    .contains("Chênh lệch thu nhập thực tế so với hồ sơ: -3,000,000 VND");
        }

        @Test
        @DisplayName("no recorded income is reported as 'not entered yet', never as zero income")
        void missingIncomeIsNotZeroIncome() {
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(expense(2_000_000L)));
            when(budgetRepository.findByUserIdMonthYear(any(), any(), any())).thenReturn(List.of());
            when(projectRepository.findAllByUser(any())).thenReturn(List.of());
            User user = onboardedUser(BigDecimal.valueOf(10_000_000));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            String context = service.buildChatContext(userId, 6, 2026);

            assertThat(context)
                    .contains("CHƯA GHI NHẬN khoản thu")
                    .contains("Trạng thái dòng tiền tháng: UNKNOWN");
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

    // ── §B2: only live projects reserve money out of the month ──────────────

    @Nested
    @DisplayName("safe spending vs project lifecycle")
    class SafeSpendingCommitment {

        /** 20,000,000 target spread over the 12 months from creation to deadline. */
        private static final BigDecimal RUNNING_PROJECT_COMMITMENT = new BigDecimal("1666666.67");

        private void stubMonth(List<Project> projects) {
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(income(10_000_000L)));
            when(budgetRepository.findByUserIdMonthYear(any(), any(), any())).thenReturn(List.of());
            when(projectRepository.findAllByUser(any())).thenReturn(projects);
            // Built before the when(...) call: onboardedUser() stubs its own mock, and Mockito
            // rejects stubbing that happens inside another when(...) argument.
            User user = onboardedUser(BigDecimal.valueOf(10_000_000));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        }

        @Test
        @DisplayName("a running project is subtracted from safe spending")
        void runningProjectIsSubtracted() {
            stubMonth(List.of(standaloneProject("Mua nhà")));

            var snapshot = service.getFinancialSnapshot(userId, 6, 2026);

            assertThat(snapshot.activeCommitment()).isEqualByComparingTo(RUNNING_PROJECT_COMMITMENT);
            assertThat(snapshot.safeSpending())
                    .isEqualByComparingTo(BigDecimal.valueOf(10_000_000).subtract(RUNNING_PROJECT_COMMITMENT));
        }

        @Test
        @DisplayName("an EXPIRED project releases its commitment — safe spending recovers in full")
        void expiredProjectReleasesCommitment() {
            stubMonth(List.of(projectWith("Mua xe", false, ProjectStatus.EXPIRED,
                    LocalDate.now().plusMonths(12))));

            var snapshot = service.getFinancialSnapshot(userId, 6, 2026);

            // The deadline is still in the future, which is exactly the case the old
            // deadline-only filter got wrong: it kept charging the user for a failed goal.
            assertThat(snapshot.activeCommitment()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(snapshot.releasedCommitment()).isEqualByComparingTo(RUNNING_PROJECT_COMMITMENT);
            assertThat(snapshot.safeSpending()).isEqualByComparingTo(BigDecimal.valueOf(10_000_000));
        }

        @Test
        @DisplayName("COMPLETED / ABANDONED / FROZEN projects release their commitment too")
        void otherTerminalStatusesRelease() {
            for (ProjectStatus status : List.of(ProjectStatus.COMPLETED, ProjectStatus.ABANDONED,
                    ProjectStatus.FROZEN)) {
                stubMonth(List.of(projectWith("Quỹ dự phòng", false, status,
                        LocalDate.now().plusMonths(12))));

                assertThat(service.getFinancialSnapshot(userId, 6, 2026).safeSpending())
                        .as("status %s must not reserve money", status)
                        .isEqualByComparingTo(BigDecimal.valueOf(10_000_000));
            }
        }

        @Test
        @DisplayName("a past-deadline project still stored as ACTIVE is treated as expired")
        void lazilyExpiredProjectIsTreatedAsExpired() {
            // Expiry is applied lazily by the project screens, so findAllByUser can hand back a
            // row whose status says ACTIVE long after its deadline passed.
            stubMonth(List.of(projectWith("Du lịch hè", false, ProjectStatus.ACTIVE,
                    LocalDate.now().minusMonths(2))));

            var snapshot = service.getFinancialSnapshot(userId, 6, 2026);
            assertThat(snapshot.activeCommitment()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(snapshot.safeSpending()).isEqualByComparingTo(BigDecimal.valueOf(10_000_000));

            // ...and the context must not describe it as running, either.
            String context = service.buildChatContext(userId, 6, 2026);
            assertThat(context).contains("countsTowardSafeSpending=false");
            assertThat(context).contains("status=EXPIRED");
        }

        @Test
        @DisplayName("remaining safe spending subtracts living costs but not project contributions")
        void remainingExcludesProjectContributions() {
            Transaction contribution = Transaction.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .amount(new Amount(BigDecimal.valueOf(3_000_000)))
                    .type(TransactionType.EXPENSE)
                    .category(Category.PROJECT_CONTRIBUTION)
                    .description("góp dự án")
                    .date(LocalDateTime.of(2026, 6, 10, 9, 0))
                    .build();
            when(transactionRepository.findByUserIdWithFilters(eq(userId), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(income(10_000_000L), expense(2_000_000L), contribution));
            when(budgetRepository.findByUserIdMonthYear(any(), any(), any())).thenReturn(List.of());
            when(projectRepository.findAllByUser(any())).thenReturn(List.of());
            User user = onboardedUser(BigDecimal.valueOf(10_000_000));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            var snapshot = service.getFinancialSnapshot(userId, 6, 2026);

            assertThat(snapshot.recordedExpense()).isEqualByComparingTo(BigDecimal.valueOf(5_000_000));
            assertThat(snapshot.livingExpense()).isEqualByComparingTo(BigDecimal.valueOf(2_000_000));
            // Moving 3M into a saving goal must not read as 3M less to live on.
            assertThat(snapshot.remainingSafeSpending()).isEqualByComparingTo(BigDecimal.valueOf(8_000_000));
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