package com.example.smartmoney.infrastructure.integration.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlockParam;
import com.example.smartmoney.application.ports.chat.AIChatPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Sole {@link AIChatPort} implementation — direct Anthropic Messages API via the official
 * {@code anthropic-java} SDK, replacing the two-tier Gemini setup (see {@code LlmRouter}, which
 * now always routes here). The system prompt is marked with an ephemeral prompt-cache breakpoint
 * on every call: it's mostly static per user (base rules + tone block from
 * {@code ChatbotPromptBuilder}), so repeat turns within the cache TTL reuse it instead of
 * reprocessing — cheaper and faster. Below Haiku 4.5's ~4096-token minimum cacheable prefix this
 * silently doesn't cache (no error) — {@code cacheReadInputTokens}/{@code cacheCreationInputTokens}
 * are logged at debug level so that's verifiable rather than assumed.
 */
@Slf4j
@Component("claudeHaikuAdapter")
public class AnthropicClaudeAdapter implements AIChatPort {

    // Vietnamese BPE tokenization needs ~1.5-2x tokens/word vs English, so headroom is kept high
    // even though replies are capped at ~250 words by the system prompt.
    private static final int MAX_OUTPUT_TOKENS = 2048;
    private static final double TEMPERATURE = 0.3;

    // The SDK default is a 10-minute request timeout with 2 retries — worst case ~30 min, far past
    // the frontend's own ~60s HTTP timeout, so a slow/overloaded Claude response reads to the user
    // as a generic "connection error" instead of a clear AI-side failure. Capping both here keeps
    // the worst case (REQUEST_TIMEOUT * (1 + MAX_RETRIES)) comfortably under the frontend's budget,
    // leaving headroom for guardrails/DB work on our own side.
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_RETRIES = 1;

    private static final String SUGGESTIONS_SYSTEM_PROMPT = """
            Bạn là trợ lý tài chính SmartMoney. Dựa trên dữ liệu tài chính, tạo 3-5 câu hỏi tự phản tư.
            Yêu cầu: viết ngôi thứ nhất ("tôi"), tối đa 100 ký tự mỗi câu, cá nhân hoá theo dữ liệu.
            Trả về DUY NHẤT JSON array (không markdown):
            ["câu hỏi 1","câu hỏi 2","câu hỏi 3"]
            """;

    private final AnthropicClient client;
    private final AnthropicProperties properties;
    private final ObjectMapper objectMapper;

    public AnthropicClaudeAdapter(AnthropicProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = AnthropicOkHttpClient.builder()
                .apiKey(properties.getApiKey())
                .timeout(REQUEST_TIMEOUT)
                .maxRetries(MAX_RETRIES)
                .build();
    }

    /**
     * A blank {@code CLAUDE_API_KEY} doesn't fail startup (matches the existing
     * {@code GeminiProperties} pattern of defaulting to an empty string) — but every chat call
     * would then fail with a 401 from Anthropic, surfacing to users as a generic "connection
     * error" with nothing in the logs pointing at the real cause. Log it loudly and immediately
     * instead of waiting for the first failed request to (eventually) reveal it.
     */
    @PostConstruct
    void warnIfApiKeyMissing() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.error("CLAUDE_API_KEY is not set — every chat request will fail with a 401 " +
                    "authentication_error until it's configured.");
        }
    }

    // ── Synchronous chat ─────────────────────────────────────────────────────

    @Override
    public String chat(String systemPrompt, String userMessage, String userId) {
        try {
            Message response = client.messages().create(buildParams(systemPrompt, userMessage));
            logCacheUsage(response);
            return extractText(response);
        } catch (RuntimeException e) {
            log.warn("Claude chat call failed for user {}: {}", userId, e.toString());
            throw new RuntimeException("Failed to call AI service: " + e.getMessage(), e);
        }
    }

    // ── Streaming chat ───────────────────────────────────────────────────────

    @Override
    public void chatStream(String systemPrompt, String userMessage, String userId,
                            Consumer<String> onNext, Consumer<Throwable> onError, Runnable onComplete) {
        CompletableFuture.runAsync(() -> {
            StringBuilder cumulative = new StringBuilder();
            try (StreamResponse<RawMessageStreamEvent> stream =
                         client.messages().createStreaming(buildParams(systemPrompt, userMessage))) {
                stream.stream().forEach(event -> event.contentBlockDelta().ifPresent(delta ->
                        delta.delta().text().ifPresent(textDelta -> {
                            cumulative.append(textDelta.text());
                            onNext.accept(cumulative.toString());
                        })));
                onComplete.run();
            } catch (Exception e) {
                onError.accept(e);
            }
        });
    }

    // ── Suggestion chips ─────────────────────────────────────────────────────

    @Override
    public List<String> generateSuggestions(String userData, String userId) {
        try {
            Message response = client.messages().create(MessageCreateParams.builder()
                    .model(properties.getModel())
                    .maxTokens(MAX_OUTPUT_TOKENS)
                    .temperature(TEMPERATURE)
                    .system(SUGGESTIONS_SYSTEM_PROMPT)
                    .addUserMessage(userData)
                    .build());

            String raw = extractText(response).trim();
            if (raw.startsWith("```")) {
                int nl = raw.indexOf('\n');
                int last = raw.lastIndexOf("```");
                if (nl > 0 && last > nl) raw = raw.substring(nl + 1, last).trim();
            }
            List<String> questions = objectMapper.readValue(raw, new TypeReference<List<String>>() {});
            return questions.subList(0, Math.min(5, questions.size()));

        } catch (Exception e) {
            return List.of(
                    "Tôi có thể cắt giảm chi tiêu ở danh mục nào tháng này?",
                    "Tiến độ các dự án tiết kiệm của tôi đang như thế nào?",
                    "Tôi có đang chi tiêu vượt ngân sách không?");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private MessageCreateParams buildParams(String systemPrompt, String userMessage) {
        return MessageCreateParams.builder()
                .model(properties.getModel())
                .maxTokens(MAX_OUTPUT_TOKENS)
                .temperature(TEMPERATURE)
                .systemOfTextBlockParams(List.of(
                        TextBlockParam.builder()
                                .text(systemPrompt)
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build()))
                .addUserMessage(userMessage)
                .build();
    }

    private String extractText(Message response) {
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(block -> block.text())
                .collect(Collectors.joining());
    }

    private void logCacheUsage(Message response) {
        try {
            var usage = response.usage();
            log.debug("Claude usage: cacheRead={} cacheCreate={} input={} output={}",
                    usage.cacheReadInputTokens(), usage.cacheCreationInputTokens(),
                    usage.inputTokens(), usage.outputTokens());
        } catch (Exception e) {
            // Diagnostics only — never fail the request over a logging issue.
            log.debug("Claude usage logging failed: {}", e.getMessage());
        }
    }
}
