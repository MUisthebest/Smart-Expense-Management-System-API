package com.example.smartmoney.application.usecases.chat;

import com.example.smartmoney.application.dtos.chat.PendingChatAction;
import com.example.smartmoney.application.dtos.requests.chat.ChatMessageDto;
import com.example.smartmoney.application.dtos.requests.chat.ChatRequest;
import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.guardrails.InputGuardrail;
import com.example.smartmoney.application.guardrails.JsonSchemaGuardrail;
import com.example.smartmoney.application.ports.chat.AIChatPort;
import com.example.smartmoney.application.ports.chat.ChatPendingActionPort;
import com.example.smartmoney.application.services.BudgetAdvisorService;
import com.example.smartmoney.application.services.ChatHistoryCacheService;
import com.example.smartmoney.application.services.ChatbotPromptBuilder;
import com.example.smartmoney.application.services.LlmRouter;
import com.example.smartmoney.application.services.MessageLanguageDetector;
import com.example.smartmoney.application.services.ProjectHypothesisService;
import com.example.smartmoney.application.services.ProjectUpdateSuggestionService;
import com.example.smartmoney.application.services.SpendingSimulationService;
import com.example.smartmoney.application.services.UserFinancialDataService;
import com.example.smartmoney.application.services.WindfallAllocationService;
import com.example.smartmoney.application.usecases.budget.ComputeBudgetAllocationUseCase;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatWithAIUseCase {

    private final UserFinancialDataService dataProvider;
    private final LlmRouter llmRouter;
    private final BudgetAdvisorService budgetAdvisorService;
    private final SpendingSimulationService spendingSimulationService;
    private final WindfallAllocationService windfallAllocationService;
    private final ProjectUpdateSuggestionService projectUpdateSuggestionService;
    private final ProjectHypothesisService projectHypothesisService;
    private final ChatbotPromptBuilder promptBuilder;
    private final UserRepository userRepository;
    private final ChatHistoryCacheService chatHistoryCacheService;
    private final JsonSchemaGuardrail jsonSchemaGuardrail;
    private final ChatPendingActionPort chatPendingActionPort;
    private final ComputeBudgetAllocationUseCase computeBudgetAllocationUseCase;

    // Suppress current-month BUDGET_UPDATE/PROJECT_UPDATE suggestions once this few days remain —
    // the reply steers the user toward a next-month plan instead (req: never touch a month that
    // hasn't started/ended yet).
    private static final int DAYS_LEFT_SUPPRESS_THRESHOLD = 15;

    // Shown when the LLM call itself fails (network blip, Anthropic rate-limited/overloaded,
    // misconfigured API key, ...) instead of letting the exception bubble up to a raw 500 — the
    // frontend renders any non-2xx/timeout as the same generic "connection error" toast
    // regardless of cause, so degrading to a normal 200 chat turn here is strictly better UX for
    // every transient failure mode.
    private static final String LLM_UNAVAILABLE_REPLY_VI =
            "Xin lỗi, trợ lý AI đang gặp sự cố kết nối tạm thời. Bạn vui lòng thử lại sau ít phút nhé.";
    private static final String LLM_UNAVAILABLE_REPLY_EN =
            "Sorry, the AI assistant is temporarily unavailable. Please try again in a few minutes.";

    // ── Synchronous chat (POST /chat) ────────────────────────────────────────

    @InputGuardrail
    public ChatResponse execute(ChatRequest request, UUID userId) {
        // Note: the deterministic plain yes/no confirm/deny gate (bare "có"/"không"/"yes"/"no"
        // answering the previous turn's pending suggestion) now runs in InputGuardrailAspect,
        // BEFORE this method's guardrail chain — see ResolvePlainAnswerUseCase's class doc for
        // why it can't live inside this method body anymore.
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

        // Call the routed model (sync) and validate/parse its JSON output; falls back to a
        // friendly reply instead of throwing if the LLM call itself fails.
        JsonSchemaGuardrail.ParsedLlmResponse parsed = callLlm(
                adapter, systemPrompt, userMessage, userId.toString(), message.toLowerCase());

        // Persist history: append USER + ASSISTANT turns, compressing older turns if needed
        chatHistoryCacheService.appendAndCompress(userId, history, message, parsed.reply());

        int daysLeft = now.lengthOfMonth() - now.getDayOfMonth();

        // Attach server-computed structured payloads based on detected intent. The LLM reply is
        // already in hand at this point — don't let a transient DB/Redis hiccup while computing
        // suggestions (budget/project lookups, none of which involve the LLM) throw away a
        // perfectly good reply and turn it into a 500; fall back to a reply-only response instead.
        boolean english = MessageLanguageDetector.isEnglish(message.toLowerCase());
        ChatResponse response;
        try {
            response = buildStructuredResponse(parsed.intent(), parsed.reply(), userId, user, month, year,
                    message.toLowerCase(), daysLeft,
                    relatedQuestionsOrFallback(parsed.intent(), parsed.relatedQuestions()));
        } catch (Exception e) {
            log.warn("ChatWithAIUseCase: failed to compute structured suggestions for user {}, " +
                    "returning reply-only response: {}", userId, e.getMessage());
            response = ChatResponse.builder()
                    .reply(parsed.reply())
                    .intent(parsed.intent())
                    .relatedQuestions(relatedQuestionsOrFallback(parsed.intent(), parsed.relatedQuestions()))
                    .build();
        }
        // Stamped once here regardless of which branch built the response, rather than touching
        // every ChatResponse.builder() call inside buildStructuredResponse — one source of truth
        // for the frontend's confirm/deny button wording ("Có"/"Không" vs "Yes"/"No").
        return response.toBuilder().english(english).build();
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

    // ── Guarded LLM call ──────────────────────────────────────────────────────

    /**
     * Calls {@code adapter.chat()} and validates its JSON output, but never lets an exception
     * from the LLM call itself (network error, Anthropic rate-limited/overloaded, misconfigured
     * API key, timeout, ...) escape to the controller as an uncaught 500 — instead falls back to
     * a friendly GENERAL_ADVICE reply, same fallback shape {@link JsonSchemaGuardrail} already
     * uses for malformed JSON. Retry-on-malformed-JSON (inside {@code jsonSchemaGuardrail.validate})
     * is untouched — this only guards the first call, which previously had no try/catch at all.
     */
    private JsonSchemaGuardrail.ParsedLlmResponse callLlm(
            AIChatPort adapter, String systemPrompt, String userMessage, String userId, String messageLow) {
        String rawResponse;
        try {
            rawResponse = adapter.chat(systemPrompt, userMessage, userId);
        } catch (Exception e) {
            log.warn("ChatWithAIUseCase: LLM call failed for user {}, returning fallback reply: {}",
                    userId, e.getMessage());
            boolean english = MessageLanguageDetector.isEnglish(messageLow);
            return new JsonSchemaGuardrail.ParsedLlmResponse(
                    ChatResponse.ChatIntent.GENERAL_ADVICE,
                    english ? LLM_UNAVAILABLE_REPLY_EN : LLM_UNAVAILABLE_REPLY_VI,
                    List.of());
        }
        return jsonSchemaGuardrail.validate(rawResponse, () -> adapter.chat(systemPrompt, userMessage, userId));
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
                                                 UUID userId, User user, int month, int year, String messageLow,
                                                 int daysLeft, List<String> relatedQuestions) {
        // Days-left gate: with few days left in the current month, never propose changing THIS
        // month's budget/project — the reply already steers toward a next-month plan instead.
        boolean suppressCurrentMonth = daysLeft <= DAYS_LEFT_SUPPRESS_THRESHOLD
                && (intent == ChatResponse.ChatIntent.BUDGET_UPDATE
                    || intent == ChatResponse.ChatIntent.PROJECT_UPDATE
                    || intent == ChatResponse.ChatIntent.PROJECT_HYPOTHESIS);

        return switch (intent) {
            case BUDGET_UPDATE -> {
                // "increase 1-2 categories" is offered as a neutral swap (raise the requested
                // category, cover it by cutting the lowest-spend category) rather than approved
                // outright — this path still targets the CURRENT month, so the days-left gate
                // still applies to it.
                if (BUDGET_INCREASE_PATTERN.matcher(messageLow).find()) {
                    if (suppressCurrentMonth) {
                        yield ChatResponse.builder()
                                .reply(reply).intent(intent).relatedQuestions(relatedQuestions).build();
                    }
                    var swap = budgetAdvisorService.suggestIncreaseSwap(userId, month, year, messageLow);
                    String actionId = swap.isEmpty() ? null
                            : storePendingAction(userId, budgetSwapAction(swap), messageLow);
                    String increaseReply = reply;
                    String warning = abnormalIncreaseWarning(swap, user, messageLow);
                    if (warning != null) increaseReply = appendToReply(reply, warning);
                    yield ChatResponse.builder()
                            .reply(increaseReply)
                            .intent(intent)
                            .budgetSuggestions(swap.isEmpty() ? null : swap)
                            .pendingActionId(actionId)
                            .actionRequired(!swap.isEmpty())
                            .relatedQuestions(relatedQuestions)
                            .build();
                }
                // Plain cut/reduce request: always a full per-category plan for the FOLLOWING
                // month only — never the current month (nothing to gate on days-left for), never
                // more than one month ahead. Computed via the same deterministic
                // ComputeBudgetAllocationUseCase the standalone allocation endpoint uses, not the
                // old single-category ad-hoc 20%-cut math.
                YearMonth nextMonth = YearMonth.of(year, month).plusMonths(1);
                var suggestions = computeNextMonthBudgetSuggestions(userId, nextMonth, messageLow);
                String actionId = suggestions.isEmpty() ? null
                        : storePendingAction(userId, nextMonthBudgetPlanAction(nextMonth, suggestions), messageLow);
                yield ChatResponse.builder()
                        .reply(reply)
                        .intent(intent)
                        .budgetSuggestions(suggestions.isEmpty() ? null : suggestions)
                        .suggestedMonth(suggestions.isEmpty() ? null : nextMonth.getMonthValue())
                        .suggestedYear(suggestions.isEmpty() ? null : nextMonth.getYear())
                        .pendingActionId(actionId)
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
                String actionId = storePendingActionIfAny(userId, shortfallSuggestions, null, messageLow);
                yield ChatResponse.builder()
                        .reply(reply)
                        .intent(intent)
                        .simulationResult(simResult)
                        .budgetSuggestions(shortfallSuggestions.isEmpty() ? null : shortfallSuggestions)
                        .pendingActionId(actionId)
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

                String actionId = storePendingActionIfAny(userId, budgetBoosts, savingsSuggestions, messageLow);
                yield ChatResponse.builder()
                        .reply(reply)
                        .intent(intent)
                        .budgetSuggestions(budgetBoosts.isEmpty() ? null : budgetBoosts)
                        .savingsSuggestions(savingsSuggestions.isEmpty() ? null : savingsSuggestions)
                        .pendingActionId(actionId)
                        .actionRequired(!budgetBoosts.isEmpty() || !savingsSuggestions.isEmpty())
                        .relatedQuestions(relatedQuestions)
                        .build();
            }
            case LARGE_EXPENSE -> {
                // AI detected large unplanned expense → suggest budget coverage + idle
                // abandoned-project funds + (only if still short) active-project deferral.
                // Same scoping rule as INCOME_WINDFALL above.
                boolean showBudget = wantsBudgetSuggestion(messageLow);
                boolean showProject = wantsProjectSuggestion(messageLow);
                boolean scoped = showBudget || showProject;

                var coverage = computeExpenseCoverage(userId, month, year, messageLow, scoped, showBudget, showProject);

                String actionId = storePendingActionIfAny(userId, coverage.budgetCoverage(), coverage.savings(), messageLow);
                yield ChatResponse.builder()
                        .reply(reply)
                        .intent(intent)
                        .budgetSuggestions(coverage.budgetCoverage().isEmpty() ? null : coverage.budgetCoverage())
                        .savingsSuggestions(coverage.savings().isEmpty() ? null : coverage.savings())
                        .pendingActionId(actionId)
                        .actionRequired(!coverage.budgetCoverage().isEmpty() || !coverage.savings().isEmpty())
                        .relatedQuestions(relatedQuestions)
                        .build();
            }
            case PROJECT_UPDATE -> {
                // User directly asked to adjust a project's tracked income (contribution)
                // or expense (money owed) — not triggered by a windfall/large-expense event.
                var suggestions = suppressCurrentMonth
                        ? Collections.<ChatResponse.SavingsPlanSuggestion>emptyList()
                        : projectUpdateSuggestionService.suggest(userId, messageLow);
                String actionId = storePendingActionIfAny(userId, null, suggestions, messageLow);
                String finalReply = reply;
                if (suggestions.isEmpty() && !suppressCurrentMonth) {
                    String note = namedInactiveProjectNote(userId, messageLow);
                    if (note != null) finalReply = appendToReply(reply, note);
                }
                yield ChatResponse.builder()
                        .reply(finalReply)
                        .intent(intent)
                        .savingsSuggestions(suggestions.isEmpty() ? null : suggestions)
                        .pendingActionId(actionId)
                        .actionRequired(!suggestions.isEmpty())
                        .relatedQuestions(relatedQuestions)
                        .build();
            }
            case PROJECT_HYPOTHESIS -> {
                if (suppressCurrentMonth) {
                    yield ChatResponse.builder()
                            .reply(reply).intent(intent).relatedQuestions(relatedQuestions).build();
                }
                var suggestions = computeProjectHypothesisSuggestions(userId, messageLow);
                String actionId = suggestions.isEmpty() ? null
                        : storePendingAction(userId, pendingActionForSuggestions(suggestions), messageLow);
                String hypothesisReply = reply;
                if (suggestions.isEmpty()) {
                    String note = namedInactiveProjectNote(userId, messageLow);
                    if (note != null) hypothesisReply = appendToReply(reply, note);
                }
                yield ChatResponse.builder()
                        .reply(hypothesisReply)
                        .intent(intent)
                        .projectChangeSuggestions(suggestions.isEmpty() ? null : suggestions)
                        .pendingActionId(actionId)
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
                // GENERAL_ADVICE: the LLM didn't flag this as a windfall/large-expense/project-
                // hypothesis intent, but the message may still describe one in passing (e.g.
                // "lương tháng này tăng 3 triệu, tôi nên làm gì?" answered generically). Attach the
                // same suggestions those intents would as a safety net, without overriding intent.
                //
                // Structural project-change phrasing ("rút ngắn dự án X 3 tháng") is checked
                // FIRST and, if matched, is the ONLY safety net allowed to fire this turn — even
                // when it can't compute a full preview (e.g. no duration given). This is
                // deliberate: EXPENSE_EVENT_PATTERN below matches "mua xe"/"mua nhà" etc. as
                // substrings, which also matches inside a PROJECT NAME like "dự án mua xe" — so an
                // explicit shorten/extend/create/delete request could otherwise get misread as a
                // brand-new unplanned expense and answered with an unrelated (and sometimes
                // contradictory — e.g. deferral always EXTENDS) suggestion instead of the shorten
                // the user actually asked for.
                var builder = ChatResponse.builder()
                        .reply(reply)
                        .intent(intent)
                        .relatedQuestions(relatedQuestions);
                boolean looksStructural = projectHypothesisService.looksLikeStructuralChangeRequest(messageLow);
                boolean handled = looksStructural && !suppressCurrentMonth
                        && attachImplicitProjectHypothesis(builder, userId, messageLow);
                if (looksStructural && !handled) {
                    String note = namedInactiveProjectNote(userId, messageLow);
                    if (note != null) builder.reply(appendToReply(reply, note));
                }
                if (!handled && !looksStructural) {
                    handled = attachImplicitEventSuggestions(builder, userId, month, year, messageLow);
                }
                // Don't pile a second, unrelated suggestion onto a turn that already got one, and
                // never volunteer one on a bare greeting/farewell or a structural-change request
                // (an unrelated proactive nudge, e.g. checkFeasibility's "extend this project",
                // could easily contradict what the user just asked for).
                if (!handled && !looksStructural && !GREETING_OR_FAREWELL.matcher(messageLow.trim()).matches()) {
                    attachProactiveSuggestions(builder, reply, userId, month, year, daysLeft, messageLow);
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

    // BUDGET_UPDATE direction: "increase" phrasing routes to the neutral swap suggestion
    // instead of the plain reduction path.
    private static final Pattern BUDGET_INCREASE_PATTERN = Pattern.compile(
            "tăng|nâng (mức|hạn mức)|thêm hạn mức|increase|raise the limit|more budget",
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
            String actionId = storePendingActionIfAny(userId, budgets, savings, messageLow);
            builder.budgetSuggestions(budgets.isEmpty() ? null : budgets)
                   .savingsSuggestions(savings.isEmpty() ? null : savings)
                   .pendingActionId(actionId)
                   .actionRequired(!budgets.isEmpty() || !savings.isEmpty());
            return true;
        } else if (EXPENSE_EVENT_PATTERN.matcher(messageLow).find()) {
            var coverage = computeExpenseCoverage(userId, month, year, messageLow, scoped, showBudget, showProject);
            String actionId = storePendingActionIfAny(userId, coverage.budgetCoverage(), coverage.savings(), messageLow);
            builder.budgetSuggestions(coverage.budgetCoverage().isEmpty() ? null : coverage.budgetCoverage())
                   .savingsSuggestions(coverage.savings().isEmpty() ? null : coverage.savings())
                   .pendingActionId(actionId)
                   .actionRequired(!coverage.budgetCoverage().isEmpty() || !coverage.savings().isEmpty());
            return true;
        }
        return false;
    }

    /**
     * Computes LARGE_EXPENSE coverage in priority order: budget headroom (unchanged), then idle
     * funds sitting in an ABANDONED project (real money already saved, no longer earmarked for
     * anything), then — only for whatever shortfall remains — deferring an ACTIVE project's
     * deadline. Shared by the explicit LARGE_EXPENSE intent and the implicit expense-event safety
     * net so both stay in sync.
     */
    private ExpenseCoverage computeExpenseCoverage(UUID userId, int month, int year, String messageLow,
                                                     boolean scoped, boolean showBudget, boolean showProject) {
        var budgetCoverage = (!scoped || showBudget)
                ? windfallAllocationService.suggestExpenseCoverage(userId, month, year, messageLow)
                : Collections.<ChatResponse.BudgetUpdateSuggestion>emptyList();

        List<ChatResponse.SavingsPlanSuggestion> savings = new ArrayList<>();
        if (!scoped || showProject) {
            var abandonedFunds = windfallAllocationService.suggestAbandonedProjectFunds(userId, messageLow);
            savings.addAll(abandonedFunds);
            BigDecimal abandonedTotal = abandonedFunds.stream()
                    .map(s -> BigDecimal.valueOf(Math.abs(s.getSuggestedAddAmount())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            savings.addAll(windfallAllocationService.suggestProjectDeferral(
                    userId, month, year, messageLow, abandonedTotal));
        }
        return new ExpenseCoverage(budgetCoverage, savings);
    }

    private record ExpenseCoverage(List<ChatResponse.BudgetUpdateSuggestion> budgetCoverage,
                                    List<ChatResponse.SavingsPlanSuggestion> savings) {}

    /**
     * GENERAL_ADVICE safety net mirroring {@link #attachImplicitEventSuggestions} but for
     * structural project-change phrasing (shorten/extend/create/delete) instead of windfall/
     * expense wording — guarantees the deterministic {@link ProjectHypothesisService} computation
     * wins whenever the message unambiguously asks for one of those, regardless of what the LLM
     * classified the intent as.
     *
     * @return true when a computable suggestion was found (mutates {@code builder} in place)
     */
    boolean attachImplicitProjectHypothesis(ChatResponse.ChatResponseBuilder builder,
                                             UUID userId, String messageLow) {
        var suggestions = computeProjectHypothesisSuggestions(userId, messageLow);
        if (suggestions.isEmpty()) return false;
        String actionId = storePendingAction(userId, pendingActionForSuggestions(suggestions), messageLow);
        builder.projectChangeSuggestions(suggestions).pendingActionId(actionId).actionRequired(true);
        return true;
    }

    /** Multi-project batch if ≥2 known project names are mentioned, else a single-project preview. */
    private List<ChatResponse.ProjectChangeSuggestion> computeProjectHypothesisSuggestions(
            UUID userId, String messageLow) {
        // Bulk delete-by-status ("xóa các dự án đã hoàn thành") checked first — most specific
        // match, and doesn't require any project to be named.
        var byStatus = projectHypothesisService.previewDeleteByStatus(userId, messageLow);
        if (!byStatus.isEmpty()) return byStatus;
        var multi = projectHypothesisService.previewMultiProject(userId, messageLow);
        if (!multi.isEmpty()) return multi;
        return projectHypothesisService.previewCreate(userId, messageLow)
                .or(() -> projectHypothesisService.previewDelete(userId, messageLow))
                .or(() -> projectHypothesisService.previewShortenOrExtend(userId, messageLow))
                .map(List::of)
                .orElse(Collections.emptyList());
    }

    private PendingChatAction pendingActionForSuggestions(List<ChatResponse.ProjectChangeSuggestion> suggestions) {
        if (suggestions.size() == 1) return toPendingAction(suggestions.get(0));
        return PendingChatAction.builder()
                .type(PendingChatAction.Type.BATCH)
                .batchActions(suggestions.stream().map(this::toPendingAction).toList())
                .build();
    }

    /**
     * Every suggestion-producing branch (windfall, large-expense, plain contribution/debt update,
     * simulation shortfall, and the GENERAL_ADVICE safety net for either) funnels through here so
     * the client has one uniform confirm/dismiss action for every suggestion type, not just
     * PROJECT_HYPOTHESIS/BUDGET_UPDATE. Persists a batch pending action (single entry if there's
     * only one) and returns its id, or null when there's nothing to confirm.
     */
    private String storePendingActionIfAny(UUID userId,
                                            List<ChatResponse.BudgetUpdateSuggestion> budgetSuggestions,
                                            List<ChatResponse.SavingsPlanSuggestion> savingsSuggestions,
                                            String messageLow) {
        List<PendingChatAction> subActions = new ArrayList<>();
        if (budgetSuggestions != null) {
            for (var b : budgetSuggestions) {
                // No existing budgetId to act on (shouldn't occur on this path — the next-month
                // plan, the only budgetId-less suggestion source, has its own dedicated action).
                if (b.getBudgetId() == null) continue;
                subActions.add(PendingChatAction.builder()
                        .type(PendingChatAction.Type.UPDATE_BUDGET_LIMIT)
                        .budgetId(b.getBudgetId())
                        .newAmountLimit(BigDecimal.valueOf(b.getSuggestedAmount()))
                        .build());
            }
        }
        if (savingsSuggestions != null) {
            for (var s : savingsSuggestions) {
                subActions.add(PendingChatAction.builder()
                        .type(PendingChatAction.Type.PROJECT_TRACKING_UPDATE)
                        .projectId(s.getProjectId())
                        .trackingMoneySaved(s.getNewMoneySaved() != null
                                ? BigDecimal.valueOf(s.getNewMoneySaved()) : null)
                        .trackingMonthLeft(s.getSuggestedMonthLeft())
                        .trackingMoneyOwed(s.getSuggestedMoneyOwed() != null
                                ? BigDecimal.valueOf(s.getSuggestedMoneyOwed()) : null)
                        .build());
            }
        }
        if (subActions.isEmpty()) return null;
        PendingChatAction action = subActions.size() == 1
                ? subActions.get(0)
                : PendingChatAction.builder().type(PendingChatAction.Type.BATCH).batchActions(subActions).build();
        return storePendingAction(userId, action, messageLow);
    }

    /**
     * Proactive safety net for a plain GENERAL_ADVICE turn with no explicit ask and no implicit
     * windfall/expense event: volunteers at most ONE nudge, checked in priority order — a budget
     * nearing its limit, an unsustainable project commitment, a month-end leftover to park, or a
     * multi-month surplus streak — so the user isn't shown several unrelated nudges at once.
     *
     * <p>Unlike a suggestion the user explicitly asked for, this ASKS first instead of attaching
     * a ready-to-confirm structured payload: the nudge's question is appended to {@code reply}
     * only — {@code budgetSuggestions}/{@code savingsSuggestions}/{@code actionRequired} are left
     * untouched (no {@code pendingActionId} either). If the user says yes on a follow-up turn,
     * that's a new explicit request handled by the normal BUDGET_UPDATE/PROJECT_UPDATE/
     * PROJECT_HYPOTHESIS path, which does attach a confirmable suggestion.
     */
    void attachProactiveSuggestions(ChatResponse.ChatResponseBuilder builder, String reply,
                                     UUID userId, int month, int year, int daysLeft, String messageLow) {
        boolean english = MessageLanguageDetector.isEnglish(messageLow);
        var snapshot = dataProvider.getFinancialSnapshot(userId, month, year);

        var nearLimit = budgetAdvisorService.checkNearLimit(userId, month, year, daysLeft, english);
        if (nearLimit.isPresent()) {
            builder.reply(appendToReply(reply, nearLimit.get().getReason()));
            return;
        }

        var infeasible = projectUpdateSuggestionService.checkFeasibility(userId, snapshot.monthlyIncome(), english);
        if (infeasible.isPresent()) {
            builder.reply(appendToReply(reply, infeasible.get().getReason()));
            return;
        }

        var leftover = projectUpdateSuggestionService.checkMonthEndLeftover(
                userId, daysLeft, snapshot.safeSpending(), english);
        if (leftover.isPresent()) {
            builder.reply(appendToReply(reply, leftover.get().getReason()));
            return;
        }

        var surplusTrend = projectUpdateSuggestionService.checkSurplusTrend(userId, english);
        surplusTrend.ifPresent(s -> builder.reply(appendToReply(reply, s.getReason())));
    }

    /** Appends a note/question/warning as a new paragraph onto an existing reply. */
    private String appendToReply(String reply, String note) {
        return (reply == null || reply.isBlank()) ? note : reply.trim() + "\n\n" + note;
    }

    /**
     * When a project-update/hypothesis suggestion comes back empty, checks whether that's
     * because the user named a project that exists but isn't ACTIVE anymore — silently doing
     * nothing reads as the request being ignored, so this makes the reason explicit instead.
     *
     * @return a note to append to the reply, or null when the empty result isn't explained by
     *         an inactive-project name match (e.g. no duration parsed, no project named at all)
     */
    private String namedInactiveProjectNote(UUID userId, String messageLow) {
        boolean english = MessageLanguageDetector.isEnglish(messageLow);
        return projectUpdateSuggestionService.findNamedInactiveProject(userId, messageLow)
                .map(p -> english
                        ? String.format("Project '%s' is %s, so it can't be updated anymore.",
                                p.getName(), ProjectUpdateSuggestionService.statusPhrase(p.getStatus(), true))
                        : String.format("Dự án '%s' hiện %s nên không thể cập nhật thêm.",
                                p.getName(), ProjectUpdateSuggestionService.statusPhrase(p.getStatus(), false)))
                .orElse(null);
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

    // ── Confirm-then-execute: pending-action persistence ────────────────────

    /**
     * Persists {@code action} under a fresh id and returns it for {@code ChatResponse.pendingActionId}.
     * Also records the message's language on the action (needed by {@link ConfirmChatActionUseCase},
     * which has no prose of its own to detect language from) and marks this as the user's latest
     * pending action, so a later bare "yes"/"no" reply can resolve to it without an explicit id — see
     * {@link #tryHandlePlainAgreement}.
     */
    private String storePendingAction(UUID userId, PendingChatAction action, String messageLow) {
        String actionId = UUID.randomUUID().toString();
        action.setEnglish(MessageLanguageDetector.isEnglish(messageLow));
        chatPendingActionPort.save(userId, actionId, action);
        chatPendingActionPort.saveLatestId(userId, actionId);
        return actionId;
    }

    /** Converts a project-hypothesis preview into the real mutation {@link ConfirmChatActionUseCase} will run. */
    private PendingChatAction toPendingAction(ChatResponse.ProjectChangeSuggestion s) {
        return switch (s.getOperation()) {
            case CREATE -> PendingChatAction.builder()
                    .type(PendingChatAction.Type.PROJECT_CREATE)
                    .name(s.getProjectName())
                    .targetAmount(BigDecimal.valueOf(s.getTargetAmount()))
                    .currency("VND")
                    .newDeadline(s.getNewDeadline())
                    .build();
            case SHORTEN, EXTEND -> PendingChatAction.builder()
                    .type(PendingChatAction.Type.PROJECT_UPDATE_DEADLINE)
                    .projectId(s.getProjectId())
                    .newDeadline(s.getNewDeadline())
                    .build();
            case DELETE -> PendingChatAction.builder()
                    .type(PendingChatAction.Type.PROJECT_DELETE)
                    .projectId(s.getProjectId())
                    .name(s.getProjectName())
                    .build();
        };
    }

    /** Batches a budget-increase swap (the raised category + its lowest-spend offsets) into one action. */
    private PendingChatAction budgetSwapAction(List<ChatResponse.BudgetUpdateSuggestion> swap) {
        return PendingChatAction.builder()
                .type(PendingChatAction.Type.UPDATE_BUDGET_LIMIT_SWAP)
                .batchActions(swap.stream()
                        .map(s -> PendingChatAction.builder()
                                .type(PendingChatAction.Type.UPDATE_BUDGET_LIMIT)
                                .budgetId(s.getBudgetId())
                                .newAmountLimit(BigDecimal.valueOf(s.getSuggestedAmount()))
                                .build())
                        .toList())
                .build();
    }

    /**
     * Full per-category plan for {@code targetMonth}, via {@link ComputeBudgetAllocationUseCase}
     * (the same deterministic allocator {@code POST /api/v1/budgets/allocation/compute} uses) —
     * never the current month, never more than one month ahead. Returns empty when the plan has
     * no allocations (e.g. income not configured) instead of throwing.
     */
    private List<ChatResponse.BudgetUpdateSuggestion> computeNextMonthBudgetSuggestions(
            UUID userId, YearMonth targetMonth, String messageLow) {
        var plan = computeBudgetAllocationUseCase.execute(userId, targetMonth.atDay(1));
        if (plan.getAllocations() == null || plan.getAllocations().isEmpty()) {
            return Collections.emptyList();
        }
        boolean english = MessageLanguageDetector.isEnglish(messageLow);
        return plan.getAllocations().stream()
                .map(a -> ChatResponse.BudgetUpdateSuggestion.builder()
                        .budgetId(null)
                        .category(a.getCategory().name())
                        .currentAmount(null)
                        .suggestedAmount(a.getAmount().longValue())
                        .reason(english
                                ? String.format("Suggested %s budget for %d/%d: %,.0f VND.",
                                        a.getCategory().name(), plan.getMonth(), plan.getYear(), a.getAmount())
                                : String.format("Đề xuất ngân sách %s cho tháng %d/%d: %,.0f VND.",
                                        a.getCategory().name(), plan.getMonth(), plan.getYear(), a.getAmount()))
                        .build())
                .toList();
    }

    private PendingChatAction nextMonthBudgetPlanAction(
            YearMonth targetMonth, List<ChatResponse.BudgetUpdateSuggestion> suggestions) {
        return PendingChatAction.builder()
                .type(PendingChatAction.Type.APPLY_NEXT_MONTH_BUDGET_PLAN)
                .targetMonth(targetMonth.getMonthValue())
                .targetYear(targetMonth.getYear())
                .categoryAmounts(suggestions.stream()
                        .map(s -> PendingChatAction.CategoryAmount.builder()
                                .category(s.getCategory())
                                .amountLimit(BigDecimal.valueOf(s.getSuggestedAmount()))
                                .build())
                        .toList())
                .build();
    }

    // A requested category increase more than doubling its current limit is flagged as abnormal —
    // simple, explainable heuristic (not tied to income/history) matching common financial-advisor
    // "that's a big jump" red flags.
    private static final BigDecimal ABNORMAL_INCREASE_MULTIPLIER = BigDecimal.valueOf(2);

    /**
     * Flags any category in a budget-increase swap whose suggested amount is more than double its
     * current limit, and phrases a warning tuned to the user's {@code Saving_Pace} — RELAXED gets
     * a gentle heads-up, AGGRESSIVE gets a direct, consequence-naming warning, BALANCED sits
     * between the two. Mirrors the tone mapping {@code ChatbotPromptBuilder} already uses for the
     * LLM's own replies, applied here to a Java-generated warning string instead.
     *
     * @return the warning to append to the reply, or null when nothing in {@code suggestions}
     *         crosses the threshold
     */
    private String abnormalIncreaseWarning(List<ChatResponse.BudgetUpdateSuggestion> suggestions,
                                            User user, String messageLow) {
        if (suggestions == null || suggestions.isEmpty()) return null;
        var abnormal = suggestions.stream()
                .filter(s -> s.getCurrentAmount() != null && s.getCurrentAmount() > 0
                        && s.getSuggestedAmount() != null
                        && BigDecimal.valueOf(s.getSuggestedAmount()).compareTo(
                                BigDecimal.valueOf(s.getCurrentAmount()).multiply(ABNORMAL_INCREASE_MULTIPLIER)) > 0)
                .map(ChatResponse.BudgetUpdateSuggestion::getCategory)
                .toList();
        if (abnormal.isEmpty()) return null;

        boolean english = MessageLanguageDetector.isEnglish(messageLow);
        String categories = String.join(", ", abnormal);
        Saving_Pace pace = user != null ? user.getSavingPace() : null;

        if (pace == Saving_Pace.AGGRESSIVE) {
            return english
                    ? String.format("⚠️ Warning: the requested increase for %s is more than double the " +
                            "current limit — an unusually large jump that could seriously hurt your " +
                            "monthly savings if it's not reversed later.", categories)
                    : String.format("⚠️ Cảnh báo: mức tăng cho %s cao hơn gấp đôi hạn mức hiện tại — đây " +
                            "là mức tăng bất thường, có thể ảnh hưởng nghiêm trọng đến tiết kiệm hàng " +
                            "tháng của bạn nếu không điều chỉnh lại sau này.", categories);
        } else if (pace == Saving_Pace.RELAXED) {
            return english
                    ? String.format("Just a gentle note: the new amount for %s is quite a bit higher than " +
                            "before — worth keeping an eye on, no rush to change anything.", categories)
                    : String.format("Lưu ý nhẹ nhàng thôi: mức mới cho %s cao hơn khá nhiều so với trước — " +
                            "bạn để ý một chút, không cần vội điều chỉnh gì cả.", categories);
        }
        return english
                ? String.format("Note: the increase for %s is more than double the current limit — please " +
                        "make sure this still fits your overall budget.", categories)
                : String.format("Lưu ý: mức tăng cho %s cao hơn gấp đôi hạn mức hiện tại — bạn kiểm tra lại " +
                        "xem có còn phù hợp với tổng ngân sách không nhé.", categories);
    }
}
