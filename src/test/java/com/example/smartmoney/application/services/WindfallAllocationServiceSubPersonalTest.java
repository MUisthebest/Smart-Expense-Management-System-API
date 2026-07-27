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
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the source-level sub-personal (group-linked) exclusion in
 * {@link WindfallAllocationService#suggestSavingsBoost}: a windfall lump sum should never be
 * proposed to a project with {@code groupProjectId != null}, because that would silently mutate
 * a member's share of a group commitment.
 *
 * <p>The orchestrator layer also strips these via
 * {@code ChatWithAIUseCase.stripGroupLinkedSavings}, but doing it here at source avoids wasted
 * allocation math and makes the intent visible in the service itself.
 */
@DisplayName("WindfallAllocationService sub-personal exclusion")
class WindfallAllocationServiceSubPersonalTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final BudgetRepository budgetRepository = mock(BudgetRepository.class);
    private final WindfallAllocationService service =
            new WindfallAllocationService(budgetRepository, projectRepository);

    private final UUID userId = UUID.randomUUID();

    private Project projectWith(String name, boolean subPersonal, ProjectPriority priority) {
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
                ProjectStatus.ACTIVE, priority, name, "desc",
                new Money(BigDecimal.valueOf(20_000_000), "VND"),
                LocalDate.now().plusMonths(12), groupProjectId, List.of(), tracking, Instant.now());
    }

    @Test
    @DisplayName("suggestSavingsBoost skips a HIGH-priority group-linked project even when it's the only candidate")
    void groupLinkedProjectSkippedEvenIfSoleCandidate() {
        // A group-linked project with HIGHEST priority — old code would greedily allocate the
        // whole windfall pool to it. New code sees the pool as empty and returns nothing.
        Project subPersonal = projectWith("Team Retreat", true, ProjectPriority.HIGH);
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(subPersonal));

        List<ChatResponse.SavingsPlanSuggestion> boosts = service.suggestSavingsBoost(
                userId, 6, 2026, "tôi được thưởng 10 triệu");

        assertThat(boosts).isEmpty();
    }

    @Test
    @DisplayName("with mixed candidates, only the standalone project receives the windfall pool")
    void mixedPoolOnlyAllocatesToStandalone() {
        Project subPersonal = projectWith("Team Retreat", true, ProjectPriority.HIGH);
        Project standalone = projectWith("Mua nhà", false, ProjectPriority.HIGH);
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(subPersonal, standalone));

        List<ChatResponse.SavingsPlanSuggestion> boosts = service.suggestSavingsBoost(
                userId, 6, 2026, "tôi được thưởng 10 triệu");

        // Standalone gets the allocation — the group-linked one is invisible to the greedy fill.
        assertThat(boosts).isNotEmpty();
        assertThat(boosts).extracting(ChatResponse.SavingsPlanSuggestion::getProjectName)
                .containsOnly("Mua nhà");
    }

    @Test
    @DisplayName("all-group user (unusual but possible) gets no savings boost at all — nothing safe to allocate to")
    void allGroupUserGetsNoBoosts() {
        // User has only sub-personal projects (all their savings goals are group commitments).
        // Historically we'd have allocated the whole windfall to their HIGH-priority group
        // project, silently altering a shared plan. Now: empty suggestion list, and the LLM
        // reply (per §5) directs the user through the Group screen.
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(
                projectWith("Team A", true, ProjectPriority.HIGH),
                projectWith("Team B", true, ProjectPriority.MEDIUM)));

        List<ChatResponse.SavingsPlanSuggestion> boosts = service.suggestSavingsBoost(
                userId, 6, 2026, "tôi được thưởng 10 triệu");

        assertThat(boosts).isEmpty();
    }
}