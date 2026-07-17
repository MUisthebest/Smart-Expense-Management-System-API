package com.example.smartmoney.application.usecases.chat;

import com.example.smartmoney.application.dtos.requests.chat.ChatRequest;
import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.guardrails.JsonSchemaGuardrail;
import com.example.smartmoney.application.ports.chat.AIChatPort;
import com.example.smartmoney.application.ports.chat.ChatPendingActionPort;
import com.example.smartmoney.application.services.BudgetAdvisorService;
import com.example.smartmoney.application.services.ChatHistoryCacheService;
import com.example.smartmoney.application.services.ChatbotPromptBuilder;
import com.example.smartmoney.application.services.LlmRouter;
import com.example.smartmoney.application.services.ProjectHypothesisService;
import com.example.smartmoney.application.services.ProjectUpdateSuggestionService;
import com.example.smartmoney.application.services.SpendingSimulationService;
import com.example.smartmoney.application.services.UserFinancialDataService;
import com.example.smartmoney.application.services.WindfallAllocationService;
import com.example.smartmoney.application.usecases.budget.ComputeBudgetAllocationUseCase;
import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.ProjectTracking;
import com.example.smartmoney.domain.models.project.enums.ProjectPriority;
import com.example.smartmoney.domain.models.project.enums.ProjectStatus;
import com.example.smartmoney.domain.models.project.enums.ProjectType;
import com.example.smartmoney.domain.models.project.valueobjects.Money;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the GENERAL_ADVICE safety net: when the LLM classifies a message as GENERAL_ADVICE
 * but it still describes a large (>= 1,000,000 VND) income/expense event, suggestions must
 * still be attached instead of being silently dropped.
 */
@DisplayName("ChatWithAIUseCase.attachImplicitEventSuggestions")
class ChatWithAIUseCaseTest {

    // Pinned early in a 31-day month so the days-left month-end gate (suppresses current-month
    // BUDGET_UPDATE/PROJECT_UPDATE/PROJECT_HYPOTHESIS suggestions once <= 15 days remain) never
    // fires here — otherwise these tests would pass early in the month and fail after the 16th.
    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDate.of(2026, 1, 6).atStartOfDay(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault());

    private final BudgetRepository budgetRepository = mock(BudgetRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final WindfallAllocationService windfallAllocationService =
            new WindfallAllocationService(budgetRepository, projectRepository);

    private final ChatWithAIUseCase useCase = new ChatWithAIUseCase(
            mock(UserFinancialDataService.class),
            mock(LlmRouter.class),
            mock(BudgetAdvisorService.class),
            mock(SpendingSimulationService.class),
            windfallAllocationService,
            mock(ProjectUpdateSuggestionService.class),
            mock(ProjectHypothesisService.class),
            mock(ChatbotPromptBuilder.class),
            mock(UserRepository.class),
            mock(ChatHistoryCacheService.class),
            mock(JsonSchemaGuardrail.class),
            mock(ChatPendingActionPort.class), mock(ComputeBudgetAllocationUseCase.class), FIXED_CLOCK);

    private Budget budget(Category category, long limit, long spent) {
        return Budget.builder()
                .id(UUID.randomUUID())
                .category(category)
                .amountLimit(BigDecimal.valueOf(limit))
                .spent(BigDecimal.valueOf(spent))
                .build();
    }

    private Project project(String name) {
        return project(name, ProjectStatus.ACTIVE, BigDecimal.valueOf(2_000_000));
    }

    private Project project(String name, ProjectStatus status, BigDecimal moneySaved) {
        ProjectId projectId = ProjectId.newId();
        ProjectTracking tracking = new ProjectTracking(
                UUID.randomUUID(), projectId, moneySaved, 1,
                BigDecimal.valueOf(500_000), 12, BigDecimal.ZERO, 0,
                Instant.now(), Instant.now());
        return Project.rehydrate(
                projectId, UserId.of(UUID.randomUUID().toString()), ProjectType.PERSONAL,
                status, ProjectPriority.HIGH, name, "desc",
                new Money(BigDecimal.valueOf(20_000_000), "VND"),
                LocalDate.now().plusMonths(12), null, List.of(), tracking, Instant.now());
    }

    @Test
    @DisplayName("attaches budget + savings suggestions when message describes a large income event")
    void incomeEventAttachesSuggestions() {
        when(budgetRepository.findByUserIdMonthYear(any(), any(), any()))
                .thenReturn(List.of(budget(Category.FOOD, 3_000_000, 1_000_000)));
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(project("Mua nhà")));

        var builder = ChatResponse.builder().reply("ok").intent(ChatResponse.ChatIntent.GENERAL_ADVICE);
        useCase.attachImplicitEventSuggestions(builder, UUID.randomUUID(), 7, 2026,
                "tháng này tôi được thưởng 5 triệu, tôi nên làm gì?");

        ChatResponse response = builder.build();
        assertThat(response.getBudgetSuggestions()).isNotNull().isNotEmpty();
        assertThat(response.getSavingsSuggestions()).isNotNull().isNotEmpty();
        assertThat(response.isActionRequired()).isTrue();
    }

    @Test
    @DisplayName("attaches budget coverage + deferral suggestions for a large expense event")
    void expenseEventAttachesSuggestions() {
        when(budgetRepository.findByUserIdMonthYear(any(), any(), any()))
                .thenReturn(List.of(budget(Category.FOOD, 2_000_000, 1_900_000)));
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(project("Sửa xe")));

        var builder = ChatResponse.builder().reply("ok").intent(ChatResponse.ChatIntent.GENERAL_ADVICE);
        useCase.attachImplicitEventSuggestions(builder, UUID.randomUUID(), 7, 2026,
                "tôi vừa phải thanh toán viện phí 4 triệu");

        ChatResponse response = builder.build();
        assertThat(response.isActionRequired()).isTrue();
        assertThat(response.getBudgetSuggestions() != null || response.getSavingsSuggestions() != null)
                .isTrue();
    }

    @Test
    @DisplayName("expense event suggests idle funds from an ABANDONED project instead of deferring an ACTIVE one")
    void expenseEventPrefersAbandonedProjectFundsOverActiveDeferral() {
        when(budgetRepository.findByUserIdMonthYear(any(), any(), any())).thenReturn(List.of());
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(
                project("Du lịch", ProjectStatus.ABANDONED, BigDecimal.valueOf(3_000_000)),
                project("Mua xe", ProjectStatus.ACTIVE, BigDecimal.valueOf(2_000_000))));

        var builder = ChatResponse.builder().reply("ok").intent(ChatResponse.ChatIntent.GENERAL_ADVICE);
        useCase.attachImplicitEventSuggestions(builder, UUID.randomUUID(), 7, 2026,
                "tôi cần chi phí đột xuất 3 triệu để tổ chức tiệc sinh nhật");

        ChatResponse response = builder.build();
        assertThat(response.getSavingsSuggestions()).isNotNull();
        assertThat(response.getSavingsSuggestions())
                .anySatisfy(s -> {
                    assertThat(s.getProjectName()).isEqualTo("Du lịch");
                    assertThat(s.getSuggestedAddAmount()).isEqualTo(-3_000_000L);
                });
        // The idle abandoned-project funds fully cover the 3-triệu expense — the ACTIVE
        // "Mua xe" project must NOT also be deferred.
        assertThat(response.getSavingsSuggestions())
                .noneMatch(s -> "Mua xe".equals(s.getProjectName()));
    }

    @Test
    @DisplayName("does nothing when no qualifying amount is mentioned")
    void noAmountNoSuggestions() {
        var builder = ChatResponse.builder().reply("ok").intent(ChatResponse.ChatIntent.GENERAL_ADVICE);
        useCase.attachImplicitEventSuggestions(builder, UUID.randomUUID(), 7, 2026,
                "tôi nên tiết kiệm như thế nào?");

        ChatResponse response = builder.build();
        assertThat(response.getBudgetSuggestions()).isNull();
        assertThat(response.getSavingsSuggestions()).isNull();
        assertThat(response.isActionRequired()).isFalse();
    }

    @Test
    @DisplayName("does nothing when the amount is below the large-event threshold")
    void smallAmountNoSuggestions() {
        var builder = ChatResponse.builder().reply("ok").intent(ChatResponse.ChatIntent.GENERAL_ADVICE);
        useCase.attachImplicitEventSuggestions(builder, UUID.randomUUID(), 7, 2026,
                "hôm nay tôi được thưởng 50 nghìn");

        ChatResponse response = builder.build();
        assertThat(response.getBudgetSuggestions()).isNull();
        assertThat(response.getSavingsSuggestions()).isNull();
        assertThat(response.isActionRequired()).isFalse();
    }

    // ── execute(): LLM call failure falls back gracefully instead of throwing ───

    @Test
    @DisplayName("execute() falls back to a friendly reply when the LLM call throws, instead of a raw 500")
    void llmFailureFallsBackGracefully() {
        UserFinancialDataService dataProvider = mock(UserFinancialDataService.class);
        LlmRouter router = mock(LlmRouter.class);
        ChatHistoryCacheService historyService = mock(ChatHistoryCacheService.class);
        ChatbotPromptBuilder promptBuilder = mock(ChatbotPromptBuilder.class);
        UserRepository userRepo = mock(UserRepository.class);
        JsonSchemaGuardrail guardrail = mock(JsonSchemaGuardrail.class);
        AIChatPort failingAdapter = mock(AIChatPort.class);

        when(failingAdapter.chat(any(), any(), any())).thenThrow(new RuntimeException("Connection reset"));
        when(router.forMessage(any())).thenReturn(failingAdapter);
        when(dataProvider.buildChatContext(any(), anyInt(), anyInt())).thenReturn("");
        when(historyService.load(any())).thenReturn(List.of());
        when(userRepo.findById(any())).thenReturn(Optional.empty());

        ChatWithAIUseCase uc = new ChatWithAIUseCase(
                dataProvider, router, mock(BudgetAdvisorService.class), mock(SpendingSimulationService.class),
                windfallAllocationService, mock(ProjectUpdateSuggestionService.class),
                mock(ProjectHypothesisService.class), promptBuilder, userRepo, historyService, guardrail,
                mock(ChatPendingActionPort.class), mock(ComputeBudgetAllocationUseCase.class), FIXED_CLOCK);

        ChatResponse response = uc.execute(
                ChatRequest.builder().message("ngân sách tháng này của tôi thế nào?").build(),
                UUID.randomUUID());

        assertThat(response.getIntent()).isEqualTo(ChatResponse.ChatIntent.GENERAL_ADVICE);
        assertThat(response.getReply()).contains("AI");
        assertThat(response.isEnglish()).isFalse();
        verifyNoInteractions(guardrail);
        verify(historyService).appendAndCompress(any(), any(), any(), any());
    }

    @Test
    @DisplayName("execute(): an English message is stamped ChatResponse.english = true, "
            + "regardless of which branch built the response")
    void englishMessageStampsResponseAsEnglish() {
        UserFinancialDataService dataProvider = mock(UserFinancialDataService.class);
        LlmRouter router = mock(LlmRouter.class);
        ChatHistoryCacheService historyService = mock(ChatHistoryCacheService.class);
        UserRepository userRepo = mock(UserRepository.class);
        JsonSchemaGuardrail guardrail = mock(JsonSchemaGuardrail.class);
        AIChatPort failingAdapter = mock(AIChatPort.class);

        when(failingAdapter.chat(any(), any(), any())).thenThrow(new RuntimeException("Connection reset"));
        when(router.forMessage(any())).thenReturn(failingAdapter);
        when(dataProvider.buildChatContext(any(), anyInt(), anyInt())).thenReturn("");
        when(historyService.load(any())).thenReturn(List.of());
        when(userRepo.findById(any())).thenReturn(Optional.empty());

        ChatWithAIUseCase uc = new ChatWithAIUseCase(
                dataProvider, router, mock(BudgetAdvisorService.class), mock(SpendingSimulationService.class),
                windfallAllocationService, mock(ProjectUpdateSuggestionService.class),
                mock(ProjectHypothesisService.class), mock(ChatbotPromptBuilder.class), userRepo, historyService,
                guardrail, mock(ChatPendingActionPort.class), mock(ComputeBudgetAllocationUseCase.class), FIXED_CLOCK);

        ChatResponse response = uc.execute(
                ChatRequest.builder().message("how is my budget this month?").build(), UUID.randomUUID());

        assertThat(response.isEnglish()).isTrue();
    }

    // ── attachImplicitProjectHypothesis / structural-change priority ────────────

    @Test
    @DisplayName("attachImplicitProjectHypothesis attaches a SHORTEN suggestion for an explicit shorten request")
    void implicitProjectHypothesisAttachesShortenSuggestion() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        UserRepository hypothesisUserRepo = mock(UserRepository.class);
        var realHypothesisService = new com.example.smartmoney.application.services.ProjectHypothesisService(
                projectRepository, hypothesisUserRepo);

        Project p = project("Mua xe");
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));
        when(hypothesisUserRepo.findById(any())).thenReturn(java.util.Optional.empty());

        ChatWithAIUseCase uc = new ChatWithAIUseCase(
                mock(UserFinancialDataService.class), mock(LlmRouter.class), mock(BudgetAdvisorService.class),
                mock(SpendingSimulationService.class), windfallAllocationService,
                mock(ProjectUpdateSuggestionService.class), realHypothesisService, mock(ChatbotPromptBuilder.class),
                mock(UserRepository.class), mock(ChatHistoryCacheService.class), mock(JsonSchemaGuardrail.class),
                mock(ChatPendingActionPort.class), mock(ComputeBudgetAllocationUseCase.class), FIXED_CLOCK);

        var builder = ChatResponse.builder().reply("ok").intent(ChatResponse.ChatIntent.GENERAL_ADVICE);
        boolean handled = uc.attachImplicitProjectHypothesis(
                builder, UUID.randomUUID(), "tôi muốn rút ngắn dự án mua xe 3 tháng");

        assertThat(handled).isTrue();
        ChatResponse response = builder.build();
        assertThat(response.getProjectChangeSuggestions()).isNotNull().hasSize(1);
        assertThat(response.getProjectChangeSuggestions().get(0).getOperation())
                .isEqualTo(ChatResponse.ProjectChangeOperation.SHORTEN);
        assertThat(response.getPendingActionId()).isNotNull();
    }

    @Test
    @DisplayName("execute(): explicit shorten request wins over the 'mua xe' expense-event false-positive "
            + "and the proactive extend-only nudge — never contradicts what the user asked for")
    void explicitShortenRequestNotOverriddenByExpenseEventOrProactiveNudge() {
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        UserRepository hypothesisUserRepo = mock(UserRepository.class);
        var realHypothesisService = new com.example.smartmoney.application.services.ProjectHypothesisService(
                projectRepository, hypothesisUserRepo);
        Project p = project("Mua xe");
        when(projectRepository.findAllByUser(any())).thenReturn(List.of(p));
        when(hypothesisUserRepo.findById(any())).thenReturn(java.util.Optional.empty());

        UserFinancialDataService dataProvider = mock(UserFinancialDataService.class);
        when(dataProvider.buildChatContext(any(), anyInt(), anyInt())).thenReturn("");

        LlmRouter router = mock(LlmRouter.class);
        AIChatPort adapter = mock(AIChatPort.class);
        when(router.forMessage(any())).thenReturn(adapter);

        ChatHistoryCacheService historyService = mock(ChatHistoryCacheService.class);
        when(historyService.load(any())).thenReturn(List.of());

        JsonSchemaGuardrail guardrail = mock(JsonSchemaGuardrail.class);
        when(guardrail.validate(any(), any())).thenReturn(new JsonSchemaGuardrail.ParsedLlmResponse(
                ChatResponse.ChatIntent.GENERAL_ADVICE, "Đã hiểu, đây là góp ý của tôi.", List.of()));

        UserRepository userRepoMain = mock(UserRepository.class);
        when(userRepoMain.findById(any())).thenReturn(java.util.Optional.empty());

        ChatWithAIUseCase uc = new ChatWithAIUseCase(
                dataProvider, router, mock(BudgetAdvisorService.class), mock(SpendingSimulationService.class),
                windfallAllocationService, mock(ProjectUpdateSuggestionService.class), realHypothesisService,
                mock(ChatbotPromptBuilder.class), userRepoMain, historyService, guardrail,
                mock(ChatPendingActionPort.class), mock(ComputeBudgetAllocationUseCase.class), FIXED_CLOCK);

        // Contains BOTH the "mua xe" expense-event substring AND a large amount — without the
        // structural-change priority fix, this used to fire the windfall/expense safety net
        // (which always EXTENDS via suggestProjectDeferral) instead of the shorten the user asked for.
        ChatResponse response = uc.execute(
                ChatRequest.builder().message("tôi muốn rút ngắn dự án mua xe 200 triệu xuống 3 tháng").build(),
                UUID.randomUUID());

        assertThat(response.getProjectChangeSuggestions()).isNotNull().hasSize(1);
        assertThat(response.getProjectChangeSuggestions().get(0).getOperation())
                .isEqualTo(ChatResponse.ProjectChangeOperation.SHORTEN);
        assertThat(response.getSavingsSuggestions()).isNull();
        assertThat(response.getBudgetSuggestions()).isNull();
    }

    // ── BUDGET_UPDATE (cut) targets next month only ──────────────────────────

    @Test
    @DisplayName("execute(): a budget-cut request computes a next-month, per-category plan (never the current month)")
    void budgetCutTargetsNextMonthOnly() {
        UserFinancialDataService dataProvider = mock(UserFinancialDataService.class);
        when(dataProvider.buildChatContext(any(), anyInt(), anyInt())).thenReturn("");

        LlmRouter router = mock(LlmRouter.class);
        AIChatPort adapter = mock(AIChatPort.class);
        when(router.forMessage(any())).thenReturn(adapter);

        ChatHistoryCacheService historyService = mock(ChatHistoryCacheService.class);
        when(historyService.load(any())).thenReturn(List.of());

        JsonSchemaGuardrail guardrail = mock(JsonSchemaGuardrail.class);
        when(guardrail.validate(any(), any())).thenReturn(new JsonSchemaGuardrail.ParsedLlmResponse(
                ChatResponse.ChatIntent.BUDGET_UPDATE, "Đây là đề xuất ngân sách của tôi.", List.of()));

        UserRepository userRepoMain = mock(UserRepository.class);
        when(userRepoMain.findById(any())).thenReturn(java.util.Optional.empty());

        ComputeBudgetAllocationUseCase computeUseCase = mock(ComputeBudgetAllocationUseCase.class);
        LocalDate now = LocalDate.now(FIXED_CLOCK);
        LocalDate expectedNextMonth = now.plusMonths(1);
        var plan = com.example.smartmoney.application.dtos.responses.budgets.BudgetAllocationPlanResponse.builder()
                .month(expectedNextMonth.getMonthValue())
                .year(expectedNextMonth.getYear())
                .allocations(List.of(
                        com.example.smartmoney.application.dtos.responses.budgets.BudgetAllocationPlanResponse
                                .CategoryAllocation.builder()
                                .category(Category.FOOD).amount(BigDecimal.valueOf(2_500_000)).build(),
                        com.example.smartmoney.application.dtos.responses.budgets.BudgetAllocationPlanResponse
                                .CategoryAllocation.builder()
                                .category(Category.ENTERTAINMENT).amount(BigDecimal.valueOf(800_000)).build()))
                .build();
        when(computeUseCase.execute(any(), any(LocalDate.class))).thenReturn(plan);

        ChatWithAIUseCase uc = new ChatWithAIUseCase(
                dataProvider, router, mock(BudgetAdvisorService.class), mock(SpendingSimulationService.class),
                windfallAllocationService, mock(ProjectUpdateSuggestionService.class),
                mock(ProjectHypothesisService.class), mock(ChatbotPromptBuilder.class), userRepoMain,
                historyService, guardrail, mock(ChatPendingActionPort.class), computeUseCase, FIXED_CLOCK);

        ChatResponse response = uc.execute(
                ChatRequest.builder().message("tôi muốn cắt giảm ngân sách tháng này").build(), UUID.randomUUID());

        verify(computeUseCase).execute(any(), eq(expectedNextMonth.withDayOfMonth(1)));
        assertThat(response.getBudgetSuggestions()).hasSize(2);
        assertThat(response.getSuggestedMonth()).isEqualTo(expectedNextMonth.getMonthValue());
        assertThat(response.getSuggestedYear()).isEqualTo(expectedNextMonth.getYear());
        assertThat(response.getPendingActionId()).isNotNull();
    }

    // ── attachProactiveSuggestions asks first, doesn't attach a ready-to-confirm suggestion ──

    @Test
    @DisplayName("attachProactiveSuggestions appends a question to reply instead of attaching a structured suggestion")
    void proactiveNudgeAsksFirstInsteadOfAttaching() {
        BudgetRepository budgetRepo = mock(BudgetRepository.class);
        Budget nearLimitBudget = Budget.builder()
                .id(UUID.randomUUID()).category(Category.FOOD)
                .amountLimit(BigDecimal.valueOf(2_000_000)).spent(BigDecimal.valueOf(1_700_000)).build();
        when(budgetRepo.findByUserIdMonthYear(any(), anyInt(), anyInt())).thenReturn(List.of(nearLimitBudget));
        BudgetAdvisorService realBudgetAdvisor = new BudgetAdvisorService(budgetRepo);

        UserFinancialDataService dataProvider = mock(UserFinancialDataService.class);
        when(dataProvider.getFinancialSnapshot(any(), anyInt(), anyInt())).thenReturn(
                new UserFinancialDataService.FinancialSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, 5));

        ChatWithAIUseCase uc = new ChatWithAIUseCase(
                dataProvider, mock(LlmRouter.class), realBudgetAdvisor, mock(SpendingSimulationService.class),
                windfallAllocationService, mock(ProjectUpdateSuggestionService.class),
                mock(ProjectHypothesisService.class), mock(ChatbotPromptBuilder.class), mock(UserRepository.class),
                mock(ChatHistoryCacheService.class), mock(JsonSchemaGuardrail.class),
                mock(ChatPendingActionPort.class), mock(ComputeBudgetAllocationUseCase.class), FIXED_CLOCK);

        var builder = ChatResponse.builder().reply("Chào bạn, tôi có thể giúp gì?")
                .intent(ChatResponse.ChatIntent.GENERAL_ADVICE);
        uc.attachProactiveSuggestions(builder, "Chào bạn, tôi có thể giúp gì?",
                UUID.randomUUID(), 7, 2026, 5, "ngân sách của tôi thế nào?");

        ChatResponse response = builder.build();
        assertThat(response.getBudgetSuggestions()).isNull();
        assertThat(response.getSavingsSuggestions()).isNull();
        assertThat(response.isActionRequired()).isFalse();
        assertThat(response.getPendingActionId()).isNull();
        assertThat(response.getReply()).startsWith("Chào bạn, tôi có thể giúp gì?").contains("FOOD");
    }

    // ── Every explicit suggestion type now gets a pendingActionId ────────────

    @Test
    @DisplayName("execute(): PROJECT_UPDATE (contribution/debt) now attaches a pendingActionId, not just PROJECT_HYPOTHESIS/BUDGET_UPDATE")
    void projectUpdateContributionGetsPendingActionId() {
        ProjectRepository projectRepo = mock(ProjectRepository.class);
        Project p = project("Mua nhà");
        when(projectRepo.findAllByUser(any())).thenReturn(List.of(p));
        var realProjectUpdateService = new com.example.smartmoney.application.services.ProjectUpdateSuggestionService(
                projectRepo, mock(com.example.smartmoney.domain.repositories.TransactionRepository.class));

        UserFinancialDataService dataProvider = mock(UserFinancialDataService.class);
        when(dataProvider.buildChatContext(any(), anyInt(), anyInt())).thenReturn("");

        LlmRouter router = mock(LlmRouter.class);
        AIChatPort adapter = mock(AIChatPort.class);
        when(router.forMessage(any())).thenReturn(adapter);

        ChatHistoryCacheService historyService = mock(ChatHistoryCacheService.class);
        when(historyService.load(any())).thenReturn(List.of());

        JsonSchemaGuardrail guardrail = mock(JsonSchemaGuardrail.class);
        when(guardrail.validate(any(), any())).thenReturn(new JsonSchemaGuardrail.ParsedLlmResponse(
                ChatResponse.ChatIntent.PROJECT_UPDATE, "Đã ghi nhận khoản góp thêm.", List.of()));

        UserRepository userRepoMain = mock(UserRepository.class);
        when(userRepoMain.findById(any())).thenReturn(java.util.Optional.empty());

        ChatWithAIUseCase uc = new ChatWithAIUseCase(
                dataProvider, router, mock(BudgetAdvisorService.class), mock(SpendingSimulationService.class),
                windfallAllocationService, realProjectUpdateService, mock(ProjectHypothesisService.class),
                mock(ChatbotPromptBuilder.class), userRepoMain, historyService, guardrail,
                mock(ChatPendingActionPort.class), mock(ComputeBudgetAllocationUseCase.class), FIXED_CLOCK);

        ChatResponse response = uc.execute(
                ChatRequest.builder().message("tôi vừa góp thêm 2 triệu vào dự án mua nhà").build(),
                UUID.randomUUID());

        assertThat(response.getSavingsSuggestions()).isNotNull().hasSize(1);
        assertThat(response.getPendingActionId()).isNotNull();
    }

    // ── Abandoned/inactive project → clear reply instead of silent no-op ────────

    @Test
    @DisplayName("execute(): naming an ABANDONED project for a contribution gets a clear reply, not silence")
    void namedAbandonedProjectGetsClearReply() {
        ProjectRepository projectRepo = mock(ProjectRepository.class);
        ProjectId projectId = ProjectId.newId();
        ProjectTracking tracking = new ProjectTracking(
                UUID.randomUUID(), projectId, BigDecimal.ZERO, 1, BigDecimal.ZERO, 0,
                BigDecimal.ZERO, 0, Instant.now(), Instant.now());
        Project abandoned = Project.rehydrate(
                projectId, UserId.of(UUID.randomUUID().toString()),
                com.example.smartmoney.domain.models.project.enums.ProjectType.PERSONAL,
                ProjectStatus.ABANDONED, ProjectPriority.HIGH, "Du lịch", "desc",
                new Money(BigDecimal.valueOf(20_000_000), "VND"),
                LocalDate.now().plusMonths(12), null, List.of(), tracking, Instant.now());
        when(projectRepo.findAllByUser(any())).thenReturn(List.of(abandoned));
        var realProjectUpdateService = new com.example.smartmoney.application.services.ProjectUpdateSuggestionService(
                projectRepo, mock(com.example.smartmoney.domain.repositories.TransactionRepository.class));

        UserFinancialDataService dataProvider = mock(UserFinancialDataService.class);
        when(dataProvider.buildChatContext(any(), anyInt(), anyInt())).thenReturn("");

        LlmRouter router = mock(LlmRouter.class);
        AIChatPort adapter = mock(AIChatPort.class);
        when(router.forMessage(any())).thenReturn(adapter);

        ChatHistoryCacheService historyService = mock(ChatHistoryCacheService.class);
        when(historyService.load(any())).thenReturn(List.of());

        JsonSchemaGuardrail guardrail = mock(JsonSchemaGuardrail.class);
        when(guardrail.validate(any(), any())).thenReturn(new JsonSchemaGuardrail.ParsedLlmResponse(
                ChatResponse.ChatIntent.PROJECT_UPDATE, "Đã hiểu yêu cầu của bạn.", List.of()));

        UserRepository userRepoMain = mock(UserRepository.class);
        when(userRepoMain.findById(any())).thenReturn(Optional.empty());

        ChatWithAIUseCase uc = new ChatWithAIUseCase(
                dataProvider, router, mock(BudgetAdvisorService.class), mock(SpendingSimulationService.class),
                windfallAllocationService, realProjectUpdateService, mock(ProjectHypothesisService.class),
                mock(ChatbotPromptBuilder.class), userRepoMain, historyService, guardrail,
                mock(ChatPendingActionPort.class), mock(ComputeBudgetAllocationUseCase.class), FIXED_CLOCK);

        ChatResponse response = uc.execute(
                ChatRequest.builder().message("tôi vừa góp thêm 2 triệu vào dự án du lịch").build(),
                UUID.randomUUID());

        assertThat(response.getSavingsSuggestions()).isNull();
        assertThat(response.getPendingActionId()).isNull();
        assertThat(response.getReply()).contains("Du lịch").contains("đã bị hủy");
    }

    // ── Abnormal budget increase → tone-adjusted warning ─────────────────────

    @Test
    @DisplayName("execute(): an abnormally large budget-increase request gets an AGGRESSIVE-toned warning")
    void abnormalIncreaseGetsAggressiveWarning() {
        BudgetRepository budgetRepo = mock(BudgetRepository.class);
        Budget food = Budget.builder().id(UUID.randomUUID()).category(Category.FOOD)
                .amountLimit(BigDecimal.valueOf(1_000_000)).spent(BigDecimal.valueOf(500_000)).build();
        Budget entertainment = Budget.builder().id(UUID.randomUUID()).category(Category.ENTERTAINMENT)
                .amountLimit(BigDecimal.valueOf(1_000_000)).spent(BigDecimal.valueOf(100_000)).build();
        when(budgetRepo.findByUserIdMonthYear(any(), anyInt(), anyInt())).thenReturn(List.of(food, entertainment));
        var realBudgetAdvisor = new BudgetAdvisorService(budgetRepo);

        UserFinancialDataService dataProvider = mock(UserFinancialDataService.class);
        when(dataProvider.buildChatContext(any(), anyInt(), anyInt())).thenReturn("");

        LlmRouter router = mock(LlmRouter.class);
        AIChatPort adapter = mock(AIChatPort.class);
        when(router.forMessage(any())).thenReturn(adapter);

        ChatHistoryCacheService historyService = mock(ChatHistoryCacheService.class);
        when(historyService.load(any())).thenReturn(List.of());

        JsonSchemaGuardrail guardrail = mock(JsonSchemaGuardrail.class);
        when(guardrail.validate(any(), any())).thenReturn(new JsonSchemaGuardrail.ParsedLlmResponse(
                ChatResponse.ChatIntent.BUDGET_UPDATE, "Đây là đề xuất của tôi.", List.of()));

        User aggressiveUser = mock(User.class);
        when(aggressiveUser.getSavingPace()).thenReturn(Saving_Pace.AGGRESSIVE);
        UserRepository userRepoMain = mock(UserRepository.class);
        when(userRepoMain.findById(any())).thenReturn(Optional.of(aggressiveUser));

        ChatWithAIUseCase uc = new ChatWithAIUseCase(
                dataProvider, router, realBudgetAdvisor, mock(SpendingSimulationService.class),
                windfallAllocationService, mock(ProjectUpdateSuggestionService.class),
                mock(ProjectHypothesisService.class), mock(ChatbotPromptBuilder.class), userRepoMain,
                historyService, guardrail, mock(ChatPendingActionPort.class),
                mock(ComputeBudgetAllocationUseCase.class), FIXED_CLOCK);

        // Requested increase (3 triệu on a 1M FOOD limit) is a 4x jump — well past the 2x threshold.
        ChatResponse response = uc.execute(
                ChatRequest.builder().message("tôi muốn tăng ngân sách ăn thêm 3 triệu").build(),
                UUID.randomUUID());

        assertThat(response.getReply()).contains("Cảnh báo").contains("FOOD");
    }
}
