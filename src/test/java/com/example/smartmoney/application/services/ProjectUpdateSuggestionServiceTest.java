package com.example.smartmoney.application.services;

import com.example.smartmoney.application.dtos.responses.ChatResponse;
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
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.services.EnvelopeCalculator;
import com.example.smartmoney.domain.services.EnvelopeSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ProjectUpdateSuggestionService")
class ProjectUpdateSuggestionServiceTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final EnvelopeCalculator envelopeCalculator = mock(EnvelopeCalculator.class);
    private final ProjectUpdateSuggestionService service =
            new ProjectUpdateSuggestionService(
                    projectRepository, transactionRepository, userRepository, envelopeCalculator);

    private Project project(String name, BigDecimal moneySaved, BigDecimal moneyOwed) {
        return project(name, moneySaved, moneyOwed, BigDecimal.valueOf(1_000_000), 12);
    }

    private Project project(String name, BigDecimal moneySaved, BigDecimal moneyOwed,
                            BigDecimal monthlySaving, int monthLeft) {
        ProjectId projectId = ProjectId.newId();
        UserId ownerId = UserId.of(UUID.randomUUID().toString());
        ProjectTracking tracking = new ProjectTracking(
                UUID.randomUUID(), projectId, moneySaved, 1,
                monthlySaving, monthLeft, moneyOwed, 0,
                Instant.now(), Instant.now());
        // "Current progress" is read from Project.contributions (Project.totalContributed()),
        // not ProjectTracking.moneySaved, since AddProjectContributionUseCase (what a real
        // contribution confirm now calls) only ever writes contributions. Seed a matching
        // contribution here so this fixture's `moneySaved` still means "already saved so far"
        // the way every test below assumes, under the current (contributions-based) model.
        List<ProjectContribution> contributions = moneySaved.compareTo(BigDecimal.ZERO) > 0
                ? List.of(new ProjectContribution(ContributionId.newId(), ownerId,
                new Money(moneySaved, "VND"), null, Instant.now()))
                : List.of();
        Project project = Project.rehydrate(
                projectId, ownerId, ProjectType.PERSONAL,
                ProjectStatus.ACTIVE, ProjectPriority.HIGH, name, "desc",
                new Money(BigDecimal.valueOf(20_000_000), "VND"),
                LocalDate.now().plusMonths(Math.max(monthLeft, 1)), null, contributions, tracking, Instant.now());
        return project;
    }

    /**
     * Build a minimal {@link EnvelopeSnapshot} for a checkMonthEndLeftover test — only the
     * {@code realRemaining} field matters for the leftover gate; the other fields are zeroed.
     * Keeps the tests focused on ProjectUpdateSuggestionService's own thresholds rather than
     * re-testing envelope math (which has its own tests on EnvelopeCalculator).
     */
    private EnvelopeSnapshot snapshotWithRealRemaining(BigDecimal realRemaining) {
        return new EnvelopeSnapshot(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, realRemaining, false);
    }

    /**
     * Wire up the two dependencies checkMonthEndLeftover needs to reach its threshold check:
     * a user with a completed financial setup, and an envelope calculator returning the given
     * {@code realRemaining}. Tests that early-return via the days-left gate don't need this.
     */
    private void stubEnvelope(BigDecimal realRemaining) {
        User user = mock(User.class);
        when(user.hasCompletedFinancialSetup()).thenReturn(true);
        when(userRepository.findById(any())).thenReturn(Optional.of(user));
        when(envelopeCalculator.compute(any(User.class), any(), any()))
                .thenReturn(snapshotWithRealRemaining(realRemaining));
    }

    @Test
    @DisplayName("defaults to a moneySaved contribution suggestion when no debt keyword is present")
    void contributionSuggestion() {
        Project p = project("Mua nhà", BigDecimal.valueOf(5_000_000), BigDecimal.ZERO);
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));

        List<ChatResponse.SavingsPlanSuggestion> result =
                service.suggest(UUID.randomUUID(), "tôi vừa góp thêm 2 triệu vào dự án mua nhà");

        assertThat(result).hasSize(1);
        ChatResponse.SavingsPlanSuggestion s = result.get(0);
        assertThat(s.getCurrentMoneySaved()).isEqualTo(5_000_000L);
        assertThat(s.getSuggestedAddAmount()).isEqualTo(2_000_000L);
        assertThat(s.getNewMoneySaved()).isEqualTo(7_000_000L);
        assertThat(s.getCurrentMoneyOwed()).isNull();
    }

    @Test
    @DisplayName("increases moneyOwed when the message reports a new debt")
    void debtIncreaseSuggestion() {
        Project p = project("Sửa xe", BigDecimal.ZERO, BigDecimal.valueOf(1_000_000));
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));

        List<ChatResponse.SavingsPlanSuggestion> result =
                service.suggest(UUID.randomUUID(), "dự án sửa xe phát sinh thêm nợ 3 triệu");

        assertThat(result).hasSize(1);
        ChatResponse.SavingsPlanSuggestion s = result.get(0);
        assertThat(s.getCurrentMoneyOwed()).isEqualTo(1_000_000L);
        assertThat(s.getSuggestedMoneyOwed()).isEqualTo(4_000_000L);
        assertThat(s.getSuggestedAddAmount()).isNull();
    }

    @Test
    @DisplayName("decreases moneyOwed when the message reports a debt payoff")
    void debtDecreaseSuggestion() {
        Project p = project("Du lịch", BigDecimal.ZERO, BigDecimal.valueOf(2_000_000));
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));

        List<ChatResponse.SavingsPlanSuggestion> result =
                service.suggest(UUID.randomUUID(), "tôi đã trả bớt 1 triệu nợ dự án du lịch");

        assertThat(result).hasSize(1);
        ChatResponse.SavingsPlanSuggestion s = result.get(0);
        assertThat(s.getCurrentMoneyOwed()).isEqualTo(2_000_000L);
        assertThat(s.getSuggestedMoneyOwed()).isEqualTo(1_000_000L);
    }

    @Test
    @DisplayName("returns empty list when the user has no projects")
    void noProjects() {
        when(projectRepository.findAllByUser(any())).thenReturn(List.of());

        assertThat(service.suggest(UUID.randomUUID(), "góp thêm 2 triệu vào dự án")).isEmpty();
    }

    @Test
    @DisplayName("suggest() ignores a COMPLETED project even when it's the only one and is named explicitly")
    void ignoresCompletedProjectEvenWhenNamed() {
        ProjectId projectId = ProjectId.newId();
        ProjectTracking tracking = new ProjectTracking(
                UUID.randomUUID(), projectId, BigDecimal.valueOf(20_000_000), 1,
                BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, Instant.now(), Instant.now());
        Project completed = Project.rehydrate(
                projectId, UserId.of(UUID.randomUUID().toString()), ProjectType.PERSONAL,
                ProjectStatus.COMPLETED, ProjectPriority.HIGH, "Mua nhà", "desc",
                new Money(BigDecimal.valueOf(20_000_000), "VND"),
                LocalDate.now().plusMonths(12), null, List.of(), tracking, Instant.now());
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(completed));

        assertThat(service.suggest(UUID.randomUUID(), "tôi vừa góp thêm 2 triệu vào dự án mua nhà")).isEmpty();
    }

    // ── checkFeasibility ──────────────────────────────────────────────────────

    @Test
    @DisplayName("checkFeasibility flags a project whose monthlySaving exceeds 50% of income")
    void feasibilityFlagsOverCommittedProject() {
        // remaining = target(20M) - moneySaved(2M) = 18M, consistent with the original
        // monthlySaving(6M) * monthLeft(3) plan being replaced by a lower, sustainable rate.
        Project p = project("Mua nhà", BigDecimal.valueOf(2_000_000), BigDecimal.ZERO,
                BigDecimal.valueOf(6_000_000), 3);
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));

        var result = service.checkFeasibility(UUID.randomUUID(), BigDecimal.valueOf(10_000_000), false);

        assertThat(result).isPresent();
        assertThat(result.get().getSuggestedMonthLeft()).isGreaterThan(3);
    }

    @Test
    @DisplayName("checkFeasibility is empty when monthlySaving is a sustainable fraction of income")
    void feasibilityEmptyWhenSustainable() {
        Project p = project("Mua nhà", BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.valueOf(1_000_000), 12);
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));

        assertThat(service.checkFeasibility(UUID.randomUUID(), BigDecimal.valueOf(10_000_000), false))
                .isEmpty();
    }

    @Test
    @DisplayName("checkFeasibility is empty when income is unset")
    void feasibilityEmptyWhenIncomeUnset() {
        assertThat(service.checkFeasibility(UUID.randomUUID(), BigDecimal.ZERO, false)).isEmpty();
        assertThat(service.checkFeasibility(UUID.randomUUID(), null, false)).isEmpty();
    }

    // ── checkMonthEndLeftover ─────────────────────────────────────────────────
    //
    // These tests exercise the leftover-nudge THRESHOLDS specific to this service (days-left
    // gate + minimum leftover amount). The realRemaining figure they act on is produced by
    // EnvelopeCalculator — its own tests cover the envelope arithmetic (safeSpending, target
    // savings, actual-expense reconciliation), so here we stub it and focus on this service's
    // own logic. See {@code EnvelopeCalculatorTest} for the underlying math.

    @Test
    @DisplayName("checkMonthEndLeftover suggests topping up the top project near month-end with real leftover")
    void monthEndLeftoverFires() {
        Project p = project("Du lịch", BigDecimal.valueOf(3_000_000), BigDecimal.ZERO);
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));
        stubEnvelope(BigDecimal.valueOf(1_500_000));

        var result = service.checkMonthEndLeftover(UUID.randomUUID(), 3, false);

        assertThat(result).isPresent();
        assertThat(result.get().getSuggestedAddAmount()).isEqualTo(1_500_000L);
        assertThat(result.get().getNewMoneySaved()).isEqualTo(4_500_000L);
    }

    @Test
    @DisplayName("checkMonthEndLeftover is empty when more than a few days remain in the month")
    void monthEndLeftoverEmptyWhenNotNearMonthEnd() {
        // Early return on the days-left gate before ever touching user/envelope — no stubs needed.
        assertThat(service.checkMonthEndLeftover(UUID.randomUUID(), 10, false)).isEmpty();
    }

    @Test
    @DisplayName("checkMonthEndLeftover is empty when the leftover is too small to matter")
    void monthEndLeftoverEmptyWhenAmountTooSmall() {
        Project p = project("Du lịch", BigDecimal.ZERO, BigDecimal.ZERO);
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));
        stubEnvelope(BigDecimal.valueOf(100_000)); // below LEFTOVER_MIN_AMOUNT (500K)

        assertThat(service.checkMonthEndLeftover(UUID.randomUUID(), 2, false)).isEmpty();
    }

    @Test
    @DisplayName("checkMonthEndLeftover is empty when actual spending this month has already eaten the envelope " +
            "(deficit month) — realRemaining is zero, so no leftover suggestion fires")
    void monthEndLeftoverEmptyWhenActualSpendingExceedsSafeSpending() {
        UUID userId = UUID.randomUUID();
        Project p = project("Mua laptop", BigDecimal.ZERO, BigDecimal.ZERO);
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));
        // EnvelopeCalculator floors realRemaining at zero when actual expense exceeds envelope,
        // so the concrete "deficit month" case surfaces here as realRemaining = 0.
        stubEnvelope(BigDecimal.ZERO);

        assertThat(service.checkMonthEndLeftover(userId, 5, false)).isEmpty();
    }

    @Test
    @DisplayName("checkMonthEndLeftover suggests only the REAL unspent portion — the envelope calculator " +
            "already subtracted month-to-date actual expenses before returning realRemaining")
    void monthEndLeftoverUsesRealRemainingNotPlannedFigure() {
        UUID userId = UUID.randomUUID();
        Project p = project("Du lịch", BigDecimal.valueOf(1_000_000), BigDecimal.ZERO);
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));
        // Envelope was 5M, 2M already actually spent → realRemaining is 3M. The calculator does
        // that subtraction for us; the service just reads realRemaining and offers it.
        stubEnvelope(BigDecimal.valueOf(3_000_000));

        var result = service.checkMonthEndLeftover(userId, 5, false);

        assertThat(result).isPresent();
        assertThat(result.get().getSuggestedAddAmount()).isEqualTo(3_000_000L);
    }

    // ── checkSurplusTrend ─────────────────────────────────────────────────────

    @Test
    @DisplayName("checkSurplusTrend fires after 3 consecutive months clearing the surplus threshold")
    void surplusTrendFires() {
        Project p = project("Du lịch", BigDecimal.valueOf(1_000_000), BigDecimal.ZERO);
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));
        when(transactionRepository.getTotalIncomeBetween(any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(15_000_000));
        when(transactionRepository.getTotalExpenseBetween(any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(12_000_000));

        var result = service.checkSurplusTrend(UUID.randomUUID(), false);

        assertThat(result).isPresent();
        assertThat(result.get().getSuggestedAddAmount()).isEqualTo(3_000_000L);
    }

    @Test
    @DisplayName("checkSurplusTrend is empty when any of the last 3 months misses the threshold")
    void surplusTrendEmptyWhenStreakBroken() {
        Project p = project("Du lịch", BigDecimal.ZERO, BigDecimal.ZERO);
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));
        when(transactionRepository.getTotalIncomeBetween(any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(11_000_000));
        when(transactionRepository.getTotalExpenseBetween(any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(10_000_000));

        assertThat(service.checkSurplusTrend(UUID.randomUUID(), false)).isEmpty();
    }
}