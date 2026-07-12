package com.example.smartmoney.application.usecases.chat;

import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.guardrails.JsonSchemaGuardrail;
import com.example.smartmoney.application.services.BudgetAdvisorService;
import com.example.smartmoney.application.services.ChatHistoryCacheService;
import com.example.smartmoney.application.services.ChatbotPromptBuilder;
import com.example.smartmoney.application.services.LlmRouter;
import com.example.smartmoney.application.services.ProjectUpdateSuggestionService;
import com.example.smartmoney.application.services.SpendingSimulationService;
import com.example.smartmoney.application.services.UserFinancialDataService;
import com.example.smartmoney.application.services.WindfallAllocationService;
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
import com.example.smartmoney.domain.repositories.BudgetRepository;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
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
 * Covers the GENERAL_ADVICE safety net: when the LLM classifies a message as GENERAL_ADVICE
 * but it still describes a large (>= 1,000,000 VND) income/expense event, suggestions must
 * still be attached instead of being silently dropped.
 */
@DisplayName("ChatWithAIUseCase.attachImplicitEventSuggestions")
class ChatWithAIUseCaseTest {

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
            mock(ChatbotPromptBuilder.class),
            mock(UserRepository.class),
            mock(ChatHistoryCacheService.class),
            mock(JsonSchemaGuardrail.class));

    private Budget budget(Category category, long limit, long spent) {
        return Budget.builder()
                .id(UUID.randomUUID())
                .category(category)
                .amountLimit(BigDecimal.valueOf(limit))
                .spent(BigDecimal.valueOf(spent))
                .build();
    }

    private Project project(String name) {
        ProjectId projectId = ProjectId.newId();
        ProjectTracking tracking = new ProjectTracking(
                UUID.randomUUID(), projectId, BigDecimal.valueOf(2_000_000), 1,
                BigDecimal.valueOf(500_000), 12, BigDecimal.ZERO, 0,
                Instant.now(), Instant.now());
        return Project.rehydrate(
                projectId, UserId.of(UUID.randomUUID().toString()), ProjectType.PERSONAL,
                ProjectStatus.ACTIVE, ProjectPriority.HIGH, name, "desc",
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
}
