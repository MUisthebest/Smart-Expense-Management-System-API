package com.example.smartmoney.application.usecases.settlement;

import com.example.smartmoney.application.usecases.group.NotifyGroupProjectCompletedUseCase;
import com.example.smartmoney.application.usecases.notification.CreateNotificationUseCase;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.models.user.UserIncome;
import com.example.smartmoney.domain.repositories.GroupProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectHistoryRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.ProjectTrackingRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserIncomeRepository;
import com.example.smartmoney.domain.services.SafeSpendingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the all-auto settlement transitions: bounded/self-healing debt,
 * freeze/unfreeze, net-based completion, and expiry.
 * See docs/project-tracking-all-auto-plan.md §4 / §11.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PerformMonthlySettlementUseCase — all-auto transitions")
class PerformMonthlySettlementUseCaseTest {

    private static final String OWNER = "f5bbeb40-0a56-4a6b-a9cf-5a547ce38a7b";

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectTrackingRepository projectTrackingRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private UserIncomeRepository userIncomeRepository;
    @Mock private ProjectHistoryRepository projectHistoryRepository;
    @Mock private SafeSpendingService safeSpendingService;
    @Mock private GroupProjectRepository groupProjectRepository;
    @Mock private NotifyGroupProjectCompletedUseCase notifyGroupProjectCompleted;
    @Mock private CreateNotificationUseCase createNotificationUseCase;

    private PerformMonthlySettlementUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PerformMonthlySettlementUseCase(
                projectRepository, projectTrackingRepository, transactionRepository,
                userIncomeRepository, projectHistoryRepository, safeSpendingService,
                groupProjectRepository, notifyGroupProjectCompleted, createNotificationUseCase);
        ReflectionTestUtils.setField(useCase, "freezeMult", 3);
        ReflectionTestUtils.setField(useCase, "unfreezeMult", 0);
        ReflectionTestUtils.setField(useCase, "maxFrozenMonths", 3);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private UserIncome income(int safeSpending) {
        return new UserIncome(UUID.randomUUID(), UUID.fromString(OWNER),
                1_000_000, 500_000, safeSpending, "VND", null, false, null,
                Instant.now(), Instant.now());
    }

    private Project project(ProjectStatus status, String target, String monthly,
                            String saved, String owed, int monthLeft, int frozenMonths) {
        ProjectId pid = ProjectId.newId();
        ProjectTracking tracking = new ProjectTracking(
                UUID.randomUUID(), pid,
                new BigDecimal(saved), 1, new BigDecimal(monthly), monthLeft,
                new BigDecimal(owed), frozenMonths, Instant.now(), Instant.now());
        Project p = Project.rehydrate(pid, UserId.of(OWNER), ProjectType.PERSONAL, status,
                ProjectPriority.MEDIUM, "Goal", null, new Money(new BigDecimal(target), "VND"),
                LocalDate.now().plusMonths(12), null, List.of(), tracking, Instant.now());
        when(projectTrackingRepository.findByProjectId(pid)).thenReturn(Optional.of(tracking));
        return p;
    }

    private void stubIncome(int safeSpending) {
        when(userIncomeRepository.findByUserId(UUID.fromString(OWNER))).thenReturn(Optional.of(income(safeSpending)));
    }

    private void stubNoExpenses() {
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
    }

    private void stubExpenses(String amount) {
        Transaction tx = mock(Transaction.class);
        when(tx.getAmount()).thenReturn(new Amount(new BigDecimal(amount)));
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(tx));
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                () -> "expected " + expected + " but was " + actual);
    }

    // ── tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Surplus repays debt; calendar advances exactly once")
    void surplusRepaysDebt() {
        stubIncome(500);
        stubNoExpenses();
        // base = 500 + 150 debt = 650 surplus; repays 150 → owed 0; gross 0+100.
        Project p = project(ProjectStatus.ACTIVE, "1200", "100", "0", "150", 12, 0);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertAmount("0", p.getTracking().getMoneyOwed());
        assertAmount("100", p.getTracking().getMoneySaved());
        assertEquals(ProjectStatus.ACTIVE, p.getStatus());
        assertEquals(2, p.getTracking().getCurrentMonth()); // advanced once, not twice
    }

    @Test
    @DisplayName("Penalty is capped at freezeMult×monthly and the project freezes")
    void penaltyCappedAndFreeze() {
        stubIncome(0);
        stubExpenses("1000"); // base = 0 + 250 debt = 250; penalty = 750
        // cap = 3×100 = 300; room = 300-250 = 50; debt capped at 300.
        Project p = project(ProjectStatus.ACTIVE, "1200", "100", "0", "250", 12, 0);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertAmount("300", p.getTracking().getMoneyOwed());
        assertEquals(ProjectStatus.FROZEN, p.getStatus());
        assertEquals(0, p.getTracking().getFrozenMonths());
    }

    @Test
    @DisplayName("Frozen project is repaid from surplus and auto-unfreezes when debt clears")
    void frozenRepaidUnfreezes() {
        stubIncome(200);
        stubNoExpenses(); // base = 200 + 80 = 280 surplus; repays 80 → owed 0
        Project p = project(ProjectStatus.FROZEN, "1200", "100", "400", "80", 10, 2);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertAmount("0", p.getTracking().getMoneyOwed());
        assertAmount("400", p.getTracking().getMoneySaved()); // no new savings while frozen
        assertEquals(ProjectStatus.ACTIVE, p.getStatus());
        assertEquals(0, p.getTracking().getFrozenMonths());
    }

    @Test
    @DisplayName("Completes when net savings reach target")
    void completesOnNet() {
        stubIncome(500);
        stubNoExpenses();
        Project p = project(ProjectStatus.ACTIVE, "1200", "100", "1100", "0", 2, 0);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertEquals(ProjectStatus.COMPLETED, p.getStatus());
    }

    @Test
    @DisplayName("Expires when the deadline passes under target, and notifies the owner")
    void expiresOnDeadline() {
        stubIncome(500);
        stubNoExpenses();
        Project p = project(ProjectStatus.ACTIVE, "1200", "100", "300", "0", 1, 0);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertEquals(ProjectStatus.EXPIRED, p.getStatus());
        verify(projectRepository, times(1)).save(p); // status change must be persisted
        verify(createNotificationUseCase, times(1))
                .createNotification(any(UUID.class), any(String.class), any(String.class));
    }

    @Test
    @DisplayName("Stuck-frozen beyond max-frozen-months expires and notifies")
    void stuckFrozenExpires() {
        stubIncome(0);
        stubExpenses("250"); // base = 0 + 250 = 250; expenses 250 → no surplus, no penalty
        Project p = project(ProjectStatus.FROZEN, "1200", "100", "400", "250", 10, 2);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertAmount("250", p.getTracking().getMoneyOwed()); // unchanged
        assertEquals(ProjectStatus.EXPIRED, p.getStatus());   // frozenMonths 2 -> 3 == max
        verify(projectRepository, times(1)).save(p); // status change must be persisted
        verify(createNotificationUseCase, times(1))
                .createNotification(any(UUID.class), any(String.class), any(String.class));
    }

    @Test
    @DisplayName("A healthy active project does not freeze and is never notified")
    void healthyActiveNoFreezeNoNotify() {
        stubIncome(500);
        stubNoExpenses();
        Project p = project(ProjectStatus.ACTIVE, "1200", "100", "0", "0", 12, 0);

        useCase.settleUserProjects(UserId.of(OWNER), List.of(p));

        assertEquals(ProjectStatus.ACTIVE, p.getStatus());
        assertAmount("100", p.getTracking().getMoneySaved());
        verify(createNotificationUseCase, never())
                .createNotification(any(UUID.class), any(String.class), any(String.class));
    }
}
