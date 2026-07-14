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
 * Maintains a fixed 5-slot window: 1 rolling summary + the 4 most recent raw messages (2
 * user/assistant pairs). The summarisation call itself is routed through {@link LlmRouter}, same
 * as the main chat turn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryCacheService {

    // Fixed 5-slot window: 1 rolling summary + 4 raw messages (2 user/assistant pairs). Folding
    // the oldest pair (2 messages) every time a new pair would push the raw count past 4 keeps
    // the stored history at exactly 5 entries after every turn — trigger and fold-size were
    // chosen together so raw count returns to 4 immediately after each fold (add 2, fold 2).
    private static final int COMPRESS_TRIGGER_SIZE = 4;
    private static final int FOLD_COUNT = 2;

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
