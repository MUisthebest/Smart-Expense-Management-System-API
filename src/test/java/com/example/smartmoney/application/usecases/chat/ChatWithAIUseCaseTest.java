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
import com.example.smartmoney.domain.models.project.Project;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import com.example.smartmoney.domain.models.project.valueobjects.UserId;
import com.example.smartmoney.domain.models.user.User;
import com.example.smartmoney.domain.models.user.enums.Focus_Mode;
import com.example.smartmoney.domain.models.user.enums.Saving_Pace;
import com.example.smartmoney.domain.repositories.ProjectRepository;
import com.example.smartmoney.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
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
public class ChatWithAIUseCaseTest {

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
    // Defense-in-depth for §5 of ChatbotPromptBuilder ("group-project isolation"): even if the
    // LLM or one of the deterministic suggestion services proposes a change to a sub-personal
    // project (a personal Project with groupProjectId != null, i.e. part of a group commitment),
    // we filter it out here before returning to the client. That way a stray "yes" reply can
    // never silently mutate a shared group goal — the prompt says "don't propose it", the
    // services never had a reason to check, so the orchestrator draws the final line.
    private final ProjectRepository projectRepository;
    // Injected so the days-left month-end gate is testable at a pinned date rather than being
    // silently coupled to the wall-clock (see DomainServiceConfig's systemDefaultZone bean).
    private final Clock clock;

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

        LocalDate now = LocalDate.now(clock);
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

        LocalDate now = LocalDate.now(clock);
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
                    List.of(),
                    null, null, null);
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

        // BUG: the "reply in the user's language" rule lives in ChatbotPromptBuilder's system
        // prompt — a static instruction buried among dozens of other rules, competing with an
        // entirely-Vietnamese system prompt AND entirely-Vietnamese financial-context labels
        // above ("Dữ liệu tài chính", "Ngân sách tháng...", ...). For an English message the
        // model has a wall of Vietnamese text surrounding one static rule to counter it, and
        // English messages measurably slipped through as Vietnamese replies. Since
        // MessageLanguageDetector already deterministically classifies the message elsewhere in
        // this class (for tagging ChatResponse.english), reuse it here to inject an explicit,
        // per-turn directive right before generation — closest to the output, hardest to miss.
        boolean english = MessageLanguageDetector.isEnglish(message.toLowerCase());
        sb.append(english
                ? "\n\n=== REQUIRED LANGUAGE FOR THIS REPLY ===\nThe user's message above is in " +
                  "ENGLISH. Your \"reply\" (or the entire answer, if this isn't a JSON turn) MUST " +
                  "be written ENTIRELY in English — do not translate it to Vietnamese, do not mix " +
                  "languages, even though the financial data and the rest of this prompt are in " +
                  "Vietnamese."
                : "\n\n=== NGÔN NGỮ BẮT BUỘC CHO LƯỢT NÀY ===\nTin nhắn của người dùng ở trên là " +
                  "TIẾNG VIỆT. \"reply\" (hoặc toàn bộ câu trả lời, nếu lượt này không phải JSON) " +
                  "PHẢI viết hoàn toàn bằng tiếng Việt — không chuyển sang tiếng Anh, không trộn " +
                  "ngôn ngữ.");

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

        // §5 group-project isolation — load once per turn and pass into every branch that
        // produces a project-typed suggestion. Even though the prompt tells the LLM never to
        // propose changes to a project with linkedToGroup=true, and buildChatContext now
        // surfaces that flag, we still strip stray sub-personal entries at the orchestrator
        // level so a stray "yes" can never mutate a shared group commitment.
        Set<String> groupLinkedIds = loadSubPersonalProjectIds(userId);

        return switch (intent) {
            case BUDGET_UPDATE -> {
                // "increase 1-2 categories" is offered as a neutral swap (raise the requested
                // category, cover it by cutting the lowest-spend category) rather than approved
                // outright — this path still targets the CURRENT month, so the days-left gate
                // still applies to it.
                if (BUDGET_INCREASE_PATTERN.matcher(messageLow).find()) {
                    // The increase-swap path only ever computes/applies against the CURRENT
                    // queried month — there's no "increase-swap for next month" mechanism (unlike
                    // the cut path just below, which has a real next-month plan). If the user
                    // explicitly insists this apply to a future month ("tháng sau tăng ngân sách
                    // X lên..."), attaching a confirm button here would silently apply it to the
                    // WRONG (current) month instead — never attach one in that case, even if the
                    // user pushes for it; same reasoning as INCOME_WINDFALL/LARGE_EXPENSE's
                    // future-dated gate (see FUTURE_INTENT_PATTERN).
                    if (suppressCurrentMonth || looksFutureDated(messageLow)) {
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
                // A hypothetical/future windfall ("nếu tôi được thưởng KPI thì sao") must never
                // get a real, confirmable suggestion — see HYPOTHETICAL_PATTERN's doc comment.
                // The LLM's own reply already carries advisory-only text for this case per
                // ChatbotPromptBuilder's rule; just pass it through with no suggestion attached.
                if (isHypothetical(messageLow)) {
                    yield ChatResponse.builder()
                            .reply(reply).intent(intent).relatedQuestions(relatedQuestions).build();
                }
                // Real money, but the user explicitly frames applying it as a FUTURE plan — see
                // FUTURE_INTENT_PATTERN's doc comment. No suggestion card (nothing to schedule
                // for a later month here), but unlike the hypothetical branch above the amount is
                // real/known, so the LLM's reply is still expected to state it.
                if (looksFutureDated(messageLow)) {
                    yield ChatResponse.builder()
                            .reply(reply).intent(intent).relatedQuestions(relatedQuestions).build();
                }
                // AI detected sudden income → allocate 60% to savings, 40% to budget limits.
                // Only surface the side(s) the user actually asked about; if neither budget
                // nor project is explicitly mentioned, default to the user's financial-setup
                // profile (see windfallShouldPreferSavingsOnly) before falling back to "show both".
                boolean showBudget = wantsBudgetSuggestion(messageLow);
                boolean showProject = wantsProjectSuggestion(messageLow);
                boolean scoped = showBudget || showProject;
                if (!scoped && windfallShouldPreferSavingsOnly(user, userId, month, year)) {
                    scoped = true;
                    showProject = true;
                }

                var savingsSuggestions = (!scoped || showProject)
                        ? windfallAllocationService.suggestSavingsBoost(userId, month, year, messageLow)
                        : Collections.<ChatResponse.SavingsPlanSuggestion>emptyList();
                var budgetBoosts = (!scoped || showBudget)
                        ? windfallAllocationService.suggestBudgetBoosts(userId, month, year, messageLow)
                        : Collections.<ChatResponse.BudgetUpdateSuggestion>emptyList();

                // §5: never propose adding a windfall lump-sum to a group-linked project via chat.
                savingsSuggestions = stripGroupLinkedSavings(savingsSuggestions, groupLinkedIds);

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
                // LARGE_EXPENSE is now a pure feasibility ASSESSMENT, never a suggestion card: a
                // one-off transaction judged against safe_spending (see ChatbotPromptBuilder) —
                // no budget-category change, no abandoned-project idle-funds reuse, no
                // active-project deferral. The LLM already has safe_spending and this month's
                // recorded income in its financial context, so no Java computation is needed;
                // this branch only still exists to apply the hypothetical/future-dated gates.
                if (isHypothetical(messageLow)) {
                    yield ChatResponse.builder()
                            .reply(reply).intent(intent).relatedQuestions(relatedQuestions).build();
                }
                // Real expense, but explicitly planned to be COVERED later ("tháng sau tôi sẽ trả
                // khoản này") — same reasoning as INCOME_WINDFALL's future-dated gate above.
                if (looksFutureDated(messageLow)) {
                    yield ChatResponse.builder()
                            .reply(reply).intent(intent).relatedQuestions(relatedQuestions).build();
                }
                yield ChatResponse.builder()
                        .reply(reply)
                        .intent(intent)
                        .relatedQuestions(relatedQuestions)
                        .build();
            }
            case PROJECT_UPDATE -> {
                // User directly asked to adjust a project's tracked income (contribution)
                // or expense (money owed) — not triggered by a windfall/large-expense event.
                // Same future-dated gate as BUDGET_UPDATE's increase-swap/INCOME_WINDFALL above:
                // suggest()/UpdateProjectTrackingUseCase only ever apply to the project's CURRENT
                // state — an explicit "tháng sau tôi sẽ góp thêm..." has no real mechanism to
                // schedule against, so no confirm button even if the user pushes for one.
                boolean deferredToFuture = looksFutureDated(messageLow);
                var suggestions = (suppressCurrentMonth || deferredToFuture)
                        ? Collections.<ChatResponse.SavingsPlanSuggestion>emptyList()
                        : projectUpdateSuggestionService.suggest(userId, messageLow);
                // §5: strip suggestions that target a sub-personal (group-linked) project. If
                // the user's PROJECT_UPDATE request named a group-linked project explicitly, this
                // list ends up empty — the fallthrough below to namedInactiveProjectNote handles
                // any messaging gap, and the LLM's reply itself was already told not to propose.
                suggestions = stripGroupLinkedSavings(suggestions, groupLinkedIds);
                String actionId = storePendingActionIfAny(userId, null, suggestions, messageLow);
                String finalReply = reply;
                if (suggestions.isEmpty() && !suppressCurrentMonth && !deferredToFuture) {
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
                // Same "no confirm for an explicitly deferred request" gate as BUDGET_UPDATE/
                // PROJECT_UPDATE above: "tháng sau tôi định rút ngắn dự án X 3 tháng" means the
                // user wants to decide/apply this NEXT month, not right now — attaching a real
                // confirm button today would let a habitual "có" apply the change immediately,
                // contradicting what they just said. Low false-positive risk: previewCreate/
                // previewShortenOrExtend both require an explicit "N tháng" (a digit) to compute
                // anything at all, so "tháng sau"/"tháng tới" describing a target date without a
                // number already yields no suggestion regardless of this gate.
                if (suppressCurrentMonth || looksFutureDated(messageLow)) {
                    yield ChatResponse.builder()
                            .reply(reply).intent(intent).relatedQuestions(relatedQuestions).build();
                }
                var suggestions = computeProjectHypothesisSuggestions(userId, messageLow);
                // §5: strip shorten/extend/delete previews that target a group-linked project.
                // CREATE previews have projectId=null (project doesn't exist yet) — never removed.
                suggestions = stripGroupLinkedProjectChanges(suggestions, groupLinkedIds);
                String actionId = suggestions.isEmpty() ? null
                        : storePendingAction(userId, pendingActionForSuggestions(userId, suggestions), messageLow);
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
                boolean handled = looksStructural && !suppressCurrentMonth && !looksFutureDated(messageLow)
                        && attachImplicitProjectHypothesis(builder, userId, messageLow, groupLinkedIds);
                if (looksStructural && !handled) {
                    String note = namedInactiveProjectNote(userId, messageLow);
                    if (note != null) builder.reply(appendToReply(reply, note));
                }
                if (!handled && !looksStructural) {
                    handled = attachImplicitEventSuggestions(
                            builder, user, userId, month, year, messageLow, groupLinkedIds);
                }
                // §3 Rule A: proactive nudges (near-limit warnings, feasibility checks, month-end
                // leftover offers, surplus-trend suggestions) all assume the month has real
                // headroom. When cash-flow is DEFICIT, offering to top up a saving project or
                // extend a limit contradicts the LLM's own reply, which by §3 must warn about the
                // shortfall. Skip proactive suggestions entirely in that case — the LLM reply is
                // the whole response for the turn.
                boolean deficit = monthInDeficit(userId, month, year);
                if (!handled && !looksStructural && !deficit
                        && !GREETING_OR_FAREWELL.matcher(messageLow.trim()).matches()) {
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

    // A message phrased as a hypothetical ("nếu tôi được thưởng KPI thì sao", "what if I get a
    // bonus") describes an income/expense event that HASN'T actually happened yet — unlike an
    // already-received windfall or already-incurred expense, it must never get a real, confirmable
    // suggestion. The suggestion-computation services below (WindfallAllocationService) only ever
    // target the CURRENT month's real budget/project rows; confirming a suggestion built from money
    // the user doesn't actually have yet would incorrectly fold a future what-if into the current
    // month's real data. Only advisory text (already tone-adjusted by ChatbotPromptBuilder per the
    // user's saving pace / intervention level / focus mode) is appropriate for a pure hypothetical.
    private static final Pattern HYPOTHETICAL_PATTERN = Pattern.compile(
            "\\bnếu\\b|giả sử|giả định|trong trường hợp|what if|in case",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private boolean isHypothetical(String messageLow) {
        return HYPOTHETICAL_PATTERN.matcher(messageLow).find();
    }

    // A message describing a REAL (non-hypothetical) income/expense event that the user
    // explicitly says will only be APPLIED at some later, unspecified time ("tháng sau tôi định
    // góp vào...", "sắp tới sẽ dùng để..."). The money/expense itself is real and its amount is
    // known, but — unlike BUDGET_UPDATE's cut path, which has a dedicated next-month mechanism
    // (APPLY_NEXT_MONTH_BUDGET_PLAN) — there is no way to schedule a windfall/expense allocation
    // for a later month here, so no confirmable suggestion is attached. Distinct from
    // HYPOTHETICAL_PATTERN: this is real money, just deferred application, so the reply should
    // still state the known amount — only the actionable card is suppressed, not the number.
    private static final Pattern FUTURE_INTENT_PATTERN = Pattern.compile(
            "tháng sau|tháng tới|tháng kế|kỳ tới|sắp tới sẽ|dự định|định sẽ|tính sẽ|kế hoạch sẽ|" +
                    "sẽ dùng để|sẽ dùng vào|next month|plan to|planning to|going to use it",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private boolean looksFutureDated(String messageLow) {
        return FUTURE_INTENT_PATTERN.matcher(messageLow).find();
    }

    // "Ample" safe spending for the windfall-allocation profile check below — same income-ratio
    // style threshold this codebase already uses elsewhere (e.g. ProjectUpdateSuggestionService's
    // leftover/surplus nudges), not a new convention.
    private static final BigDecimal AMPLE_SAFE_SPENDING_RATIO = BigDecimal.valueOf(0.20);
    private static final BigDecimal AMPLE_SAFE_SPENDING_FALLBACK = BigDecimal.valueOf(2_000_000);

    /**
     * Financial-setup profile as an ALLOCATION factor, not just tone: when the user hasn't said
     * which side (budget vs project) an unscoped windfall should go to, a REDUCE_SPENDING/
     * SAVE_MORE focus — or auto-invest-surplus being on — signals they'd rather bank it than
     * loosen a spending limit, PROVIDED safe spending already has comfortable headroom (if
     * money's already tight, defaulting to "show both" so the user sees the budget-relief option
     * too is still the safer default). An explicit ask ("tăng ngân sách X") always overrides this
     * regardless of profile — this only changes the UNSCOPED default.
     */
    private boolean windfallShouldPreferSavingsOnly(User user, UUID userId, int month, int year) {
        if (user == null) return false;
        Focus_Mode focus = user.getFocusMode();
        boolean profileFavorsSaving = focus == Focus_Mode.REDUCE_SPENDING
                || focus == Focus_Mode.SAVE_MORE
                || user.isAutoInvestSurplus();
        if (!profileFavorsSaving) return false;

        var snapshot = dataProvider.getFinancialSnapshot(userId, month, year);
        BigDecimal safeSpending = snapshot.safeSpending();
        if (safeSpending == null) return false;
        BigDecimal monthlyIncome = snapshot.monthlyIncome();
        BigDecimal threshold = (monthlyIncome != null && monthlyIncome.compareTo(BigDecimal.ZERO) > 0)
                ? monthlyIncome.multiply(AMPLE_SAFE_SPENDING_RATIO)
                : AMPLE_SAFE_SPENDING_FALLBACK;
        return safeSpending.compareTo(threshold) >= 0;
    }

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
    boolean attachImplicitEventSuggestions(ChatResponse.ChatResponseBuilder builder, User user,
                                           UUID userId, int month, int year, String messageLow,
                                           Set<String> groupLinkedIds) {
        BigDecimal amount = windfallAllocationService.parseAmount(messageLow);
        if (amount.compareTo(LARGE_AMOUNT_THRESHOLD) < 0) return false;

        boolean showBudget = wantsBudgetSuggestion(messageLow);
        boolean showProject = wantsProjectSuggestion(messageLow);
        boolean scoped = showBudget || showProject;

        if (INCOME_EVENT_PATTERN.matcher(messageLow).find()) {
            // Hypothetical/future event ("nếu lương tháng này tăng 3 triệu thì sao") — the LLM's
            // own (already advisory-tuned) reply stands as-is; treat this turn as "handled" so no
            // unrelated proactive nudge piles on top, but attach no real suggestion.
            if (isHypothetical(messageLow)) return true;
            // Real income, but explicitly earmarked for a later month — see FUTURE_INTENT_PATTERN.
            if (looksFutureDated(messageLow)) return true;
            if (!scoped && windfallShouldPreferSavingsOnly(user, userId, month, year)) {
                scoped = true;
                showProject = true;
            }
            var savings = (!scoped || showProject)
                    ? windfallAllocationService.suggestSavingsBoost(userId, month, year, messageLow)
                    : Collections.<ChatResponse.SavingsPlanSuggestion>emptyList();
            var budgets = (!scoped || showBudget)
                    ? windfallAllocationService.suggestBudgetBoosts(userId, month, year, messageLow)
                    : Collections.<ChatResponse.BudgetUpdateSuggestion>emptyList();
            // §5: strip windfall-savings entries targeting a group-linked (sub-personal) project
            // — same rule as the explicit INCOME_WINDFALL branch above, applied to the GENERAL_
            // ADVICE safety net so intent-misclassification doesn't sneak a group mutation past.
            savings = stripGroupLinkedSavings(savings, groupLinkedIds);
            String actionId = storePendingActionIfAny(userId, budgets, savings, messageLow);
            builder.budgetSuggestions(budgets.isEmpty() ? null : budgets)
                    .savingsSuggestions(savings.isEmpty() ? null : savings)
                    .pendingActionId(actionId)
                    .actionRequired(!budgets.isEmpty() || !savings.isEmpty());
            return true;
        } else if (EXPENSE_EVENT_PATTERN.matcher(messageLow).find()) {
            if (isHypothetical(messageLow)) return true;
            if (looksFutureDated(messageLow)) return true;
            // Pure feasibility assessment now — no suggestion card (see the LARGE_EXPENSE case's
            // doc comment in buildStructuredResponse). "Handled" just means the LLM's own reply
            // (already judged against safe_spending per ChatbotPromptBuilder) stands as-is, so no
            // unrelated proactive nudge piles on top of this turn.
            return true;
        }
        return false;
    }

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
                                            UUID userId, String messageLow,
                                            Set<String> groupLinkedIds) {
        var suggestions = computeProjectHypothesisSuggestions(userId, messageLow);
        // §5: strip shorten/extend/delete previews for group-linked projects before returning.
        // CREATE previews carry projectId=null (project doesn't exist yet), never removed.
        suggestions = stripGroupLinkedProjectChanges(suggestions, groupLinkedIds);
        if (suggestions.isEmpty()) return false;
        String actionId = storePendingAction(userId, pendingActionForSuggestions(userId, suggestions), messageLow);
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

    private PendingChatAction pendingActionForSuggestions(
            UUID userId, List<ChatResponse.ProjectChangeSuggestion> suggestions) {
        if (suggestions.size() == 1) return toPendingAction(userId, suggestions.get(0));
        return PendingChatAction.builder()
                .type(PendingChatAction.Type.BATCH)
                .batchActions(suggestions.stream().map(s -> toPendingAction(userId, s)).toList())
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
                subActions.add(toProjectSavingsAction(s));
            }
        }
        if (subActions.isEmpty()) return null;
        PendingChatAction action = subActions.size() == 1
                ? subActions.get(0)
                : PendingChatAction.builder().type(PendingChatAction.Type.BATCH).batchActions(subActions).build();
        return storePendingAction(userId, action, messageLow);
    }

    /**
     * A {@code SavingsPlanSuggestion} maps to one of two real mutations depending on what it
     * actually represents:
     * <ul>
     *   <li>A positive {@code suggestedAddAmount} is real money moving INTO the project (windfall
     *       boost, an explicit "tôi vừa góp thêm X" contribution, a month-end-leftover/surplus
     *       nudge) — this must go through {@code PROJECT_CONTRIBUTION_ADD} so confirming it
     *       creates a real {@code ProjectContribution} + ledger {@code Transaction}, the only
     *       thing the project's visible progress/completion actually reads. Routing this through
     *       {@code PROJECT_TRACKING_UPDATE} instead only touches the separate pace-tracking row —
     *       confirming it looks like a success but the project's displayed progress never moves.</li>
     *   <li>Everything else (a debt/{@code moneyOwed} change, a deadline-only deferral via {@code
     *       suggestedMonthLeft}, or a negative {@code suggestedAddAmount} withdrawing idle funds
     *       from an ABANDONED project) isn't a contribution — stays on {@code
     *       PROJECT_TRACKING_UPDATE} as before.</li>
     * </ul>
     */
    private PendingChatAction toProjectSavingsAction(ChatResponse.SavingsPlanSuggestion s) {
        boolean isContribution = s.getSuggestedAddAmount() != null && s.getSuggestedAddAmount() > 0;
        if (isContribution) {
            return PendingChatAction.builder()
                    .type(PendingChatAction.Type.PROJECT_CONTRIBUTION_ADD)
                    .projectId(s.getProjectId())
                    .contributionAmount(BigDecimal.valueOf(s.getSuggestedAddAmount()))
                    .build();
        }
        return PendingChatAction.builder()
                .type(PendingChatAction.Type.PROJECT_TRACKING_UPDATE)
                .projectId(s.getProjectId())
                .trackingMoneySaved(s.getNewMoneySaved() != null
                        ? BigDecimal.valueOf(s.getNewMoneySaved()) : null)
                .trackingMonthLeft(s.getSuggestedMonthLeft())
                .trackingMoneyOwed(s.getSuggestedMoneyOwed() != null
                        ? BigDecimal.valueOf(s.getSuggestedMoneyOwed()) : null)
                .build();
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

    /** Whether this turn's intent should never carry follow-up question chips at all — the
     *  reply is either terminal (OUT_OF_SCOPE) or a self-contained assessment (LARGE_EXPENSE)
     *  where any "want me to look at X too?" chip would feel like a nag. Kept as a code rule
     *  because the {@code relatedQuestions} field is separate from the closing-question logic
     *  the prompt controls: without this, an LLM turn that correctly returned {@code []} for
     *  the field per §7 would still get {@code DEFAULT_RELATED_QUESTIONS} re-injected by
     *  {@link #relatedQuestionsOrFallback}, contradicting the prompt. */
    static boolean intentSuppressesRelatedQuestions(ChatResponse.ChatIntent intent) {
        return intent == ChatResponse.ChatIntent.OUT_OF_SCOPE
                || intent == ChatResponse.ChatIntent.LARGE_EXPENSE;
    }

    /** OUT_OF_SCOPE and LARGE_EXPENSE get no related questions per the prompt's §7; other
     *  intents fall back to a generic set when the LLM omits the field (e.g. after a
     *  JsonSchemaGuardrail fallback). */
    private List<String> relatedQuestionsOrFallback(ChatResponse.ChatIntent intent, List<String> llmQuestions) {
        if (intentSuppressesRelatedQuestions(intent)) return null;
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
     * {@link #}.
     */
    private String storePendingAction(UUID userId, PendingChatAction action, String messageLow) {
        String actionId = UUID.randomUUID().toString();
        action.setEnglish(MessageLanguageDetector.isEnglish(messageLow));
        chatPendingActionPort.save(userId, actionId, action);
        chatPendingActionPort.saveLatestId(userId, actionId);
        return actionId;
    }

    /** Converts a project-hypothesis preview into the real mutation {@link ConfirmChatActionUseCase} will run. */
    private PendingChatAction toPendingAction(UUID userId, ChatResponse.ProjectChangeSuggestion s) {
        return switch (s.getOperation()) {
            // BUG: ConfirmChatActionUseCase used to default every chat-created project to MEDIUM
            // priority (nothing here ever set one) — CreateProjectUseCase then rejects it with
            // PROJECT_ACTIVE_PRIORITY_CONFLICT as soon as the user already has one ACTIVE MEDIUM
            // project, which confirm surfaces as a raw, confusing exception message. Pick an
            // actually-unused priority instead, same as a user filling the create form would.
            case CREATE -> PendingChatAction.builder()
                    .type(PendingChatAction.Type.PROJECT_CREATE)
                    .name(s.getProjectName())
                    .priority(projectHypothesisService.pickAvailablePriorityForNewProject(userId).name())
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

    // ── §5 defense-in-depth: strip suggestions targeting sub-personal (group-linked) projects ──
    //
    // All three suggestion lists (SavingsPlanSuggestion, ProjectChangeSuggestion, and the pending
    // PROJECT_* action types) reference a project by its String projectId. A sub-personal project
    // is a Project where isSubPersonal() is true (i.e. groupProjectId != null). Even though the
    // prompt tells the LLM never to propose those, we also filter them here because:
    //   1. The deterministic suggestion services (ProjectUpdateSuggestionService, ProjectHypothesis-
    //      Service, WindfallAllocationService) do NOT check isSubPersonal() themselves — their
    //      candidate pools come from projectRepository.findAllByUser, which returns both kinds.
    //   2. A user's stray "yes" reply must never silently mutate a shared group commitment.
    //
    // Kept read-only + cheap: one findAllByUser call per structured-response build, then a set
    // membership check per suggestion. If the repo call itself fails, we fail SAFE (empty
    // group-set), which means everything is treated as personal — same as the previous behaviour
    // that already had this hazard, so no regression relative to old code.
    Set<String> loadSubPersonalProjectIds(UUID userId) {
        try {
            return projectRepository.findAllByUser(UserId.of(userId.toString())).stream()
                    .filter(Project::isSubPersonal)
                    .map(p -> p.getId().value().toString())
                    .collect(java.util.stream.Collectors.toSet());
        } catch (Exception e) {
            log.warn("ChatWithAIUseCase: could not load projects for sub-personal filter, user {}: {}",
                    userId, e.getMessage());
            return Collections.emptySet();
        }
    }

    /** Filters SavingsPlanSuggestion entries whose projectId is a sub-personal (group-linked)
     *  project. Null-safe and returns the original list untouched when no filtering is needed
     *  so intent-branch conditionals like {@code list.isEmpty() ? null : list} still work. */
    List<ChatResponse.SavingsPlanSuggestion> stripGroupLinkedSavings(
            List<ChatResponse.SavingsPlanSuggestion> in, Set<String> groupLinkedIds) {
        if (in == null || in.isEmpty() || groupLinkedIds.isEmpty()) return in;
        List<ChatResponse.SavingsPlanSuggestion> out = new ArrayList<>(in.size());
        for (ChatResponse.SavingsPlanSuggestion s : in) {
            if (s != null && s.getProjectId() != null && groupLinkedIds.contains(s.getProjectId())) continue;
            out.add(s);
        }
        return out;
    }

    /** Same as {@link #stripGroupLinkedSavings} but for ProjectChangeSuggestion. */
    List<ChatResponse.ProjectChangeSuggestion> stripGroupLinkedProjectChanges(
            List<ChatResponse.ProjectChangeSuggestion> in, Set<String> groupLinkedIds) {
        if (in == null || in.isEmpty() || groupLinkedIds.isEmpty()) return in;
        List<ChatResponse.ProjectChangeSuggestion> out = new ArrayList<>(in.size());
        for (ChatResponse.ProjectChangeSuggestion s : in) {
            // CREATE has projectId == null (project doesn't exist yet) — never a group-linked case.
            if (s != null && s.getProjectId() != null && groupLinkedIds.contains(s.getProjectId())) continue;
            out.add(s);
        }
        return out;
    }

    /**
     * Cash-flow deficit gate — delegates to {@link UserFinancialDataService#classifyCashFlow}
     * so the proactive-suggestion safety net below shares ONE definition of "this month is in
     * deficit" with what the LLM was told in the financial-context block. The prompt's Rule A
     * tells the LLM to hold back saving/boost suggestions when the month is DEFICIT; proactive
     * suggestions come from Java (not the LLM), so they need their own explicit check here.
     *
     * <p>Fail-safe: any exception yields false (allow suggestions) — same conservative default
     * as the rest of this class's best-effort safety nets. A missing deficit gate is a
     * regression against the anti-hallucination goal but not a data-safety issue; a false-
     * positive suppression would silently strip a legitimate nudge.
     */
    boolean monthInDeficit(UUID userId, int month, int year) {
        try {
            return dataProvider.classifyCashFlow(userId, month, year)
                    == UserFinancialDataService.CashFlowStatus.DEFICIT;
        } catch (Exception e) {
            log.warn("ChatWithAIUseCase: deficit gate check failed for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
}