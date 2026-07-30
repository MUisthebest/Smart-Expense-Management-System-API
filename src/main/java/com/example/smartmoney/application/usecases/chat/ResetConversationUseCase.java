package com.example.smartmoney.application.usecases.chat;

import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.example.smartmoney.application.ports.chat.ChatPendingActionPort;
import com.example.smartmoney.application.services.ChatHistoryCacheService;
import com.example.smartmoney.application.services.MessageLanguageDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Deterministic (non-LLM) gate for "hãy xóa đoạn hội thoại" — wiping the user's Redis chat window
 * so the next turn starts from an empty context.
 *
 * <p>Runs from {@code InputGuardrailAspect} BEFORE the content guardrails, for the same reason
 * {@link ResolvePlainAnswerUseCase} does: "xóa đoạn hội thoại" carries no financial content, so
 * {@code SemanticScopeGuardrail}'s LLM classifier — which sees only the raw message — would reject
 * it as OUT_OF_SCOPE before the request ever reached a handler. It also must not reach the LLM at
 * all: the model has no tool for this, so left to itself it would either claim it deleted the
 * history (it can't) or refuse. A fixed, auditable rule is the whole point.
 *
 * <p>Clearing the window also clears the "latest pending action" pointer. A fresh context that
 * still had a dangling pointer would let the next bare "có" confirm a mutation the user can no
 * longer see in their history — exactly the surprise a conversation reset is supposed to remove.
 *
 * <p>Distinct from {@code TRANSACTION_DELETE} / {@code PROJECT_DELETE}: this deletes CHAT MESSAGES,
 * never financial data. The patterns below therefore require an explicit conversation/history noun
 * ("hội thoại", "tin nhắn", "chat", "lịch sử trò chuyện") — "xóa giao dịch" and "xóa dự án" must
 * never match here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResetConversationUseCase {

    private final ChatHistoryCacheService chatHistoryCacheService;
    private final ChatPendingActionPort chatPendingActionPort;

    /**
     * A delete/clear/reset verb followed (in either order) by a conversation noun. Substring match
     * is safe here — unlike the bare "có"/"không" tokens in {@link ResolvePlainAnswerUseCase},
     * every alternative below already contains both the verb and an unambiguous chat noun, so it
     * can't collide with ordinary financial phrasing. Both diacritic and no-diacritic spellings are
     * accepted, matching {@code MessageLanguageDetector}'s convention.
     */
    private static final Pattern RESET_PATTERN = Pattern.compile(
            "(xoa|xóa|xoá|clear|reset|delete|làm mới|lam moi|bắt đầu lại|bat dau lai|khởi động lại|khoi dong lai)"
                    + "\\s+(\\S+\\s+){0,3}?"
                    + "(đoạn hội thoại|doan hoi thoai|hội thoại|hoi thoai|cuộc trò chuyện|cuoc tro chuyen|"
                    + "đoạn chat|doan chat|lịch sử trò chuyện|lich su tro chuyen|lịch sử chat|lich su chat|"
                    + "tin nhắn|tin nhan|conversation|chat history|message history|chat)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Reverse word order — "hội thoại này xóa đi giúp tôi", "conversation, clear it". */
    private static final Pattern RESET_PATTERN_REVERSED = Pattern.compile(
            "(đoạn hội thoại|doan hoi thoai|hội thoại|hoi thoai|cuộc trò chuyện|cuoc tro chuyen|"
                    + "lịch sử trò chuyện|lich su tro chuyen|conversation|chat history)"
                    + "\\s+(\\S+\\s+){0,3}?"
                    + "(xoa|xóa|xoá|clear|reset|delete)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * @return a terminal {@code ChatResponse} when {@code message} is a conversation-reset request
     * (the window has been cleared by the time this returns), or empty so the caller proceeds with
     * normal processing.
     */
    public Optional<ChatResponse> tryReset(UUID userId, String message) {
        if (message == null || message.isBlank()) return Optional.empty();
        // Punctuation becomes whitespace before matching: the patterns below join verb and noun
        // with \s+, so "conversation, please clear it" would otherwise miss on the comma. Only
        // sentence punctuation is touched — Vietnamese diacritics must survive intact.
        String normalized = message.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[,.;:!?\"'()\\[\\]“”‘’]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (!RESET_PATTERN.matcher(normalized).find()
                && !RESET_PATTERN_REVERSED.matcher(normalized).find()) {
            return Optional.empty();
        }

        boolean english = MessageLanguageDetector.isEnglish(normalized);

        chatHistoryCacheService.clear(userId);
        // A reset must not leave a confirmable mutation pointing at a conversation the user can no
        // longer see. Best-effort: a Redis hiccup here shouldn't fail the reset itself.
        try {
            chatPendingActionPort.clearLatestId(userId);
        } catch (Exception e) {
            log.warn("ResetConversationUseCase: could not clear pending-action pointer for user {}: {}",
                    userId, e.getMessage());
        }
        log.info("ResetConversationUseCase: cleared chat window for user {}", userId);

        // No relatedQuestions: the point of a reset is a blank slate, and follow-up chips would
        // immediately re-seed the context the user just asked to drop.
        return Optional.of(ChatResponse.builder()
                .intent(ChatResponse.ChatIntent.GENERAL_ADVICE)
                .reply(english
                        ? "Done — I've cleared our conversation. We're starting fresh, so I no longer "
                          + "remember what we discussed earlier. Your financial data is untouched."
                        : "Đã xóa đoạn hội thoại của chúng ta. Chúng ta bắt đầu lại từ đầu nên tôi không "
                          + "còn nhớ những gì đã trao đổi trước đó. Dữ liệu tài chính của bạn vẫn nguyên vẹn.")
                .relatedQuestions(List.of())
                .english(english)
                .build());
    }
}
