package com.example.smartmoney.application.services;

import com.example.smartmoney.application.dtos.requests.chat.ChatMessageDto;
import com.example.smartmoney.application.ports.chat.AIChatPort;
import com.example.smartmoney.application.ports.chat.ChatHistoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Wraps {@link ChatHistoryPort} with rolling summarisation so Redis history never grows
 * unbounded. The cache always holds at most one rolling {@code SYSTEM} summary entry followed
 * by the most recent raw USER/ASSISTANT turns — the summary is folded forward (merged with new
 * content) rather than replaced, so older context isn't lost, only compressed.
 *
 * The summary is purely a prompt-injection aid — {@link #load} returns it to the caller only so
 * it can be placed at the top of the prompt; it is never surfaced in any API response.
 *
 * Compression is model-agnostic: it applies the same way regardless of whether the conversation
 * turns being folded were answered by Claude Haiku or Gemini Flash. The summarisation call itself
 * is routed through {@link LlmRouter} using the same heuristic as the main chat turn, based on the
 * content being folded — so a run of budget/project-heavy turns gets summarised by Claude Haiku
 * just like the original replies were, not unconditionally by Gemini Flash.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryCacheService {

    // Trigger + fold-size are read literally from the raw message count (this is a List of
    // ChatMessageDto — 1 "turn" advertised elsewhere in this codebase == 1 user+assistant pair
    // == 2 messages). Folding the oldest 4 raw messages (2 pairs) each time a compression fires
    // keeps steady-state history oscillating around 3-5 pairs (6-10 messages) plus one summary.
    private static final int COMPRESS_TRIGGER_SIZE = 8;
    private static final int FOLD_COUNT = 4;

    private final ChatHistoryPort chatHistoryPort;
    private final LlmRouter llmRouter;

    private static final String SUMMARY_PROMPT = """
            Bạn tóm tắt lịch sử hội thoại tài chính giữa người dùng và trợ lý SmartMoney AI thành
            ĐÚNG 2-3 câu ngắn gọn. Giữ lại: số tiền cụ thể, quyết định người dùng đã đồng ý, các dự
            án tiết kiệm đang hoạt động và tình trạng của chúng, mục tiêu tài chính hiện tại. Bỏ qua
            hoàn toàn: lời chào xã giao, câu hỏi lặp lại, nội dung không liên quan đến tài chính.
            Chỉ trả về đoạn tóm tắt, không thêm tiêu đề hay giải thích.
            """;

    /** Loads whatever is cached: an optional rolling summary followed by recent raw turns. */
    public List<ChatMessageDto> load(UUID userId) {
        return chatHistoryPort.load(userId);
    }

    /**
     * Appends the newest USER+ASSISTANT pair to {@code history} and persists it, folding the
     * oldest raw turns into the rolling summary first if the raw turn count has grown past the
     * compression trigger.
     */
    public void appendAndCompress(UUID userId, List<ChatMessageDto> history,
                                  String userMessage, String assistantReply) {
        ChatMessageDto existingSummary = history.isEmpty() || history.get(0).getRole() != ChatMessageDto.Role.SYSTEM
                ? null : history.get(0);

        List<ChatMessageDto> raw = new ArrayList<>(existingSummary == null ? history : history.subList(1, history.size()));
        raw.add(ChatMessageDto.user(userMessage));
        raw.add(ChatMessageDto.assistant(assistantReply));

        List<ChatMessageDto> updated = new ArrayList<>();
        if (raw.size() > COMPRESS_TRIGGER_SIZE) {
            List<ChatMessageDto> toFold = raw.subList(0, FOLD_COUNT);
            List<ChatMessageDto> remaining = raw.subList(FOLD_COUNT, raw.size());

            String newSummary = compress(existingSummary, toFold);
            updated.add(ChatMessageDto.summary("[SUMMARY] " + newSummary));
            updated.addAll(remaining);
        } else {
            if (existingSummary != null) updated.add(existingSummary);
            updated.addAll(raw);
        }

        chatHistoryPort.save(userId, updated);
    }

    private String compress(ChatMessageDto existingSummary, List<ChatMessageDto> toFold) {
        StringBuilder sb = new StringBuilder();
        if (existingSummary != null) {
            sb.append("Tóm tắt trước đó: ").append(existingSummary.getContent()).append("\n\n");
        }
        sb.append("Đoạn hội thoại cần tóm tắt thêm:\n");
        String foldedText = toFold.stream().map(ChatMessageDto::toPromptLine).collect(Collectors.joining("\n"));
        sb.append(foldedText);

        try {
            // Route the same way the original turns were routed — a fold full of
            // budget/project/simulation content gets summarised by Claude Haiku, not
            // unconditionally by Gemini Flash.
            AIChatPort adapter = llmRouter.forMessage(foldedText.toLowerCase());
            String result = adapter.chat(SUMMARY_PROMPT, sb.toString(), "history-compression");
            return result == null ? "" : result.trim();
        } catch (Exception e) {
            // Fail safe: if summarisation fails, keep the previous summary text (if any) rather
            // than losing the folded turns' context entirely.
            log.warn("ChatHistoryCacheService: summarisation call failed, keeping prior summary: {}",
                    e.getMessage());
            return existingSummary != null ? existingSummary.getContent().replaceFirst("^\\[SUMMARY\\] ", "") : "";
        }
    }
}
