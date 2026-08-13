package com.example.smartmoney.application.services;

import com.example.smartmoney.application.dtos.chat.PendingChatAction;
import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.guardrails.JsonSchemaGuardrail;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import com.example.smartmoney.domain.services.ProjectPacePlanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the bug this service was written for: a personal project the user "created" through the
 * chatbot never appeared in {@code GET /api/v1/projects}, because a conversational create produced
 * no confirm card at all and therefore never ran {@code CreateProjectUseCase}.
 *
 * <p>Clock is pinned so the deadline arithmetic (and the 60-month window) is deterministic.
 */
@DisplayName("ProjectChangeService")
class ProjectChangeServiceTest {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 1);

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(
            TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    private final ProjectChangeService service =
            new ProjectChangeService(projectRepository, userRepository, clock);

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(projectRepository.findAllByUser(any(UserId.class))).thenReturn(List.of());
        User user = userWithCapacity(BigDecimal.valueOf(20_000_000));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private User userWithCapacity(BigDecimal capacity) {
        User user = mock(User.class);
        when(user.hasCompletedFinancialSetup()).thenReturn(true);
        when(user.getSavingCapacity()).thenReturn(capacity);
        when(user.calculateSafeSpending(any())).thenReturn(BigDecimal.valueOf(4_000_000));
        return user;
    }

    private JsonSchemaGuardrail.ProjectDraft createDraft(boolean complete) {
        return new JsonSchemaGuardrail.ProjectDraft(
                "CREATE", null, "Mua xe", null, BigDecimal.valueOf(120_000_000), "VND",
                DMY.format(TODAY.plusMonths(11)), null, null, complete);
    }

    @Test
    @DisplayName("a complete create draft yields a PROJECT_CREATE action carrying every field the use case needs")
    void completeCreateDraftBecomesAConfirmableAction() {
        var preview = service.preview(userId, createDraft(true), "tạo dự án mua xe");

        assertThat(preview).isPresent();
        PendingChatAction action = preview.get().action();
        assertThat(action.getType()).isEqualTo(PendingChatAction.Type.PROJECT_CREATE);
        assertThat(action.getName()).isEqualTo("Mua xe");
        assertThat(action.getTargetAmount()).isEqualByComparingTo("120000000");
        assertThat(action.getCurrency()).isEqualTo("VND");
        assertThat(action.getNewDeadline()).isEqualTo(TODAY.plusMonths(11));
        // Priority is filled in even though the user never named one — CreateProjectUseCase
        // requires a free priority and rejects a clash, so leaving it null would fail at confirm.
        assertThat(action.getPriority()).isEqualTo(ProjectPriority.LOW.name());

        ChatResponse.ProjectChangeSuggestion suggestion = preview.get().suggestion();
        assertThat(suggestion.getOperation()).isEqualTo(ChatResponse.ProjectChangeOperation.CREATE);
        assertThat(suggestion.getProjectId()).isNull();
        assertThat(suggestion.getNewMonthlySaving()).isEqualTo(10_000_000L);
    }

    @Test
    @DisplayName("an incomplete draft yields nothing, so the assistant keeps asking instead of half-creating")
    void incompleteDraftYieldsNoCard() {
        assertThat(service.preview(userId, createDraft(false), "tạo dự án mua xe")).isEmpty();
    }

    @Test
    @DisplayName("a draft missing the deadline yields nothing even when marked complete")
    void missingDeadlineYieldsNoCard() {
        var draft = new JsonSchemaGuardrail.ProjectDraft(
                "CREATE", null, "Mua xe", null, BigDecimal.valueOf(120_000_000), "VND",
                null, null, null, true);
        assertThat(service.preview(userId, draft, "tạo dự án mua xe")).isEmpty();
    }

    @Test
    @DisplayName("null draft (every non-project turn) yields nothing")
    void nullDraftYieldsNoCard() {
        assertThat(service.preview(userId, null, "tôi tiêu bao nhiêu tháng này")).isEmpty();
    }

    @Test
    @DisplayName("ISO deadlines are accepted too, since the model emits them when writing English")
    void isoDeadlineIsAccepted() {
        var draft = new JsonSchemaGuardrail.ProjectDraft(
                "CREATE", null, "New car", null, BigDecimal.valueOf(120_000_000), "VND",
                TODAY.plusMonths(11).toString(), null, null, true);
        var preview = service.preview(userId, draft, "create a project to buy a new car");
        assertThat(preview).isPresent();
        assertThat(preview.get().action().getNewDeadline()).isEqualTo(TODAY.plusMonths(11));
    }

    @Test
    @DisplayName("a past deadline is refused rather than silently shifted forward")
    void pastDeadlineYieldsNoCard() {
        var draft = new JsonSchemaGuardrail.ProjectDraft(
                "CREATE", null, "Mua xe", null, BigDecimal.valueOf(120_000_000), "VND",
                DMY.format(TODAY.minusDays(1)), null, null, true);
        assertThat(service.preview(userId, draft, "tạo dự án mua xe")).isEmpty();
    }

    @Test
    @DisplayName("the 3-live-project cap is pre-checked, so the card never fails at confirm time")
    void liveProjectCapIsPreChecked() {
        // Built before the when(...) call — stubbing a mock inside an unfinished stubbing throws.
        List<Project> threeLive = List.of(
                liveProject("A", ProjectPriority.HIGH),
                liveProject("B", ProjectPriority.MEDIUM),
                liveProject("C", ProjectPriority.LOW));
        when(projectRepository.findAllByUser(any(UserId.class))).thenReturn(threeLive);

        assertThat(service.preview(userId, createDraft(true), "tạo dự án mua xe")).isEmpty();
    }

    @Test
    @DisplayName("a commitment beyond the user's saving capacity is refused up front")
    void overCapacityIsPreChecked() {
        User poorUser = userWithCapacity(BigDecimal.valueOf(1_000_000));
        when(userRepository.findById(userId)).thenReturn(Optional.of(poorUser));

        // 120M over 12 months is 10M/month, well past a 1M capacity.
        assertThat(service.preview(userId, createDraft(true), "tạo dự án mua xe")).isEmpty();
    }

    @Test
    @DisplayName("a user who hasn't finished financial setup gets no card — CreateProjectUseCase would reject it")
    void incompleteFinancialSetupIsPreChecked() {
        User user = mock(User.class);
        when(user.hasCompletedFinancialSetup()).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThat(service.preview(userId, createDraft(true), "tạo dự án mua xe")).isEmpty();
    }

    // ── The urgent/relaxed pace, the way the create screen actually works ────

    private JsonSchemaGuardrail.ProjectDraft paceDraft(String pace) {
        return new JsonSchemaGuardrail.ProjectDraft(
                "CREATE", null, "Mua xe", null, BigDecimal.valueOf(120_000_000), "VND",
                null, null, pace, true);
    }

    @Test
    @DisplayName("URGENT spends the whole free capacity, so the horizon is computed — no deadline asked for")
    void urgentPacePlansTheHorizon() {
        var preview = service.preview(userId, paceDraft("URGENT"), "tạo dự án mua xe, làm gấp");

        assertThat(preview).isPresent();
        // 120M at the full 20M/month capacity → 6 months, ending 5 months out (months are
        // inclusive of the current one, exactly as CreateProjectUseCase counts them).
        assertThat(preview.get().suggestion().getNewMonthlySaving()).isEqualTo(20_000_000L);
        assertThat(preview.get().action().getNewDeadline()).isEqualTo(TODAY.plusMonths(5));
    }

    @Test
    @DisplayName("RELAXED spends half of it, so the same target takes twice as long")
    void relaxedPaceHalvesTheMonthlyAmount() {
        var preview = service.preview(userId, paceDraft("RELAXED"), "tạo dự án mua xe, từ từ thôi");

        assertThat(preview).isPresent();
        // 120M at 10M/month → 12 months.
        assertThat(preview.get().suggestion().getNewMonthlySaving()).isEqualTo(10_000_000L);
        assertThat(preview.get().action().getNewDeadline()).isEqualTo(TODAY.plusMonths(11));
    }

    @Test
    @DisplayName("the planned pace matches what the budget-advisor screen returns for the same input")
    void plannedPaceMatchesTheAdvisorScreen() {
        var plan = ProjectPacePlanner.plan(BigDecimal.valueOf(120_000_000),
                BigDecimal.valueOf(20_000_000), ProjectPacePlanner.Mode.RELAXED);
        var preview = service.preview(userId, paceDraft("RELAXED"), "tạo dự án mua xe");

        assertThat(preview).isPresent();
        assertThat(preview.get().suggestion().getNewMonthlySaving())
                .isEqualTo(plan.monthlySaving().longValue());
    }

    // ── Blocked drafts explain themselves instead of vanishing ──────────────

    @Test
    @DisplayName("hitting the project cap produces a note, so the reply can't claim the project was created")
    void cappedCreateExplainsWhy() {
        List<Project> threeLive = List.of(
                liveProject("A", ProjectPriority.HIGH),
                liveProject("B", ProjectPriority.MEDIUM),
                liveProject("C", ProjectPriority.LOW));
        when(projectRepository.findAllByUser(any(UserId.class))).thenReturn(threeLive);

        var outcome = service.evaluate(userId, createDraft(true), "tạo dự án mua xe");

        assertThat(outcome.preview()).isNull();
        assertThat(outcome.rejection())
                .isEqualTo(ProjectChangeService.Rejection.MAX_LIVE_PROJECTS);
        assertThat(outcome.note()).contains("3 dự án");
    }

    @Test
    @DisplayName("an over-capacity target is explained with the horizon that would fit")
    void overCapacityExplainsTheFeasibleHorizon() {
        User poorUser = userWithCapacity(BigDecimal.valueOf(5_000_000));
        when(userRepository.findById(userId)).thenReturn(Optional.of(poorUser));

        // 120M by TODAY+11 is 10M/month against a 5M capacity; 24 months would fit.
        var outcome = service.evaluate(userId, createDraft(true), "tạo dự án mua xe");

        assertThat(outcome.rejection()).isEqualTo(ProjectChangeService.Rejection.OVER_CAPACITY);
        assertThat(outcome.note()).contains("24 tháng");
    }

    @Test
    @DisplayName("an unfinished financial setup is named as the blocker rather than dropped")
    void missingFinancialSetupExplainsWhy() {
        User user = mock(User.class);
        when(user.hasCompletedFinancialSetup()).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        var outcome = service.evaluate(userId, createDraft(true), "tạo dự án mua xe");

        assertThat(outcome.rejection()).isEqualTo(ProjectChangeService.Rejection.NO_FINANCIAL_SETUP);
        assertThat(outcome.note()).contains("thiết lập tài chính");
    }

    @Test
    @DisplayName("a draft still being collected carries no note — the assistant's own question is the answer")
    void incompleteDraftCarriesNoNote() {
        var outcome = service.evaluate(userId, createDraft(false), "tạo dự án mua xe");

        assertThat(outcome.rejection()).isEqualTo(ProjectChangeService.Rejection.INCOMPLETE);
        assertThat(outcome.note()).isNull();
    }

    private Project liveProject(String name, ProjectPriority priority) {
        Project p = mock(Project.class);
        when(p.getStatus()).thenReturn(ProjectStatus.ACTIVE);
        when(p.getPriority()).thenReturn(priority);
        when(p.getName()).thenReturn(name);
        return p;
    }
}
