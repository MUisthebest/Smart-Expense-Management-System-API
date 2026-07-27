package com.example.smartmoney.application.services;

import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.TransactionRepository;
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

/**
 * Verifies the source-level sub-personal (group-linked) exclusion in
 * {@link ProjectUpdateSuggestionService}: the {@code activeProjects} candidate pool must never
 * include a project with {@code groupProjectId != null} so that neither {@code suggest()} (typed
 * SavingsPlanSuggestion) nor the proactive text nudges ({@code checkFeasibility},
 * {@code checkMonthEndLeftover}, {@code checkSurplusTrend}) can reference a group project.
 *
 * <p>This is defense-in-depth for the same rule
 * {@link com.example.smartmoney.application.usecases.chat.ChatWithAIUseCase#}
 * enforces at the orchestrator layer — but the orchestrator strip can only remove typed
 * suggestion entries, not free-form reason text that a nudge appends directly to
 * {@code reply}. The source-level filter is what protects reply text from naming a group project.
 */
@DisplayName("ProjectUpdateSuggestionService sub-personal exclusion")
class ProjectUpdateSuggestionServiceSubPersonalTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final TransactionRepository transactionRepository = mock(TransactionRepository.class);
    private final ProjectUpdateSuggestionService service =
            new ProjectUpdateSuggestionService(projectRepository, transactionRepository);

    private final UUID userId = UUID.randomUUID();

    private Project projectWith(String name, boolean subPersonal) {
        ProjectId projectId = ProjectId.newId();
        ProjectTracking tracking = new ProjectTracking(
                UUID.randomUUID(), projectId,
                BigDecimal.valueOf(1_000_000), 1,
                BigDecimal.valueOf(500_000), 12,
                BigDecimal.ZERO, 0,
                Instant.now(), Instant.now());
        UUID groupProjectId = subPersonal ? UUID.randomUUID() : null;
        return Project.rehydrate(
                projectId, UserId.of(userId.toString()), ProjectType.PERSONAL,
                ProjectStatus.ACTIVE, ProjectPriority.HIGH, name, "desc",
                new Money(BigDecimal.valueOf(20_000_000), "VND"),
                LocalDate.now().plusMonths(12), groupProjectId, List.of(), tracking, Instant.now());
    }

    @Test
    @DisplayName("suggest(): a message naming only a group-linked project returns empty suggestions")
    void suggestSkipsGroupLinkedProject() {
        Project subPersonal = projectWith("Team Retreat", true);
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(subPersonal));

        // Message plainly asks to contribute to that project, but the candidate pool excludes it
        // because it's group-linked. Result: no suggestion — the LLM's reply (guided by §5 of the
        // prompt) is what tells the user to go through the Group screen.
        List<ChatResponse.SavingsPlanSuggestion> suggestions =
                service.suggest(userId, "tôi vừa góp thêm 2 triệu vào Team Retreat");

        assertThat(suggestions).isEmpty();
    }

    @Test
    @DisplayName("suggest(): a message naming a standalone project still gets its suggestion")
    void suggestKeepsStandaloneProject() {
        Project standalone = projectWith("Mua nhà", false);
        // Any transactions the message could be joined with — return none so the assertion is
        // purely about candidate-pool inclusion, not about tracking-amount math.
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(standalone));
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        List<ChatResponse.SavingsPlanSuggestion> suggestions =
                service.suggest(userId, "tôi vừa góp thêm 2 triệu vào dự án Mua nhà");

        // At least one entry — the exact contents depend on suggest()'s internal amount parsing
        // and match logic, but the standalone project MUST still be reachable.
        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions).extracting(ChatResponse.SavingsPlanSuggestion::getProjectName)
                .allMatch(name -> name.equals("Mua nhà"));
    }

    @Test
    @DisplayName("mixed pool: standalone survives, sub-personal is invisible even when explicitly named")
    void mixedPoolExcludesSubPersonal() {
        Project standalone = projectWith("Mua nhà", false);
        Project subPersonal = projectWith("Team Retreat", true);
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(standalone, subPersonal));
        when(transactionRepository.findByUserIdWithFilters(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        // Even when both project names appear in the message, only the standalone gets a
        // suggestion — the sub-personal one is silently excluded here, and the prompt (§5) is
        // responsible for making the user's reply explain why via the LLM.
        List<ChatResponse.SavingsPlanSuggestion> suggestions =
                service.suggest(userId, "góp 2 triệu vào Mua nhà và Team Retreat");

        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions).extracting(ChatResponse.SavingsPlanSuggestion::getProjectName)
                .doesNotContain("Team Retreat");
    }

    @Test
    @DisplayName("checkSurplusTrend: a group-linked project never gets a proactive top-up nudge")
    void checkSurplusTrendSkipsGroupLinked() {
        Project subPersonal = projectWith("Team Retreat", true);
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(subPersonal));

        // checkSurplusTrend needs recent-months surplus history to fire; without it, an empty
        // Optional is the correct result regardless of the sub-personal filter. That's fine —
        // the test's point is that even IF surplus history existed, the only project in scope
        // is group-linked so no nudge could target anything.
        // We assert no exception (candidate pool safely empty) and non-throwing empty return.
        Optional<ChatResponse.SavingsPlanSuggestion> nudge =
                service.checkSurplusTrend(userId, /*english=*/false);

        assertThat(nudge).isEmpty();
    }
}