package com.example.smartmoney.application.usecases.chat;

import com.example.smartmoney.application.dtos.requests.chat.ChatMessageDto;
import com.example.smartmoney.application.dtos.requests.chat.ChatRequest;
import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.guardrails.InputGuardrail;
import com.example.smartmoney.application.guardrails.JsonSchemaGuardrail;
import com.example.smartmoney.application.ports.chat.AIChatPort;
import com.example.smartmoney.application.services.BudgetAdvisorService;
import com.example.smartmoney.application.services.ChatHistoryCacheService;
import com.example.smartmoney.application.services.ChatbotPromptBuilder;
import com.example.smartmoney.application.services.LlmRouter;
import com.example.smartmoney.application.services.MessageLanguageDetector;
import com.example.smartmoney.application.services.ProjectUpdateSuggestionService;
import com.example.smartmoney.application.services.SpendingSimulationService;
import com.example.smartmoney.application.services.UserFinancialDataService;
import com.example.smartmoney.application.services.WindfallAllocationService;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

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
    private final LlmRouter llmRouter;
    private final BudgetAdvisorService budgetAdvisorService;
    private final SpendingSimulationService spendingSimulationService;
    private final WindfallAllocationService windfallAllocationService;
    private final ProjectUpdateSuggestionService projectUpdateSuggestionService;
    private final ChatbotPromptBuilder promptBuilder;
    private final UserRepository userRepository;
    private final ChatHistoryCacheService chatHistoryCacheService;
    private final JsonSchemaGuardrail jsonSchemaGuardrail;

    // Suppress current-month BUDGET_UPDATE/PROJECT_UPDATE suggestions once this few days remain —
    // the reply steers the user toward a next-month plan instead (req: never touch a month that
    // hasn't started/ended yet).
    private static final int DAYS_LEFT_SUPPRESS_THRESHOLD = 15;

    // ── Synchronous chat (POST /chat) ────────────────────────────────────────

    @InputGuardrail
    public ChatResponse execute(ChatRequest request, UUID userId) {
        String message = request.getMessage();

        LocalDate now = LocalDate.now();
        int month = request.getMonth() != null ? request.getMonth() : now.getMonthValue();
        int year  = request.getYear()  != null ? request.getYear()  : now.getYear();

        User user = userRepository.findById(userId).orElse(null);
        String systemPrompt = promptBuilder.build(user);

        // Load history (summary + recent turns) from Redis
        List<ChatMessageDto> history = chatHistoryCacheService.load(userId);

        // Build financial context (income + safe_spending + budgets + projects + days-left)
        String financialContext = dataProvider.buildChatContext(userId, month, year);

        // Compose user message: context + history + current question + JSON format
        String userMessage = composeUserMessage(financialContext, history, message, true);

        // Pick the model BEFORE calling — coarse heuristic, independent of the LLM's own
        // JSON-classified intent returned below.
        AIChatPort adapter = llmRouter.forMessage(message.toLowerCase());

        // Call the routed model (sync); retry once handled inside JsonSchemaGuardrail
        String rawResponse = adapter.chat(systemPrompt, userMessage, userId.toString());

        // Validate & parse JSON output
        JsonSchemaGuardrail.ParsedLlmResponse parsed = jsonSchemaGuardrail.validate(
                rawResponse,
                () -> adapter.chat(systemPrompt, userMessage, userId.toString()));

        // Persist history: append USER + ASSISTANT turns, compressing older turns if needed
        chatHistoryCacheService.appendAndCompress(userId, history, message, parsed.reply());

        int daysLeft = now.lengthOfMonth() - now.getDayOfMonth();

        // Attach server-computed structured payloads based on detected intent
        return buildStructuredResponse(parsed.intent(), parsed.reply(), userId, month, year,
                message.toLowerCase(), daysLeft,
                relatedQuestionsOrFallback(parsed.intent(), parsed.relatedQuestions()));
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

        List<ChatMessageDto> history = chatHistoryCacheService.load(userId);

        String financialContext = dataProvider.buildChatContext(userId, month, year);

        // No JSON format instruction for stream — plain text reply
        String userMessage = composeUserMessage(financialContext, history, message, false);

        AIChatPort adapter = llmRouter.forMessage(message.toLowerCase());

        // chunk from the AIChatPort adapter is cumulative — track the latest value
        String[] lastChunk = {""};

        adapter.chatStream(
                systemPrompt, userMessage, userId.toString(),
                chunk -> { lastChunk[0] = chunk; onNext.accept(chunk); },
                onError,
                () -> {
                    // Append USER + ASSISTANT turns and save (compressing if needed)
                    chatHistoryCacheService.appendAndCompress(userId, history, message, lastChunk[0]);
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
            // ChatHistoryCacheService already caps this to [rolling summary] + recent raw turns.
            sb.append("=== Lịch sử hội thoại ===\n");
            history.forEach(turn -> sb.append(turn.toPromptLine()).append("\n"));
            sb.append("\n");
        }

        sb.append("=== Câu hỏi hiện tại ===\n").append(message);

        if (includeJsonFormat) {
            sb.append(ChatbotPromptBuilder.JSON_OUTPUT_FORMAT);
        }

        return sb.toString();
    }

    // ── Structured response builder ──────────────────────────────────────────

    private ChatResponse buildStructuredResponse(ChatResponse.ChatIntent intent, String reply,
                                                 UUID userId, int month, int year, String messageLow,
                                                 int daysLeft, List<String> relatedQuestions) {
        // Days-left gate: with few days left in the current month, never propose changing THIS
        // month's budget/project — the reply already steers toward a next-month plan instead.
        boolean suppressCurrentMonth = daysLeft <= DAYS_LEFT_SUPPRESS_THRESHOLD
                && (intent == ChatResponse.ChatIntent.BUDGET_UPDATE
                    || intent == ChatResponse.ChatIntent.PROJECT_UPDATE);

        return switch (intent) {
            case BUDGET_UPDATE -> {
                var suggestions = suppressCurrentMonth
                        ? Collections.<ChatResponse.BudgetUpdateSuggestion>emptyList()
                        : budgetAdvisorService.suggestReductions(userId, month, year, messageLow);
                yield ChatResponse.builder()
                        .reply(reply)
                        .intent(intent)
                        .budgetSuggestions(suggestions.isEmpty() ? null : suggestions)
                        .actionRequired(!suggestions.isEmpty())
                        .relatedQuestions(relatedQuestions)
                        .build();
            }
            case SIMULATION -> {
                var simResult = spendingSimulationService.simulate(userId, month, year, messageLow);
                // Shortfall detected → auto-attach a concrete budget cut alongside the simulation,
                // instead of leaving the user to ask a follow-up BUDGET_UPDATE question.
                var shortfallSuggestions = simResult.getNewSafeSpending() < 0
                        ? budgetAdvisorService.suggestReductions(userId, month, year, messageLow)
                        : Collections.<ChatResponse.BudgetUpdateSuggestion>emptyList();
                yield ChatResponse.builder()
                        .reply(reply)
                        .intent(intent)
                        .simulationResult(simResult)
                        .budgetSuggestions(shortfallSuggestions.isEmpty() ? null : shortfallSuggestions)
                        .actionRequired(!shortfallSuggestions.isEmpty())
                        .relatedQuestions(relatedQuestions)
                        .build();
            }
            case INCOME_WINDFALL -> {
                // AI detected sudden income → allocate 60% to savings, 40% to budget limits.
                // Only surface the side(s) the user actually asked about; if neither budget
                // nor project is explicitly mentioned, show both (helpful default).
                boolean showBudget = wantsBudgetSuggestion(messageLow);
                boolean showProject = wantsProjectSuggestion(messageLow);
                boolean scoped = showBudget || showProject;

                var savingsSuggestions = (!scoped || showProject)
                        ? windfallAllocationService.suggestSavingsBoost(userId, month, year, messageLow)
                        : Collections.<ChatResponse.SavingsPlanSuggestion>emptyList();
                var budgetBoosts = (!scoped || showBudget)
                        ? windfallAllocationService.suggestBudgetBoosts(userId, month, year, messageLow)
                        : Collections.<ChatResponse.BudgetUpdateSuggestion>emptyList();

                yield ChatResponse.builder()
                        .reply(reply)
                        .intent(intent)
                        .budgetSuggestions(budgetBoosts.isEmpty() ? null : budgetBoosts)
                        .savingsSuggestions(savingsSuggestions.isEmpty() ? null : savingsSuggestions)
                        .actionRequired(!budgetBoosts.isEmpty() || !savingsSuggestions.isEmpty())
                        .relatedQuestions(relatedQuestions)
                        .build();
            }
            case LARGE_EXPENSE -> {
                // AI detected large unplanned expense → suggest budget coverage + project deferral.
                // Same scoping rule as INCOME_WINDFALL above.
                boolean showBudget = wantsBudgetSuggestion(messageLow);
                boolean showProject = wantsProjectSuggestion(messageLow);
                boolean scoped = showBudget || showProject;

                var budgetCoverage = (!scoped || showBudget)
                        ? windfallAllocationService.suggestExpenseCoverage(userId, month, year, messageLow)
                        : Collections.<ChatResponse.BudgetUpdateSuggestion>emptyList();
                var deferral = (!scoped || showProject)
                        ? windfallAllocationService.suggestProjectDeferral(userId, month, year, messageLow)
                        : Collections.<ChatResponse.SavingsPlanSuggestion>emptyList();

                yield ChatResponse.builder()
                        .reply(reply)
                        .intent(intent)
                        .budgetSuggestions(budgetCoverage.isEmpty() ? null : budgetCoverage)
                        .savingsSuggestions(deferral.isEmpty() ? null : deferral)
                        .actionRequired(!budgetCoverage.isEmpty() || !deferral.isEmpty())
                        .relatedQuestions(relatedQuestions)
                        .build();
            }
            case PROJECT_UPDATE -> {
                // User directly asked to adjust a project's tracked income (contribution)
                // or expense (money owed) — not triggered by a windfall/large-expense event.
                var suggestions = suppressCurrentMonth
                        ? Collections.<ChatResponse.SavingsPlanSuggestion>emptyList()
                        : projectUpdateSuggestionService.suggest(userId, messageLow);
                yield ChatResponse.builder()
                        .reply(reply)
                        .intent(intent)
                        .savingsSuggestions(suggestions.isEmpty() ? null : suggestions)
                        .actionRequired(!suggestions.isEmpty())
                        .relatedQuestions(relatedQuestions)
                        .build();
            }
            case OUT_OF_SCOPE -> ChatResponse.builder()
                    .reply("Xin lỗi, tôi chỉ hỗ trợ các câu hỏi về tài chính cá nhân. " +
                           "Bạn có muốn hỏi về ngân sách, chi tiêu, hoặc mục tiêu tiết kiệm không?")
                    .intent(intent)
                    .build();
            default -> {
                // GENERAL_ADVICE: the LLM didn't flag this as a windfall/large-expense event,
                // but the message may still describe one in passing (e.g. "lương tháng này tăng
                // 3 triệu, tôi nên làm gì?" answered generically). Attach the same suggestions
                // as INCOME_WINDFALL/LARGE_EXPENSE would as a safety net, without overriding intent.
                var builder = ChatResponse.builder()
                        .reply(reply)
                        .intent(intent)
                        .relatedQuestions(relatedQuestions);
                boolean implicitEventHandled = attachImplicitEventSuggestions(builder, userId, month, year, messageLow);
                // Don't pile a second, unrelated suggestion onto a turn that already got one, and
                // never volunteer one on a bare greeting/farewell (nothing to suggest there).
                if (!implicitEventHandled && !GREETING_OR_FAREWELL.matcher(messageLow.trim()).matches()) {
                    attachProactiveSuggestions(builder, userId, month, year, daysLeft, messageLow);
                }
                yield builder.build();
            }
        };
    }

    // ── Implicit large income/expense detection (GENERAL_ADVICE safety net) ─────

    private static final BigDecimal LARGE_AMOUNT_THRESHOLD = BigDecimal.valueOf(1_000_000);

    private static final Pattern INCOME_EVENT_PATTERN = Pattern.compile(
            "thưởng|lương|thu nhập|hoa hồng|trúng số|trúng giải|xổ số|học bổng|thừa kế|" +
            "được (cho|tặng)|cho tặng|nhận được|windfall|bonus|kpi",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern EXPENSE_EVENT_PATTERN = Pattern.compile(
            "mua (xe|nhà|laptop|điện thoại)|sửa (xe|nhà|chữa)|viện phí|bệnh viện|" +
            "học phí|hỏng|sự cố|chi phí (lớn|đột xuất)|đóng tiền|phải trả|thanh toán",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    // Bare greeting/farewell — the WHOLE message, not a substring match, so a real question that
    // happens to open with "chào" (e.g. "chào bạn, tôi muốn hỏi về ngân sách") still gets suggestions.
    private static final Pattern GREETING_OR_FAREWELL = Pattern.compile(
            "(xin chào|chào( bạn)?|hi|hello|hey|tạm biệt|bye|goodbye|hẹn gặp lại)[\\s!.,]*",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * When intent resolved to GENERAL_ADVICE, checks whether the message still describes a
     * large (>= 1,000,000 VND) income or expense event and, if so, computes the same
     * budget/project suggestions INCOME_WINDFALL/LARGE_EXPENSE would — mutates {@code builder}
     * in place.
     *
     * @return true when a qualifying amount+keyword was found (whether or not the computed
     *         suggestion lists ended up empty) — signals the caller not to also run the
     *         lower-priority proactive safety-net checks on this same turn
     */
    boolean attachImplicitEventSuggestions(ChatResponse.ChatResponseBuilder builder,
                                                UUID userId, int month, int year, String messageLow) {
        BigDecimal amount = windfallAllocationService.parseAmount(messageLow);
        if (amount.compareTo(LARGE_AMOUNT_THRESHOLD) < 0) return false;

        boolean showBudget = wantsBudgetSuggestion(messageLow);
        boolean showProject = wantsProjectSuggestion(messageLow);
        boolean scoped = showBudget || showProject;

        if (INCOME_EVENT_PATTERN.matcher(messageLow).find()) {
            var savings = (!scoped || showProject)
                    ? windfallAllocationService.suggestSavingsBoost(userId, month, year, messageLow)
                    : Collections.<ChatResponse.SavingsPlanSuggestion>emptyList();
            var budgets = (!scoped || showBudget)
                    ? windfallAllocationService.suggestBudgetBoosts(userId, month, year, messageLow)
                    : Collections.<ChatResponse.BudgetUpdateSuggestion>emptyList();
            builder.budgetSuggestions(budgets.isEmpty() ? null : budgets)
                   .savingsSuggestions(savings.isEmpty() ? null : savings)
                   .actionRequired(!budgets.isEmpty() || !savings.isEmpty());
            return true;
        } else if (EXPENSE_EVENT_PATTERN.matcher(messageLow).find()) {
            var coverage = (!scoped || showBudget)
                    ? windfallAllocationService.suggestExpenseCoverage(userId, month, year, messageLow)
                    : Collections.<ChatResponse.BudgetUpdateSuggestion>emptyList();
            var deferral = (!scoped || showProject)
                    ? windfallAllocationService.suggestProjectDeferral(userId, month, year, messageLow)
                    : Collections.<ChatResponse.SavingsPlanSuggestion>emptyList();
            builder.budgetSuggestions(coverage.isEmpty() ? null : coverage)
                   .savingsSuggestions(deferral.isEmpty() ? null : deferral)
                   .actionRequired(!coverage.isEmpty() || !deferral.isEmpty());
            return true;
        }
        return false;
    }

    /**
     * Proactive safety net for a plain GENERAL_ADVICE turn with no explicit ask and no implicit
     * windfall/expense event: volunteers at most ONE suggestion, checked in priority order —
     * a budget nearing its limit, an unsustainable project commitment, a month-end leftover to
     * park, or a multi-month surplus streak — so the user isn't shown several unrelated nudges
     * at once. No-op (leaves {@code builder} untouched) when none apply.
     */
    void attachProactiveSuggestions(ChatResponse.ChatResponseBuilder builder,
                                     UUID userId, int month, int year, int daysLeft, String messageLow) {
        boolean english = MessageLanguageDetector.isEnglish(messageLow);
        var snapshot = dataProvider.getFinancialSnapshot(userId, month, year);

        var nearLimit = budgetAdvisorService.checkNearLimit(userId, month, year, daysLeft, english);
        if (nearLimit.isPresent()) {
            builder.budgetSuggestions(List.of(nearLimit.get())).actionRequired(true);
            return;
        }

        var infeasible = projectUpdateSuggestionService.checkFeasibility(userId, snapshot.monthlyIncome(), english);
        if (infeasible.isPresent()) {
            builder.savingsSuggestions(List.of(infeasible.get())).actionRequired(true);
            return;
        }

        var leftover = projectUpdateSuggestionService.checkMonthEndLeftover(
                userId, daysLeft, snapshot.safeSpending(), english);
        if (leftover.isPresent()) {
            builder.savingsSuggestions(List.of(leftover.get())).actionRequired(true);
            return;
        }

        var surplusTrend = projectUpdateSuggestionService.checkSurplusTrend(userId, english);
        surplusTrend.ifPresent(s -> builder.savingsSuggestions(List.of(s)).actionRequired(true));
    }

    // ── Related-questions fallback ───────────────────────────────────────────

    private static final List<String> DEFAULT_RELATED_QUESTIONS = List.of(
            "Tôi có đang chi tiêu vượt ngân sách ở danh mục nào không?",
            "Tiến độ các dự án tiết kiệm của tôi đang như thế nào?",
            "Tôi nên điều chỉnh ngân sách tháng này ra sao?");

    /** OUT_OF_SCOPE gets no related questions; other intents fall back to a generic set
     *  when the LLM omits the field (e.g. after a JsonSchemaGuardrail fallback). */
    private List<String> relatedQuestionsOrFallback(ChatResponse.ChatIntent intent, List<String> llmQuestions) {
        if (intent == ChatResponse.ChatIntent.OUT_OF_SCOPE) return null;
        return (llmQuestions == null || llmQuestions.isEmpty()) ? DEFAULT_RELATED_QUESTIONS : llmQuestions;
    }

    // ── Suggestion scoping helpers ────────────────────────────────────────────

    /** True when the user's message explicitly asks about budget/spending limits. */
    private boolean wantsBudgetSuggestion(String messageLow) {
        return messageLow.contains("ngân sách") || messageLow.contains("budget")
                || messageLow.contains("hạn mức") || messageLow.contains("chi tiêu");
    }

    /** True when the user's message explicitly asks about savings projects/goals. */
    private boolean wantsProjectSuggestion(String messageLow) {
        return messageLow.contains("dự án") || messageLow.contains("project")
                || messageLow.contains("tiết kiệm") || messageLow.contains("mục tiêu")
                || messageLow.contains("saving");
    }
}
