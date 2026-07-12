package com.example.smartmoney.application.services;

import com.example.smartmoney.application.ports.chat.AIChatPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Picks which Gemini-backed model handles a chat turn, BEFORE the model itself classifies
 * intent — this is a coarse heuristic pre-classification on the raw message text, not the final
 * {@code ChatResponse.ChatIntent} the LLM returns in its JSON payload.
 *
 * Routing:
 *  - Gemini (transactional): budget adjustment (UC1), what-if simulation (UC2), and any project
 *    adjustment/creation wording (PROJECT_UPDATE).
 *  - Gemini Flash: everything else (UC3 general advice, greetings, out-of-scope, etc.).
 *
 * {@code /suggestions} and the semantic scope classifier bypass this router entirely and always
 * use Gemini Flash directly (cheap, non-transactional calls). History summarisation does NOT
 * bypass it — {@code ChatHistoryCacheService} routes its summarisation call through this same
 * router so budget/project-heavy folded turns are summarised by the transactional tier too.
 */
@Service
public class LlmRouter {

    private final AIChatPort geminiTransactionalAdapter;
    private final AIChatPort geminiFlashAdapter;

    public LlmRouter(
            @Qualifier("geminiTransactionalAdapter") AIChatPort geminiTransactionalAdapter,
            @Qualifier("geminiFlashAdapter") AIChatPort geminiFlashAdapter) {
        this.geminiTransactionalAdapter = geminiTransactionalAdapter;
        this.geminiFlashAdapter = geminiFlashAdapter;
    }

    // BUDGET_UPDATE cues + SIMULATION ("what if" spend) cues + PROJECT_UPDATE
    // (adjust/create a savings project) cues.
    private static final Pattern HAIKU_TRIGGER = Pattern.compile(
            "ngân sách|budget|cắt giảm|giảm chi|hạn mức|chi tiêu|" +
            "nếu tôi|what if|giả sử|nếu chi|nếu tiêu|" +
            "dự án|project|góp thêm|góp vào|tiết kiệm|mục tiêu|nợ|vay|" +
            "tạo dự án|mở dự án|lập dự án|start.*project|new project",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /**
     * @param messageLow the user's message, already lower-cased
     */
    public AIChatPort forMessage(String messageLow) {
        if (messageLow != null && HAIKU_TRIGGER.matcher(messageLow).find()) {
            return geminiTransactionalAdapter;
        }
        return geminiFlashAdapter;
    }
}
