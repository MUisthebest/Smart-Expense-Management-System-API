package com.example.smartmoney.application.usecases.chat;

import com.example.smartmoney.application.dtos.requests.chat.ChatMessageDto;
import com.example.smartmoney.application.dtos.requests.chat.ChatRequest;
import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.ports.chat.AIChatPort;
import com.example.smartmoney.application.services.BudgetAdvisorService;
import com.example.smartmoney.application.services.ChatbotPromptBuilder;
import com.example.smartmoney.application.services.SpendingSimulationService;
import com.example.smartmoney.application.services.UserFinancialDataService;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Main chat orchestrator — wires UC1, UC2, and UC3 into a single execute() call.
 *
 * UC1 — Budget Adjustment:
 *   Detects "cut/reduce spending" intent → fetches budgets → lets AI explain the
 *   server-computed suggestion → returns ChatResponse with intent=BUDGET_UPDATE and
 *   a BudgetUpdateSuggestion list (frontend calls PUT /budgets/{id} on confirm).
 *
 * UC2 — What-if Simulation:
 *   Detects "if I spend X more" intent → runs SpendingSimulationService → checks 20%
 *   rule and project delay risk → returns ChatResponse with intent=SIMULATION and a
 *   SimulationResult. Read-only, no data written.
 *
 * UC3 — Personalised Tone:
 *   Fetches user's savingPace / interventionLevel / focusMode and injects them into
 *   the system prompt via ChatbotPromptBuilder. Every response (regardless of intent)
 *   is shaped by these preferences.
 *
 * Old signatures are preserved as comments; new signatures accept a full ChatRequest
 * (including optional conversation history, month, and year) and return ChatResponse.
 */
@Service
@RequiredArgsConstructor
public class ChatWithAIUseCase {

    // ── Dependencies ─────────────────────────────────────────────────────────

    private final UserFinancialDataService dataProvider;
    private final AIChatPort aiChatPort;

    // UC1
    private final BudgetAdvisorService budgetAdvisorService;
    // UC2
    private final SpendingSimulationService spendingSimulationService;
    // UC3
    private final ChatbotPromptBuilder promptBuilder;
    private final UserRepository userRepository;

    // Maximum conversation turns forwarded to the LLM to avoid context overflow
    private static final int MAX_HISTORY_TURNS = 10;

    // ── Old execute (single String message, plain String reply) ─────────────
    // Kept as a comment — the controller previously called this.
    //
    // public String execute(String message, UUID userId) {
    //     Set<UserFinancialDataService.DataType> dataTypes = determineDataTypes(message);
    //     String userData = dataProvider.getUserDataSummary(userId, dataTypes);
    //     String fullPrompt = userData.isEmpty()
    //             ? "Câu hỏi của bạn: " + message
    //             : "Đây là dữ liệu tài chính của bạn:\n" + userData + "\n\nCâu hỏi của bạn: " + message;
    //     return aiChatPort.chat(fullPrompt, userId.toString());
    // }

    /**
     * UC1 / UC2 / UC3 — synchronous chat.
     *
     * @param request contains the user message, optional history (last N turns),
     *                and optional month / year for context scoping
     * @param userId  authenticated user ID
     * @return structured ChatResponse with intent classification and optional
     *         BudgetUpdateSuggestion or SimulationResult payloads
     */
    public ChatResponse execute(ChatRequest request, UUID userId) {
        String message = request.getMessage();
        String messageLow = message.toLowerCase();

        // Resolve the analysis month/year (defaults to current month)
        LocalDate now = LocalDate.now();
        int month = request.getMonth() != null ? request.getMonth() : now.getMonthValue();
        int year  = request.getYear()  != null ? request.getYear()  : now.getYear();

        // UC3: load user preferences for personalised system prompt
        User user = userRepository.findById(userId).orElse(null);
        String systemPrompt = promptBuilder.build(user);

        // Detect intent from the user's message
        ChatResponse.ChatIntent intent = detectIntent(messageLow);

        // Build the financial context block injected ahead of the user's question
        String financialContext = buildContextForIntent(intent, userId, month, year, messageLow);

        // Include the last N turns of conversation history so the AI can refer back
        String historyBlock = buildHistoryBlock(request.getHistory());

        // Compose the full user-turn message: context + history + current question
        String userMessage = composeUserMessage(financialContext, historyBlock, message);

        // Call the AI for the natural-language reply
        String reply = aiChatPort.chat(systemPrompt, userMessage, userId.toString());

        // Build the structured response depending on detected intent
        return buildStructuredResponse(intent, reply, userId, month, year, messageLow);
    }

    // ── Old streamChat (single String message) ───────────────────────────────
    // Kept as a comment — the controller previously called this.
    //
    // public void streamChat(String message, UUID userId,
    //                        Consumer<String> onNext,
    //                        Consumer<Throwable> onError,
    //                        Runnable onComplete) {
    //     Set<UserFinancialDataService.DataType> dataTypes = determineDataTypes(message);
    //     String userData = dataProvider.getUserDataSummary(userId, dataTypes);
    //     String fullPrompt = userData.isEmpty()
    //             ? "Câu hỏi của bạn: " + message
    //             : "Đây là dữ liệu tài chính của bạn:\n" + userData + "\n\nCâu hỏi của bạn: " + message;
    //     aiChatPort.chatStream(fullPrompt, userId.toString(), onNext, onError, onComplete);
    // }

    /**
     * UC1 / UC2 / UC3 — streaming chat (SSE).
     *
     * The same intent detection and context-building logic applies; the only
     * difference is that the reply is delivered token-by-token via {@code onNext}.
     * Structured BudgetUpdateSuggestion / SimulationResult payloads are NOT
     * available in stream mode — use the synchronous endpoint for those.
     */
    public void streamChat(ChatRequest request, UUID userId,
                           Consumer<String> onNext,
                           Consumer<Throwable> onError,
                           Runnable onComplete) {

        String message = request.getMessage();
        String messageLow = message.toLowerCase();

        LocalDate now = LocalDate.now();
        int month = request.getMonth() != null ? request.getMonth() : now.getMonthValue();
        int year  = request.getYear()  != null ? request.getYear()  : now.getYear();

        // UC3: personalised system prompt
        User user = userRepository.findById(userId).orElse(null);
        String systemPrompt = promptBuilder.build(user);

        ChatResponse.ChatIntent intent = detectIntent(messageLow);
        String financialContext = buildContextForIntent(intent, userId, month, year, messageLow);
        String historyBlock = buildHistoryBlock(request.getHistory());
        String userMessage = composeUserMessage(financialContext, historyBlock, message);

        // Stream the reply; UC3 personalisation is already baked into the systemPrompt
        aiChatPort.chatStream(systemPrompt, userMessage, userId.toString(), onNext, onError, onComplete);
    }

    // ── Intent detection ─────────────────────────────────────────────────────

    /**
     * Classifies the user's message into one of the UC intents using keyword matching.
     *
     * Priority order: SIMULATION is checked first because its trigger keywords
     * ("nếu", "what if") can appear in the same message as budget keywords.
     */
    private ChatResponse.ChatIntent detectIntent(String messageLow) {
        // UC2 — What-if simulation keywords (check BEFORE budget keywords)
        if (messageLow.contains("nếu") || messageLow.contains("what if") ||
            messageLow.contains("nếu tôi") || messageLow.contains("nếu chi") ||
            messageLow.contains("mô phỏng") || messageLow.contains("thử xem") ||
            messageLow.contains("giả sử") || messageLow.contains("if i spend")) {
            return ChatResponse.ChatIntent.SIMULATION;
        }

        // UC1 — Budget adjustment keywords
        if (messageLow.contains("cắt") || messageLow.contains("giảm") ||
            messageLow.contains("điều chỉnh ngân sách") || messageLow.contains("thay đổi ngân sách") ||
            messageLow.contains("ngân sách") || messageLow.contains("cut spending") ||
            messageLow.contains("reduce") || messageLow.contains("budget") ||
            messageLow.contains("chi tiêu ăn") || messageLow.contains("chi tiêu") ||
            messageLow.contains("tiết kiệm ngân sách")) {
            return ChatResponse.ChatIntent.BUDGET_UPDATE;
        }

        // Default: general financial advice or off-topic chat
        return ChatResponse.ChatIntent.GENERAL_ADVICE;
    }

    // ── Context building ─────────────────────────────────────────────────────

    /**
     * Builds the financial context string prepended to the user message.
     * Each intent gets the data it specifically needs:
     *  UC1 → budget list with category percentages
     *  UC2 → simulation result + project list
     *  GENERAL → transactions + projects (existing behaviour)
     */
    private String buildContextForIntent(
            ChatResponse.ChatIntent intent, UUID userId, int month, int year, String messageLow) {

        return switch (intent) {
            case BUDGET_UPDATE -> {
                // UC1: give the AI the budget breakdown so it can explain the suggestion naturally
                yield budgetAdvisorService.buildBudgetContext(userId, month, year);
            }
            case SIMULATION -> {
                // UC2: give the AI the pre-computed simulation result + project summary
                String simContext = spendingSimulationService.buildSimulationContext(userId, month, year, messageLow);
                String projectContext = dataProvider.getUserDataSummary(
                        userId, Set.of(UserFinancialDataService.DataType.PROJECTS));
                yield simContext + "\n" + projectContext;
            }
            default -> {
                // GENERAL_ADVICE: load transactions + projects as before
                // Old logic from determineDataTypes() is inlined here for clarity
                Set<UserFinancialDataService.DataType> types = new HashSet<>();
                types.add(UserFinancialDataService.DataType.TRANSACTIONS);
                types.add(UserFinancialDataService.DataType.PROJECTS);
                yield dataProvider.getUserDataSummary(userId, types);
            }
        };
    }

    // ── Structured response builder ───────────────────────────────────────────

    /**
     * Assembles the final ChatResponse, attaching structured payloads only when
     * the intent requires them:
     *  BUDGET_UPDATE → BudgetUpdateSuggestion list (server-computed, read-only)
     *  SIMULATION    → SimulationResult (server-computed, read-only)
     *  GENERAL_ADVICE → reply only
     */
    private ChatResponse buildStructuredResponse(
            ChatResponse.ChatIntent intent, String reply,
            UUID userId, int month, int year, String messageLow) {

        return switch (intent) {
            case BUDGET_UPDATE -> {
                // UC1: attach the concrete budget suggestion for the frontend confirm card
                var suggestions = budgetAdvisorService.suggestReductions(userId, month, year, messageLow);
                yield ChatResponse.builder()
                        .reply(reply)
                        .intent(intent)
                        .budgetSuggestions(suggestions.isEmpty() ? null : suggestions)
                        .build();
            }
            case SIMULATION -> {
                // UC2: attach the computed simulation result for the frontend what-if panel
                var simResult = spendingSimulationService.simulate(userId, month, year, messageLow);
                yield ChatResponse.builder()
                        .reply(reply)
                        .intent(intent)
                        .simulationResult(simResult)
                        .build();
            }
            default -> ChatResponse.builder()
                    .reply(reply)
                    .intent(intent)
                    .build();
        };
    }

    // ── Prompt composition helpers ────────────────────────────────────────────

    /**
     * Formats the conversation history into a readable block for the LLM.
     * Truncated to MAX_HISTORY_TURNS to avoid overshooting the context window.
     */
    private String buildHistoryBlock(List<ChatMessageDto> history) {
        if (history == null || history.isEmpty()) {
            return "";
        }
        int startIdx = Math.max(0, history.size() - MAX_HISTORY_TURNS);
        return history.subList(startIdx, history.size()).stream()
                .map(ChatMessageDto::toPromptLine)
                .collect(Collectors.joining("\n", "Lịch sử hội thoại:\n", "\n"));
    }

    /**
     * Composes the final user-turn message from three parts:
     *  1. Financial context (transactions, budgets, or simulation result)
     *  2. Conversation history (optional, last N turns)
     *  3. The user's actual current question
     */
    private String composeUserMessage(String financialContext, String historyBlock, String message) {
        StringBuilder sb = new StringBuilder();
        if (!financialContext.isBlank()) {
            sb.append("Dữ liệu tài chính:\n").append(financialContext).append("\n");
        }
        if (!historyBlock.isBlank()) {
            sb.append(historyBlock).append("\n");
        }
        sb.append("Câu hỏi hiện tại: ").append(message);
        return sb.toString();
    }

    // ── Old determineDataTypes (replaced by buildContextForIntent above) ─────
    // Kept as a comment for reference.
    //
    // private Set<UserFinancialDataService.DataType> determineDataTypes(String message) {
    //     Set<UserFinancialDataService.DataType> types = new HashSet<>();
    //     types.add(UserFinancialDataService.DataType.INCOME);
    //     String lower = message.toLowerCase();
    //     if (lower.contains("chi tiêu") || lower.contains("giao dịch") || ...) {
    //         types.add(UserFinancialDataService.DataType.TRANSACTIONS);
    //     }
    //     if (lower.contains("dự án") || lower.contains("project") || ...) {
    //         types.add(UserFinancialDataService.DataType.PROJECTS);
    //     }
    //     return types;
    // }
}
