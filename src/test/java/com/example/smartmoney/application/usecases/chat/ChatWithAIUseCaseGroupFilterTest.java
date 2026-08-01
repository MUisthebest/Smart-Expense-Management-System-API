package com.example.smartmoney.application.usecases.chat;

import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.guardrails.JsonSchemaGuardrail;
import com.example.smartmoney.application.ports.chat.ChatPendingActionPort;
import com.example.smartmoney.application.services.BudgetAdvisorService;
import com.example.smartmoney.application.services.ChatHistoryCacheService;
import com.example.smartmoney.application.services.ChatbotPromptBuilder;
import com.example.smartmoney.application.services.LlmRouter;
import com.example.smartmoney.application.services.ProjectHypothesisService;
import com.example.smartmoney.application.services.ProjectUpdateSuggestionService;
import com.example.smartmoney.application.services.SpendingSimulationService;
import com.example.smartmoney.application.services.ProjectChangeService;
import com.example.smartmoney.application.services.GroupChangeService;
import com.example.smartmoney.application.services.TransactionChangeService;
import com.example.smartmoney.application.services.UserFinancialDataService;
import com.example.smartmoney.application.services.WindfallAllocationService;
import com.example.smartmoney.application.usecases.budget.ComputeBudgetAllocationUseCase;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the ChatWithAIUseCase changes that defend against two independent hallucination modes:
 * <ul>
 *   <li>Silent mutation of group-linked projects — verified through
 *       {@link ChatWithAIUseCase#} and
 *       {@link ChatWithAIUseCase#}, both fed by
 *       {@link ChatWithAIUseCase#}.</li>
 *   <li>Contradictory saving nudges during a deficit month — verified through
 *       {@link ChatWithAIUseCase#} and its integration with
 *       {@code attachProactiveSuggestions}.</li>
 *   <li>Follow-up chip re-injection for LARGE_EXPENSE — verified through
 *       {@link ChatWithAIUseCase#}.</li>
 * </ul>
 */
@DisplayName("ChatWithAIUseCase group-filter + deficit-gate + related-question suppression")
class ChatWithAIUseCaseGroupFilterTest {

    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final UserFinancialDataService dataProvider = mock(UserFinancialDataService.class);

    private final ChatWithAIUseCase useCase = new ChatWithAIUseCase(
            dataProvider,
            mock(LlmRouter.class),
            mock(BudgetAdvisorService.class),
            mock(SpendingSimulationService.class),
            mock(WindfallAllocationService.class),
            mock(ProjectUpdateSuggestionService.class),
            mock(ProjectHypothesisService.class),
            mock(TransactionChangeService.class),
            mock(ProjectChangeService.class),
            mock(GroupChangeService.class),
            mock(ChatbotPromptBuilder.class),
            mock(UserRepository.class),
            mock(ChatHistoryCacheService.class),
            mock(JsonSchemaGuardrail.class),
            mock(ChatPendingActionPort.class),
            mock(ComputeBudgetAllocationUseCase.class),
            projectRepository,
            Clock.systemDefaultZone());

    private final UUID userId = UUID.randomUUID();

    // ── Helpers ──────────────────────────────────────────────────────────────

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

    /** A CONTRIBUTION: positive suggestedAddAmount — the predicate toProjectSavingsAction uses to
     *  route to PROJECT_CONTRIBUTION_ADD, and the one group-linked mutation §5 permits. */
    private ChatResponse.SavingsPlanSuggestion savingsSuggestion(String projectId) {
        return ChatResponse.SavingsPlanSuggestion.builder()
                .projectId(projectId)
                .projectName("test")
                .currentMoneySaved(0L)
                .suggestedAddAmount(1_000_000L)
                .newMoneySaved(1_000_000L)
                .reason("test")
                .build();
    }

    /** A TRACKING EDIT: no positive add amount, so it routes to PROJECT_TRACKING_UPDATE — changes
     *  the shape of a commitment and stays blocked for group-linked projects. */
    private ChatResponse.SavingsPlanSuggestion trackingSuggestion(String projectId) {
        return ChatResponse.SavingsPlanSuggestion.builder()
                .projectId(projectId)
                .projectName("test")
                .currentMoneyOwed(500_000L)
                .suggestedMoneyOwed(0L)
                .suggestedMonthLeft(3)
                .reason("test")
                .build();
    }

    private ChatResponse.ProjectChangeSuggestion changeSuggestion(String projectId,
                                                                  ChatResponse.ProjectChangeOperation op) {
        return ChatResponse.ProjectChangeSuggestion.builder()
                .projectId(projectId)
                .projectName("test")
                .operation(op)
                .reason("test")
                .build();
    }

    // ── §A: loadSubPersonalProjectIds ────────────────────────────────────────

    @Nested
    @DisplayName("loadSubPersonalProjectIds")
    class LoadSubPersonalIds {

        @Test
        @DisplayName("returns only sub-personal project IDs from the user's project list")
        void filtersSubPersonalOnly() {
            Project standalone = projectWith("Mua nhà", false);
            Project subPersonal1 = projectWith("Team Retreat", true);
            Project subPersonal2 = projectWith("Team Dinner", true);
            when(projectRepository.findAllByUser(any())).thenReturn(
                    List.of(standalone, subPersonal1, subPersonal2));

            Set<String> ids = useCase.loadSubPersonalProjectIds(userId);

            assertThat(ids).containsExactlyInAnyOrder(
                    subPersonal1.getId().value().toString(),
                    subPersonal2.getId().value().toString());
        }

        @Test
        @DisplayName("returns empty set when user has no projects")
        void emptyWhenNoProjects() {
            when(projectRepository.findAllByUser(any())).thenReturn(List.of());

            assertThat(useCase.loadSubPersonalProjectIds(userId)).isEmpty();
        }

        @Test
        @DisplayName("returns empty set when user has only standalone projects (bulk of users)")
        void emptyWhenNoSubPersonal() {
            when(projectRepository.findAllByUser(any())).thenReturn(List.of(
                    projectWith("Mua nhà", false),
                    projectWith("Mua xe", false)));

            assertThat(useCase.loadSubPersonalProjectIds(userId)).isEmpty();
        }

        @Test
        @DisplayName("fail-safe: repository exception yields empty set, doesn't blow up the chat turn")
        void repoExceptionFallsBackToEmpty() {
            when(projectRepository.findAllByUser(any())).thenThrow(new RuntimeException("db down"));

            // Prompt + orchestrator + confirm-time guard are already three layers of defense;
            // failing open here means the confirm-time guard catches anything that slips past.
            // Failing CLOSED would make every chat turn error out on a transient DB blip.
            assertThat(useCase.loadSubPersonalProjectIds(userId)).isEmpty();
        }
    }

    // ── §B: stripGroupLinkedSavings ─────────────────────────────────────────

    @Nested
    @DisplayName("stripGroupLinkedSavings")
    class StripSavings {

        @Test
        @DisplayName("removes tracking-type entries targeting a group-linked project ID")
        void removesGroupLinkedEntries() {
            String groupId = UUID.randomUUID().toString();
            String personalId = UUID.randomUUID().toString();
            List<ChatResponse.SavingsPlanSuggestion> in = new ArrayList<>(List.of(
                    trackingSuggestion(personalId),
                    trackingSuggestion(groupId)));

            List<ChatResponse.SavingsPlanSuggestion> out =
                    useCase.stripGroupLinkedSavings(in, Set.of(groupId));

            assertThat(out).hasSize(1);
            assertThat(out.get(0).getProjectId()).isEqualTo(personalId);
        }

        @Test
        @DisplayName("KEEPS a contribution to the user's own group-linked project — §5 exception")
        void keepsOwnShareContributionForGroupLinked() {
            // Paying your own share into a group fund moves only the payer's money and only ever
            // increases group progress, so it needs no group consensus. Stripping it (the old
            // behaviour) dead-ended the group flow in chat: the assistant could describe the
            // shortfall but the user could do nothing about it. See §5 (A).
            String groupId = UUID.randomUUID().toString();
            List<ChatResponse.SavingsPlanSuggestion> in =
                    new ArrayList<>(List.of(savingsSuggestion(groupId)));

            List<ChatResponse.SavingsPlanSuggestion> out =
                    useCase.stripGroupLinkedSavings(in, Set.of(groupId));

            assertThat(out).hasSize(1);
            assertThat(out.get(0).getProjectId()).isEqualTo(groupId);
        }

        @Test
        @DisplayName("a group-linked project keeps its contribution but loses its tracking edit")
        void keepsContributionStripsTrackingForSameGroupProject() {
            String groupId = UUID.randomUUID().toString();
            List<ChatResponse.SavingsPlanSuggestion> in = new ArrayList<>(List.of(
                    savingsSuggestion(groupId),
                    trackingSuggestion(groupId)));

            List<ChatResponse.SavingsPlanSuggestion> out =
                    useCase.stripGroupLinkedSavings(in, Set.of(groupId));

            assertThat(out).hasSize(1);
            assertThat(out.get(0).getSuggestedAddAmount()).isPositive();
        }

        @Test
        @DisplayName("empty input returns the same empty list (identity for empty)")
        void emptyInputStaysEmpty() {
            List<ChatResponse.SavingsPlanSuggestion> empty = List.of();
            assertThat(useCase.stripGroupLinkedSavings(empty, Set.of("x"))).isSameAs(empty);
        }

        @Test
        @DisplayName("empty group-set returns input untouched (fast path when user has no group projects)")
        void emptyGroupSetShortCircuits() {
            List<ChatResponse.SavingsPlanSuggestion> in =
                    List.of(savingsSuggestion(UUID.randomUUID().toString()));
            assertThat(useCase.stripGroupLinkedSavings(in, Set.of())).isSameAs(in);
        }

        @Test
        @DisplayName("null input returned as-is (defensive) — never dereferences null")
        void nullInputReturnedAsIs() {
            assertThat(useCase.stripGroupLinkedSavings(null, Set.of("x"))).isNull();
        }

        @Test
        @DisplayName("entry with null projectId is preserved (shouldn't happen but the guard is cheap)")
        void nullProjectIdPreserved() {
            ChatResponse.SavingsPlanSuggestion nullId = ChatResponse.SavingsPlanSuggestion.builder()
                    .projectId(null).projectName("weird").build();
            List<ChatResponse.SavingsPlanSuggestion> in = new ArrayList<>(List.of(nullId));

            List<ChatResponse.SavingsPlanSuggestion> out =
                    useCase.stripGroupLinkedSavings(in, Set.of("x"));

            assertThat(out).hasSize(1);
        }
    }

    // ── §C: stripGroupLinkedProjectChanges ──────────────────────────────────

    @Nested
    @DisplayName("stripGroupLinkedProjectChanges")
    class StripProjectChanges {

        @Test
        @DisplayName("removes SHORTEN/EXTEND/DELETE entries for a group-linked project")
        void removesShortenExtendDeleteForGroup() {
            String groupId = UUID.randomUUID().toString();
            String personalId = UUID.randomUUID().toString();
            List<ChatResponse.ProjectChangeSuggestion> in = new ArrayList<>(List.of(
                    changeSuggestion(personalId, ChatResponse.ProjectChangeOperation.SHORTEN),
                    changeSuggestion(groupId, ChatResponse.ProjectChangeOperation.EXTEND),
                    changeSuggestion(groupId, ChatResponse.ProjectChangeOperation.DELETE)));

            List<ChatResponse.ProjectChangeSuggestion> out =
                    useCase.stripGroupLinkedProjectChanges(in, Set.of(groupId));

            assertThat(out).hasSize(1);
            assertThat(out.get(0).getProjectId()).isEqualTo(personalId);
            assertThat(out.get(0).getOperation()).isEqualTo(ChatResponse.ProjectChangeOperation.SHORTEN);
        }

        @Test
        @DisplayName("CREATE with null projectId is ALWAYS preserved (new project can't be group-linked)")
        void createEntryAlwaysPreserved() {
            ChatResponse.ProjectChangeSuggestion create = ChatResponse.ProjectChangeSuggestion.builder()
                    .projectId(null)
                    .projectName("new project")
                    .operation(ChatResponse.ProjectChangeOperation.CREATE)
                    .build();
            List<ChatResponse.ProjectChangeSuggestion> in = new ArrayList<>(List.of(create));

            // Even with an aggressive filter set that would have matched every entry with a real
            // id, CREATE has null projectId and slips through as designed.
            List<ChatResponse.ProjectChangeSuggestion> out =
                    useCase.stripGroupLinkedProjectChanges(in, Set.of("some-id", "another-id"));

            assertThat(out).hasSize(1);
            assertThat(out.get(0).getOperation()).isEqualTo(ChatResponse.ProjectChangeOperation.CREATE);
        }

        @Test
        @DisplayName("mixed batch (bulk delete) strips group entries but keeps personal ones")
        void bulkDeleteBatchStripsPartial() {
            String groupId = UUID.randomUUID().toString();
            String personalA = UUID.randomUUID().toString();
            String personalB = UUID.randomUUID().toString();
            List<ChatResponse.ProjectChangeSuggestion> in = new ArrayList<>(List.of(
                    changeSuggestion(personalA, ChatResponse.ProjectChangeOperation.DELETE),
                    changeSuggestion(groupId, ChatResponse.ProjectChangeOperation.DELETE),
                    changeSuggestion(personalB, ChatResponse.ProjectChangeOperation.DELETE)));

            List<ChatResponse.ProjectChangeSuggestion> out =
                    useCase.stripGroupLinkedProjectChanges(in, Set.of(groupId));

            assertThat(out).extracting(ChatResponse.ProjectChangeSuggestion::getProjectId)
                    .containsExactly(personalA, personalB);
        }
    }

    // ── §D: monthInDeficit ──────────────────────────────────────────────────

    @Nested
    @DisplayName("monthInDeficit")
    class DeficitGate {

        @Test
        @DisplayName("returns true when classifier says DEFICIT")
        void trueOnDeficit() {
            when(dataProvider.classifyCashFlow(userId, 6, 2026))
                    .thenReturn(UserFinancialDataService.CashFlowStatus.DEFICIT);
            assertThat(useCase.monthInDeficit(userId, 6, 2026)).isTrue();
        }

        @Test
        @DisplayName("returns false for BALANCED / SURPLUS / UNKNOWN")
        void falseForNonDeficitStates() {
            when(dataProvider.classifyCashFlow(userId, 6, 2026))
                    .thenReturn(UserFinancialDataService.CashFlowStatus.BALANCED);
            assertThat(useCase.monthInDeficit(userId, 6, 2026)).isFalse();

            when(dataProvider.classifyCashFlow(userId, 6, 2026))
                    .thenReturn(UserFinancialDataService.CashFlowStatus.SURPLUS);
            assertThat(useCase.monthInDeficit(userId, 6, 2026)).isFalse();

            when(dataProvider.classifyCashFlow(userId, 6, 2026))
                    .thenReturn(UserFinancialDataService.CashFlowStatus.UNKNOWN);
            assertThat(useCase.monthInDeficit(userId, 6, 2026)).isFalse();
        }

        @Test
        @DisplayName("fail-safe: classifier exception yields false (allow suggestions, don't blow up)")
        void classifierExceptionYieldsFalse() {
            when(dataProvider.classifyCashFlow(any(), anyInt(), anyInt()))
                    .thenThrow(new RuntimeException("db down"));

            // Same reasoning as loadSubPersonalProjectIds: infrastructure failure shouldn't
            // silently strip a legitimate proactive nudge, but the LLM's own reply is still
            // required to warn about deficit by §3 Rule A — so worst case is a duplicate
            // reminder, not a wrong recommendation.
            assertThat(useCase.monthInDeficit(userId, 6, 2026)).isFalse();
        }
    }

    // ── §E: intentSuppressesRelatedQuestions ────────────────────────────────

    @Nested
    @DisplayName("intentSuppressesRelatedQuestions")
    class RelatedQuestionsGate {

        @Test
        @DisplayName("LARGE_EXPENSE never gets follow-up chips (assessment intent, no natural next step)")
        void largeExpenseSuppressed() {
            assertThat(ChatWithAIUseCase.intentSuppressesRelatedQuestions(
                    ChatResponse.ChatIntent.LARGE_EXPENSE)).isTrue();
        }

        @Test
        @DisplayName("OUT_OF_SCOPE never gets follow-up chips (was existing rule, preserved)")
        void outOfScopeSuppressed() {
            assertThat(ChatWithAIUseCase.intentSuppressesRelatedQuestions(
                    ChatResponse.ChatIntent.OUT_OF_SCOPE)).isTrue();
        }

        @Test
        @DisplayName("all other intents keep their chips (regression guard: only LARGE_EXPENSE + OUT_OF_SCOPE added)")
        void otherIntentsNotSuppressed() {
            for (ChatResponse.ChatIntent intent : ChatResponse.ChatIntent.values()) {
                if (intent == ChatResponse.ChatIntent.LARGE_EXPENSE
                        || intent == ChatResponse.ChatIntent.OUT_OF_SCOPE) continue;
                assertThat(ChatWithAIUseCase.intentSuppressesRelatedQuestions(intent))
                        .as("intent %s should keep follow-up chips", intent)
                        .isFalse();
            }
        }
    }
}