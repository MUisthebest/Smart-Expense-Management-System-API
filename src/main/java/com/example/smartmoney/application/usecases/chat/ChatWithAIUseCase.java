package com.example.smartmoney.application.usecases.chat;

import com.example.smartmoney.application.dtos.requests.chat.ChatMessageDto;
import com.example.smartmoney.application.dtos.requests.chat.ChatRequest;
import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.guardrails.InputGuardrail;
import com.example.smartmoney.application.guardrails.JsonSchemaGuardrail;
import com.example.smartmoney.application.ports.chat.AIChatPort;
import com.example.smartmoney.application.ports.chat.ChatHistoryPort;
import com.example.smartmoney.application.services.BudgetAdvisorService;
import com.example.smartmoney.application.services.ChatbotPromptBuilder;
import com.example.smartmoney.application.services.SpendingSimulationService;
import com.example.smartmoney.application.services.UserFinancialDataService;
import com.example.smartmoney.application.services.WindfallAllocationService;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

/**
 * Chat orchestrator — UC1, UC2, UC3 + Redis history + Gemini + guardrails.
 *
 * Full request flow:
 *  Rate limit (aspect) → ScopeGuardrail (aspect) → PromptInjectionGuardrail (aspect)
 *  → Redis GET history → ChatbotPromptBuilder → Gemini call
 *  → JsonSchemaGuardrail (POST only) → append turns → Redis SET+EXPIRE
 *  → return ChatResponse / SSE stream
 */
@Service
@RequiredArgsConstructor
public class ChatWithAIUseCase {

    private final UserFinancialDataService dataProvider;
    private final AIChatPort aiChatPort;
    private final BudgetAdvisorService budgetAdvisorService;
    private final SpendingSimulationService spendingSimulationService;
    private final WindfallAllocationService windfallAllocationService;
    private final ChatbotPromptBuilder promptBuilder;
    private final UserRepository userRepository;
    private final ChatHistoryPort chatHistoryPort;
    private final JsonSchemaGuardrail jsonSchemaGuardrail;

    private static final int MAX_HISTORY_TURNS = 10;

    // ── Synchronous chat (POST /chat) ────────────────────────────────────────

    @InputGuardrail
    public ChatResponse execute(ChatRequest request, UUID userId) {
        String message = request.getMessage();

        LocalDate now = LocalDate.now();
        int month = request.getMonth() != null ? request.getMonth() : now.getMonthValue();
        int year  = request.getYear()  != null ? request.getYear()  : now.getYear();

        User user = userRepository.findById(userId).orElse(null);
        String systemPrompt = promptBuilder.build(user);

        // Load history from Redis
        List<ChatMessageDto> history = chatHistoryPort.load(userId);

        // Build financial context (income + safe_spending + budgets + projects)
        String financialContext = dataProvider.buildChatContext(userId, month, year);

        // Compose user message: context + history + current question + JSON format
        String userMessage = composeUserMessage(financialContext, history, message, true);

        // Call Gemini (sync); retry once handled inside JsonSchemaGuardrail
        String rawResponse = aiChatPort.chat(systemPrompt, userMessage, userId.toString());

        // Validate & parse JSON output
        JsonSchemaGuardrail.ParsedLlmResponse parsed = jsonSchemaGuardrail.validate(
                rawResponse,
                () -> aiChatPort.chat(systemPrompt, userMessage, userId.toString()));

        // Persist history: append USER + ASSISTANT turns
        history = appendAndTrim(history, message, parsed.reply());
        chatHistoryPort.save(userId, history);

        // Attach server-computed structured payloads based on detected intent
        return buildStructuredResponse(parsed.intent(), parsed.reply(), userId, month, year,
                message.toLowerCase());
    }

    // ── Streaming chat (GET /chat/stream) ────────────────────────────────────

    @InputGuardrail
    public void streamChat(ChatRequest request, UUID userId,
                           Consumer<String> onNext,
                           Consumer<Throwable> onError,
                           Runnable onComplete) {

        String message = request.getMessage();

        LocalDate now = LocalDate.now();
        int month = request.getMonth() != null ? request.getMonth() : now.getMonthValue();
        int year  = request.getYear()  != null ? request.getYear()  : now.getYear();

        User user = userRepository.findById(userId).orElse(null);
        String systemPrompt = promptBuilder.build(user);

        List<ChatMessageDto> history = chatHistoryPort.load(userId);

        String financialContext = dataProvider.buildChatContext(userId, month, year);

        // No JSON format instruction for stream — plain text reply
        String userMessage = composeUserMessage(financialContext, history, message, false);

        // chunk from GeminiAIChatAdapter is cumulative — track the latest value
        String[] lastChunk = {""};

        aiChatPort.chatStream(
                systemPrompt, userMessage, userId.toString(),
                chunk -> { lastChunk[0] = chunk; onNext.accept(chunk); },
                onError,
                () -> {
                    // Append USER + ASSISTANT turns and save to Redis
                    List<ChatMessageDto> updated = appendAndTrim(history, message, lastChunk[0]);
                    chatHistoryPort.save(userId, updated);
                    onComplete.run();
                });
    }

    // ── Prompt composition ───────────────────────────────────────────────────

    private String composeUserMessage(String financialContext,
                                      List<ChatMessageDto> history,
                                      String message,
                                      boolean includeJsonFormat) {
        StringBuilder sb = new StringBuilder();

        if (!financialContext.isBlank()) {
            sb.append("=== Dữ liệu tài chính ===\n").append(financialContext).append("\n");
        }

        if (!history.isEmpty()) {
            int start = Math.max(0, history.size() - MAX_HISTORY_TURNS);
            sb.append("=== Lịch sử hội thoại ===\n");
            history.subList(start, history.size()).forEach(turn ->
                    sb.append(turn.toPromptLine()).append("\n"));
            sb.append("\n");
        }

        sb.append("=== Câu hỏi hiện tại ===\n").append(message);

        if (includeJsonFormat) {
            sb.append(ChatbotPromptBuilder.JSON_OUTPUT_FORMAT);
        }

        return sb.toString();
    }

    // ── History management ───────────────────────────────────────────────────

    private List<ChatMessageDto> appendAndTrim(List<ChatMessageDto> history,
                                               String userMessage, String assistantReply) {
        List<ChatMessageDto> updated = new ArrayList<>(history);
        updated.add(ChatMessageDto.user(userMessage));
        updated.add(ChatMessageDto.assistant(assistantReply));
        // Evict oldest pair when exceeding MAX_HISTORY_TURNS (each turn = 2 messages)
        while (updated.size() > MAX_HISTORY_TURNS * 2) {
            updated.remove(0);
            updated.remove(0);
        }
        return updated;
    }

    // ── Structured response builder ──────────────────────────────────────────

    private ChatResponse buildStructuredResponse(ChatResponse.ChatIntent intent, String reply,
                                                 UUID userId, int month, int year, String messageLow) {
        return switch (intent) {
            case BUDGET_UPDATE -> {
                var suggestions = budgetAdvisorService.suggestReductions(userId, month, year, messageLow);
                yield ChatResponse.builder()
                        .reply(reply)
                        .intent(intent)
                        .budgetSuggestions(suggestions.isEmpty() ? null : suggestions)
                        .build();
            }
            case SIMULATION -> {
                var simResult = spendingSimulationService.simulate(userId, month, year, messageLow);
                yield ChatResponse.builder()
                        .reply(reply)
                        .intent(intent)
                        .simulationResult(simResult)
                        .build();
            }
            case INCOME_WINDFALL -> {
                // AI detected sudden income → allocate 60% to savings, 40% to budget limits
                var savingsSuggestions = windfallAllocationService.suggestSavingsBoost(
                        userId, month, year, messageLow);
                var budgetBoosts = windfallAllocationService.suggestBudgetBoosts(
                        userId, month, year, messageLow);
                yield ChatResponse.builder()
                        .reply(reply)
                        .intent(intent)
                        .budgetSuggestions(budgetBoosts.isEmpty() ? null : budgetBoosts)
                        .savingsSuggestions(savingsSuggestions.isEmpty() ? null : savingsSuggestions)
                        .build();
            }
            case LARGE_EXPENSE -> {
                // AI detected large unplanned expense → suggest budget coverage + project deferral
                var budgetCoverage = windfallAllocationService.suggestExpenseCoverage(
                        userId, month, year, messageLow);
                var deferral = windfallAllocationService.suggestProjectDeferral(
                        userId, month, year, messageLow);
                yield ChatResponse.builder()
                        .reply(reply)
                        .intent(intent)
                        .budgetSuggestions(budgetCoverage.isEmpty() ? null : budgetCoverage)
                        .savingsSuggestions(deferral.isEmpty() ? null : deferral)
                        .build();
            }
            case OUT_OF_SCOPE -> ChatResponse.builder()
                    .reply("Xin lỗi, tôi chỉ hỗ trợ các câu hỏi về tài chính cá nhân. " +
                           "Bạn có muốn hỏi về ngân sách, chi tiêu, hoặc mục tiêu tiết kiệm không?")
                    .intent(intent)
                    .build();
            default -> ChatResponse.builder()
                    .reply(reply)
                    .intent(intent)
                    .build();
        };
    }
}
