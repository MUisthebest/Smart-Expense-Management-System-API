package com.example.smartmoney.application.usecases.settlement;

import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectContribution;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.ContributionId;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.models.user.enums.Intervention_Level;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;
import com.example.smartmoney.domain.repositories.ProjectHistoryRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the revived monthly settlement engine on the income + user-traits model
 * (Saving_Pace capacity cap, Focus_Mode TRACK_ONLY penalty opt-out, Intervention_Level
 * notification gating, autoInvestSurplus routing). See docs/settlement-revival-plan.md.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PerformMonthlySettlementUseCase — income + traits settlement")
class PerformMonthlySettlementUseCaseTest {

    private static final String OWNER = "f5bbeb40-0a56-4a6b-a9cf-5a547ce38a7b";

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectTrackingRepository projectTrackingRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProjectHistoryRepository projectHistoryRepository;
    @Mock private CreateNotificationUseCase createNotificationUseCase;

    @InjectMocks private PerformMonthlySettlementUseCase useCase;

    // ── helpers ──────────────────────────────────────────────────────────────

    private void stubUser(Saving_Pace pace, Intervention_Level level, Focus_Mode focus,
                          String income, Boolean autoInvestSurplus) {
        User user = User.builder()
                .id(UUID.fromString(OWNER))
                .income(new BigDecimal(income))
                .savingPace(pace)
                .interventionLevel(level)
                .focusMode(focus)
                .autoInvestSurplus(autoInvestSurplus)
                .build();
        when(userRepository.findById(UUID.fromString(OWNER))).thenReturn(Optional.of(user));
    }

    private Project project(ProjectStatus status, String target, String monthly,
                            String saved, String owed, int monthLeft, int frozenMonths) {
        ProjectId pid = ProjectId.newId();
        ProjectTracking tracking = new ProjectTracking(
                UUID.randomUUID(), pid,
                new BigDecimal(saved), 1, new BigDecimal(monthly), monthLeft,
                new BigDecimal(owed), frozenMonths, Instant.now(), Instant.now());
        // Prior progress is modelled as existing contributions — the single source of truth that
        // settlement now reads (sum of contributions) and writes into. tracking.moneySaved just mirrors it.
        List<ProjectContribution> priorContributions = new BigDecimal(saved).signum() > 0
                ? List.of(new ProjectContribution(ContributionId.newId(), UserId.of(OWNER),
                        new Money(new BigDecimal(saved), "VND"), "prior", Instant.now()))
                : List.of();
        Project p = Project.rehydrate(pid, UserId.of(OWNER), ProjectType.PERSONAL, status,
                ProjectPriority.MEDIUM, "Goal", null, new Money(new BigDecimal(target), "VND"),
                LocalDate.now().plusMonths(12), null, priorContributions, tracking, Instant.now());
        // Settlement re-fetches each project as a full aggregate (with its contributions) before
        // crediting, so save() never wipes the contribution history.
        when(projectRepository.findById(pid)).thenReturn(Optional.of(p));
        return p;
    }

    /** Income transactions are empty, so income falls back to User.getIncome(); expenses drive savings. */
    private void stubExpenses(String... amounts) {
        List<Transaction> txns = Arrays.stream(amounts).map(a -> {
            Transaction tx = mock(Transaction.class);
            when(tx.getAmount()).thenReturn(new Amount(new BigDecimal(a)));
            return tx;
        }).toList();
        when(transactionRepository.findByUserIdWithFilters(
                any(), any(), any(), any(), any(), any(), eq(TransactionType.INCOME)))
                .thenReturn(List.of());
        when(transactionRepository.findByUserIdWithFilters(
                any(), any(), any(), any(), any(), any(), eq(TransactionType.EXPENSE)))
                .thenReturn(txns);
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    // ── core math ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Surplus repays debt; calendar advances exactly once")
    void surplusRepaysDebt() {
        stubUser(Saving_Pace.BALANCED, Intervention_Level.HARD, Focus_Mode.REDUCE_SPENDING, "1000", true);
        stubExpenses("750"); // savings = 1000 - 750 = 250; goal 100 → surplus 150 repays the 150 debt
        Project p = project(ProjectStatus.ACTIVE, "1200", "100", "0", "150", 12, 0);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertAmount("0", p.getTracking().getMoneyOwed());
        assertAmount("100", p.getTracking().getMoneySaved());
        assertEquals(ProjectStatus.ACTIVE, p.getStatus());
        assertEquals(2, p.getTracking().getCurrentMonth()); // advanced once, not twice
        verify(createNotificationUseCase, never()).createNotification(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("Shortfall accrues debt and freezes the project past the cap")
    void penaltyFreezes() {
        stubUser(Saving_Pace.BALANCED, Intervention_Level.HARD, Focus_Mode.REDUCE_SPENDING, "1000", true);
        stubExpenses("1000"); // savings 0; goal 100 → penalty 100 on top of 250 owed = 350 ≥ 3×100 cap
        Project p = project(ProjectStatus.ACTIVE, "1200", "100", "0", "250", 12, 0);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertAmount("350", p.getTracking().getMoneyOwed());
        assertEquals(ProjectStatus.FROZEN, p.getStatus());
        assertEquals(0, p.getTracking().getFrozenMonths());
    }

    @Test
    @DisplayName("autoInvestSurplus ON routes leftover surplus into the project")
    void autoInvestOnRoutesSurplus() {
        stubUser(Saving_Pace.BALANCED, Intervention_Level.HARD, Focus_Mode.REDUCE_SPENDING, "2000", true);
        stubExpenses("1600"); // savings 400; goal 100 → surplus 300 auto-invested on top of the 100
        Project p = project(ProjectStatus.ACTIVE, "1200", "100", "0", "0", 12, 0);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertAmount("400", p.getTracking().getMoneySaved()); // 100 monthly + 300 surplus
        assertEquals(ProjectStatus.ACTIVE, p.getStatus());
    }

    @Test
    @DisplayName("autoInvestSurplus OFF leaves surplus as free cash (only the monthly saving credits)")
    void autoInvestOffKeepsSurplusFree() {
        stubUser(Saving_Pace.BALANCED, Intervention_Level.HARD, Focus_Mode.REDUCE_SPENDING, "2000", false);
        stubExpenses("1600"); // savings 400; surplus 300 is NOT invested
        Project p = project(ProjectStatus.ACTIVE, "1200", "100", "0", "0", 12, 0);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertAmount("100", p.getTracking().getMoneySaved()); // only the 100 monthly saving
        assertEquals(ProjectStatus.ACTIVE, p.getStatus());
    }

    // ── Option 2: settlement credits progress via a contribution (followup item 0) ──

    @Test
    @DisplayName("Monthly credit is appended as a ProjectContribution; prior contributions survive")
    void creditIsAppendedAsContributionAndPriorContributionsSurvive() {
        stubUser(Saving_Pace.BALANCED, Intervention_Level.HARD, Focus_Mode.REDUCE_SPENDING, "1000", true);
        stubExpenses("900"); // savings 100 = goal → no surplus/penalty; monthly 100 credited
        // One prior manual contribution of 500 already sits on the project.
        Project p = project(ProjectStatus.ACTIVE, "1200", "100", "500", "0", 12, 0);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        // Progress (sum of contributions) moved 500 → 600 via a new auto contribution — the manual
        // one was NOT wiped by the save.
        assertEquals(2, p.getContributions().size());
        assertAmount("600", p.totalContributed().amount());
        assertAmount("600", p.getTracking().getMoneySaved()); // tracking mirror stays in lockstep
        boolean hasAutoEntry = p.getContributions().stream()
                .anyMatch(c -> "Auto-saved this month".equals(c.getNote()));
        org.junit.jupiter.api.Assertions.assertTrue(hasAutoEntry);
    }

    // ── synthetic project-ledger exclusion (followup 0b) ─────────────────────

    @Test
    @DisplayName("A PROJECT_CONTRIBUTION expense is not counted as spending, so saving isn't penalised")
    void syntheticContributionExpenseNotPenalised() {
        stubUser(Saving_Pace.BALANCED, Intervention_Level.HARD, Focus_Mode.REDUCE_SPENDING, "1000", false);
        // The ONLY expense is a press-to-contribute mirror. Pre-fix this looked like 1000 of
        // spending → savings 0 → a 100 penalty. It must be excluded → no debt accrues.
        Transaction contribTx = mock(Transaction.class);
        when(contribTx.getCategory()).thenReturn(com.example.smartmoney.domain.models.transaction.enums.Category.PROJECT_CONTRIBUTION);
        when(transactionRepository.findByUserIdWithFilters(
                any(), any(), any(), any(), any(), any(), eq(TransactionType.INCOME))).thenReturn(List.of());
        when(transactionRepository.findByUserIdWithFilters(
                any(), any(), any(), any(), any(), any(), eq(TransactionType.EXPENSE))).thenReturn(List.of(contribTx));
        Project p = project(ProjectStatus.ACTIVE, "1200", "100", "0", "0", 12, 0);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertAmount("0", p.getTracking().getMoneyOwed()); // no penalty for saving
        assertAmount("100", p.getTracking().getMoneySaved());
        assertEquals(ProjectStatus.ACTIVE, p.getStatus());
    }

    // ── Focus_Mode.TRACK_ONLY ────────────────────────────────────────────────

    @Test
    @DisplayName("TRACK_ONLY accrues no debt and never freezes, even on a shortfall")
    void trackOnlySuppressesPenalty() {
        stubUser(Saving_Pace.BALANCED, Intervention_Level.NOTIFY, Focus_Mode.TRACK_ONLY, "1000", true);
        stubExpenses("1000"); // savings 0 → would penalise 100 under SAVE_MORE/REDUCE_SPENDING
        Project p = project(ProjectStatus.ACTIVE, "1200", "100", "0", "0", 12, 0);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertAmount("0", p.getTracking().getMoneyOwed()); // no debt accrued
        assertAmount("100", p.getTracking().getMoneySaved()); // savings still credit
        assertEquals(ProjectStatus.ACTIVE, p.getStatus());   // no freeze
        assertEquals(0, p.getTracking().getFrozenMonths());
    }

    // ── Saving_Pace capacity cap ─────────────────────────────────────────────

    @Test
    @DisplayName("Saving capacity caps the expected goal, erasing a penalty the raw goal would create")
    void capacityCapErasesPenalty() {
        // RELAXED income 500 → capacity 50 < goal 100. Savings 60 clears the *capped* 50 goal,
        // so no penalty despite falling short of the raw 100 monthly saving.
        stubUser(Saving_Pace.RELAXED, Intervention_Level.HARD, Focus_Mode.REDUCE_SPENDING, "500", true);
        stubExpenses("440"); // savings = 500 - 440 = 60 ≥ capped goal 50
        Project p = project(ProjectStatus.ACTIVE, "1200", "100", "0", "0", 12, 0);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertAmount("0", p.getTracking().getMoneyOwed());
        assertEquals(ProjectStatus.ACTIVE, p.getStatus());
    }

    // ── Intervention_Level notification gating ───────────────────────────────

    @Test
    @DisplayName("COMPLETED notifies at HARD")
    void completedNotifiesAtHard() {
        stubUser(Saving_Pace.BALANCED, Intervention_Level.HARD, Focus_Mode.REDUCE_SPENDING, "1000", true);
        stubExpenses("900"); // savings 100 = goal → no surplus/penalty
        Project p = project(ProjectStatus.ACTIVE, "1200", "100", "1100", "0", 2, 0);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertEquals(ProjectStatus.COMPLETED, p.getStatus());
        verify(createNotificationUseCase, times(1)).createNotification(any(UUID.class), anyString(), anyString());
    }

    @Test
    @DisplayName("COMPLETED is silent below HARD (GENTLE)")
    void completedSilentAtGentle() {
        stubUser(Saving_Pace.BALANCED, Intervention_Level.GENTLE, Focus_Mode.REDUCE_SPENDING, "1000", true);
        stubExpenses("900");
        Project p = project(ProjectStatus.ACTIVE, "1200", "100", "1100", "0", 2, 0);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertEquals(ProjectStatus.COMPLETED, p.getStatus());
        verify(createNotificationUseCase, never()).createNotification(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("FROZEN notifies at GENTLE")
    void frozenNotifiesAtGentle() {
        stubUser(Saving_Pace.BALANCED, Intervention_Level.GENTLE, Focus_Mode.REDUCE_SPENDING, "1000", true);
        stubExpenses("1000"); // penalty freezes the project
        Project p = project(ProjectStatus.ACTIVE, "1200", "100", "0", "250", 12, 0);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertEquals(ProjectStatus.FROZEN, p.getStatus());
        verify(createNotificationUseCase, times(1)).createNotification(any(UUID.class), anyString(), anyString());
    }

    @Test
    @DisplayName("FROZEN is silent at NOTIFY")
    void frozenSilentAtNotify() {
        stubUser(Saving_Pace.BALANCED, Intervention_Level.NOTIFY, Focus_Mode.REDUCE_SPENDING, "1000", true);
        stubExpenses("1000");
        Project p = project(ProjectStatus.ACTIVE, "1200", "100", "0", "250", 12, 0);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertEquals(ProjectStatus.FROZEN, p.getStatus());
        verify(createNotificationUseCase, never()).createNotification(any(), anyString(), anyString());
    }

    // ── freeze/complete interaction & TRACK_ONLY unfreeze ────────────────────

    @Test
    @DisplayName("A project frozen this run is not also completed, and only the FROZEN notification fires")
    void freezeSuppressesSameRunCompletion() {
        stubUser(Saving_Pace.BALANCED, Intervention_Level.HARD, Focus_Mode.REDUCE_SPENDING, "1000", true);
        stubExpenses("1000"); // savings 0 → penalty 100 pushes owed 250 → 350 ≥ cap → freeze
        // monthly saving would take saved 900 → 1000 = target, which would otherwise complete it.
        Project p = project(ProjectStatus.ACTIVE, "1000", "100", "900", "250", 12, 0);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertEquals(ProjectStatus.FROZEN, p.getStatus()); // frozen wins; not COMPLETED
        assertAmount("1000", p.getTracking().getMoneySaved());
        assertAmount("350", p.getTracking().getMoneyOwed());
        // exactly one notification (FROZEN) — no contradictory COMPLETED ping
        verify(createNotificationUseCase, times(1)).createNotification(any(UUID.class), anyString(), anyString());
    }

    @Test
    @DisplayName("TRACK_ONLY: a frozen project whose debt clears via surplus still unfreezes")
    void trackOnlyFrozenUnfreezesWhenDebtClears() {
        stubUser(Saving_Pace.BALANCED, Intervention_Level.NOTIFY, Focus_Mode.TRACK_ONLY, "1000", true);
        stubExpenses("920"); // savings 80; no active goal → surplus 80 repays the 80 owed → owed 0
        Project p = project(ProjectStatus.FROZEN, "1200", "100", "400", "80", 10, 2);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertAmount("0", p.getTracking().getMoneyOwed());
        assertEquals(ProjectStatus.ACTIVE, p.getStatus()); // unfrozen despite TRACK_ONLY
        assertEquals(0, p.getTracking().getFrozenMonths());
    }

    @Test
    @DisplayName("EXPIRED always notifies, even at NOTIFY, and persists the status change once")
    void expiredAlwaysNotifies() {
        stubUser(Saving_Pace.BALANCED, Intervention_Level.NOTIFY, Focus_Mode.REDUCE_SPENDING, "1000", true);
        stubExpenses("1000"); // savings 0; no active goal → no surplus, debt untouched
        Project p = project(ProjectStatus.FROZEN, "1200", "100", "400", "250", 10, 2);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertAmount("250", p.getTracking().getMoneyOwed()); // unchanged
        assertEquals(ProjectStatus.EXPIRED, p.getStatus());   // frozenMonths 2 → 3 == max
        verify(projectRepository, times(1)).save(p);
        verify(createNotificationUseCase, times(1)).createNotification(any(UUID.class), anyString(), anyString());
    }
}
