package com.example.smartmoney.application.usecases.chat;

import com.example.smartmoney.application.dtos.requests.chat.ChatMessageDto;
import com.example.smartmoney.application.dtos.requests.chat.ConfirmChatActionRequest;
import com.example.smartmoney.application.dtos.responses.ChatActionConfirmResponse;
import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.ports.chat.ChatPendingActionPort;
import com.example.smartmoney.application.services.ChatHistoryCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic (non-LLM) confirm/deny gate for a pending chat action — extracted out of
 * {@code ChatWithAIUseCase} so {@code InputGuardrailAspect} can run it BEFORE the content
 * guardrails (Scope/PromptInjection/SemanticScope), not after.
 *
 * <p>Why this has to run before the guardrails, not inside the guarded method: a bare "yes"/"no"
 * resolving a pending suggestion has no financial content for those guardrails to evaluate, and
 * {@code SemanticScopeGuardrail}'s LLM classifier — which sees only the raw message with no
 * conversation context — reliably misreads an isolated "có"/"yes" as off-topic. When this logic
 * lived inside {@code ChatWithAIUseCase.execute()} (a {@code @InputGuardrail}-annotated method,
 * intercepted by Spring AOP), the guardrail chain always ran to completion first and could throw
 * before this code ever got a chance to run — so typing "có" to confirm a suggestion could get
 * rejected as OUT_OF_SCOPE. Moving this into a standalone use case lets the aspect call it
 * up-front and short-circuit entirely on a match, skipping the guardrails for that turn.
 *
 * <p>Only fires on an exact whole-message match — never a substring — since Vietnamese "có"/
 * "không" are ordinary words (e.g. "tôi có thể xem báo cáo không?" contains both), not just
 * yes/no answers. Deliberately deterministic, not LLM-classified: an LLM's own read of "yes" is
 * not a safe enough gate for a financial mutation on its own; this keeps the trigger to a fixed,
 * auditable rule instead of free-form model judgment.
 */
@Service
@RequiredArgsConstructor
public class ResolvePlainAnswerUseCase {

    private final ChatPendingActionPort chatPendingActionPort;
    private final ConfirmChatActionUseCase confirmChatActionUseCase;
    private final ChatHistoryCacheService chatHistoryCacheService;

    private static final List<String> DEFAULT_RELATED_QUESTIONS = List.of(
            "Tôi có đang chi tiêu vượt ngân sách ở danh mục nào không?",
            "Tiến độ các dự án tiết kiệm của tôi đang như thế nào?",
            "Tôi nên điều chỉnh ngân sách tháng này ra sao?");

    private enum PlainAnswer { AGREE, DENY, NONE }

    // Whole-message match only — Vietnamese "có"/"không" are far too common as ordinary sentence
    // words to ever match as a substring. Includes both with- and without-diacritics spellings,
    // same convention as MessageLanguageDetector.VIETNAMESE_NO_DIACRITICS_HINTS.
    private static final Set<String> PLAIN_AGREE_TOKENS = Set.of(
            "co", "có", "dong y", "đồng ý", "dong y luon", "đồng ý luôn",
            "ok", "oke", "okay", "yes", "yeah", "yep", "sure", "yes please",
            "vang", "vâng", "u", "ừ", "duoc", "được", "duoc roi", "được rồi",
            "chuan", "chuẩn", "chuan roi", "chuẩn rồi", "xac nhan", "xác nhận", "confirm",
            "lam di", "làm đi", "dung vay", "đúng vậy", "ap dung", "áp dụng", "ap dung di", "áp dụng đi");

    private static final Set<String> PLAIN_DENY_TOKENS = Set.of(
            "khong", "không", "khong dong y", "không đồng ý", "no", "nope",
            "thoi", "thôi", "thoi khoi", "thôi khỏi", "huy", "hủy", "huy di", "hủy đi",
            "bo qua", "bỏ qua", "khong can", "không cần", "khong muon", "không muốn",
            "deny", "cancel", "dismiss", "de sau", "để sau", "tu choi", "từ chối");

    /**
     * @return the resolved {@code ChatResponse} if {@code message} is a bare yes/no answering the
     * user's latest pending action, or empty (caller should proceed with normal processing)
     * when there's no pending action or the message isn't a plain yes/no.
     */
    public Optional<ChatResponse> tryResolve(UUID userId, String message) {
        if (message == null) return Optional.empty();
        PlainAnswer answer = classify(message);
        if (answer == PlainAnswer.NONE) return Optional.empty();

        Optional<String> actionId = chatPendingActionPort.loadLatestId(userId);
        if (actionId.isEmpty()) return Optional.empty();

        // ConfirmChatActionUseCase itself clears the "latest" pointer when it consumes this
        // actionId (handles both this path and a direct call to the /chat/confirm endpoint).
        ChatActionConfirmResponse result = confirmChatActionUseCase.execute(userId,
                ConfirmChatActionRequest.builder()
                        .actionId(actionId.get())
                        .confirm(answer == PlainAnswer.AGREE)
                        .build());

        List<ChatMessageDto> history = chatHistoryCacheService.load(userId);
        chatHistoryCacheService.appendAndCompress(userId, history, message, result.getReply());

        return Optional.of(ChatResponse.builder()
                .reply(result.getReply())
                .intent(ChatResponse.ChatIntent.GENERAL_ADVICE)
                .relatedQuestions(DEFAULT_RELATED_QUESTIONS)
                // Propagated from the pending action's own stored language — this "yes"/"no"
                // message itself carries no reliable language signal (short ambiguous tokens).
                .english(result.isEnglish())
                .build());
    }

    private PlainAnswer classify(String rawMessage) {
        String normalized = normalize(rawMessage);
        if (PLAIN_AGREE_TOKENS.contains(normalized)) return PlainAnswer.AGREE;
        if (PLAIN_DENY_TOKENS.contains(normalized)) return PlainAnswer.DENY;
        return PlainAnswer.NONE;
    }

    private static String normalize(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("^[\"'“”‘’(\\[]+", "")
                .replaceAll("[.,!?~;:\"'“”‘’)\\]]+$", "")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
