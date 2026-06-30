package com.example.smartmoney.infrastructure.integration.llm;

import com.example.smartmoney.application.ports.chat.AIChatPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Adapter that calls the OpenRouter API (DeepSeek model) on behalf of the chat use cases.
 *
 * Change summary (UC1 / UC2 / UC3 update):
 * The old {@code chat(message, userId)} and {@code chatStream(message, userId, ...)} methods
 * hard-coded the system prompt inside this adapter, which prevented UC3 personalisation.
 * They have been replaced by versions that accept a {@code systemPrompt} parameter supplied
 * by {@link com.example.smartmoney.application.services.ChatbotPromptBuilder}.
 *
 * Old implementations are preserved as comments below each new method.
 */
@Component
@RequiredArgsConstructor
public class OpenRouterAIAdapter implements AIChatPort {

    private final ObjectMapper objectMapper;
    private final OpenRouterProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

    private static final String OPENROUTER_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL = "deepseek/deepseek-v4-flash";

    // ── Synchronous chat (UC1 / UC2 / UC3) ─────────────────────────────────

    /**
     * Synchronous chat with a caller-supplied system prompt.
     * The {@code systemPrompt} is built by {@code ChatbotPromptBuilder} and carries
     * the user's onboarding preferences (pace, intervention level, focus mode).
     */
    @Override
    public String chat(String systemPrompt, String userMessage, String userId) {
        // Old implementation (hard-coded system prompt, single message parameter):
        // -------------------------------------------------------------------------
        // @Override
        // public String chat(String message, String userId) {
        //     Map<String, Object> body = Map.of(
        //             "model", "deepseek/deepseek-v4-flash",
        //             "temperature", 0.3,
        //             "max_tokens", 800,
        //             "messages", List.of(
        //                     Map.of("role", "system", "content", """
        //                             Bạn là SmartMoney AI.
        //                             Nhiệm vụ:
        //                             - Tối đa 800 chữ.
        //                             - Trả lời chính xác câu hỏi của người dùng.
        //                             ...
        //                             """),
        //                     Map.of("role", "user", "content", message)));
        //     ...
        // }
        // -------------------------------------------------------------------------

        try {
            // UC3: systemPrompt comes from ChatbotPromptBuilder.build(user) and carries
            // the personalised tone instructions (pace, intervention, focus mode).
            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "temperature", 0.3,
                    "max_tokens", 800,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userMessage)));

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENROUTER_URL))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .header("HTTP-Referer", "https://smartmoney.ai")
                    .header("X-Title", "SmartMoney AI")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("AI API error: " + response.body());
            }

            Map<String, Object> res = objectMapper.readValue(response.body(), Map.class);
            List choices = (List) res.get("choices");
            Map choice = (Map) choices.get(0);
            Map msg = (Map) choice.get("message");
            return msg.get("content").toString();

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call AI service: " + e.getMessage(), e);
        }
    }

    // ── Suggestion chips (unchanged) ────────────────────────────────────────

    @Override
    public List<String> generateSuggestions(String userData, String userId) {
        try {
            String systemPrompt = """
                    Bạn là trợ lý tài chính SmartMoney.

                    Dựa trên dữ liệu tài chính được cung cấp, hãy tạo 3 câu hỏi tự phản tư giúp người dùng suy nghĩ về thói quen chi tiêu, tiết kiệm và mục tiêu tài chính.

                    Yêu cầu:
                    - Viết ở ngôi thứ nhất, dùng "tôi", không dùng "bạn".
                    - Mỗi câu hỏi ngắn gọn, tối đa 100 ký tự.
                    - Cá nhân hóa dựa trên dữ liệu thực tế.
                    - Chỉ hỏi một vấn đề trong mỗi câu.
                    - Trả về duy nhất JSON dạng:

                    [
                    "câu hỏi 1",
                    "câu hỏi 2",
                    "câu hỏi 3"
                    ]

                    Không giải thích, không markdown, không thêm văn bản khác.
                    """;

            Map<String, Object> body = Map.of(
                    "model", MODEL,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userData)));

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENROUTER_URL))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .header("HTTP-Referer", "https://smartmoney.ai")
                    .header("X-Title", "SmartMoney AI")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("AI API error: " + response.body());
            }

            Map<String, Object> res = objectMapper.readValue(response.body(), Map.class);
            List choices = (List) res.get("choices");
            Map choice = (Map) choices.get(0);
            Map msg = (Map) choice.get("message");
            String content = msg.get("content").toString().trim();

            List<String> questions = objectMapper.readValue(content, new TypeReference<List<String>>() {});
            while (questions.size() < 3) {
                questions.add("Bạn có muốn kiểm tra lại ngân sách tháng này không?");
            }
            return questions.subList(0, 3);

        } catch (Exception e) {
            return List.of(
                    "Tôi có thể cắt giảm chi tiêu ở danh mục nào tháng này?",
                    "Tiến độ các dự án tiết kiệm của tôi đang như thế nào?",
                    "Tôi có đang chi tiêu vượt ngân sách không?");
        }
    }

    // ── Streaming chat (UC1 / UC2 / UC3) ────────────────────────────────────

    /**
     * Streaming chat with a caller-supplied system prompt (SSE token-by-token).
     * Each call to {@code onNext} delivers the CUMULATIVE reply text so the
     * frontend can render a live-updating bubble without tracking deltas.
     */
    @Override
    public void chatStream(String systemPrompt, String userMessage, String userId,
            Consumer<String> onNext,
            Consumer<Throwable> onError,
            Runnable onComplete) {

        // Old implementation (hard-coded system prompt):
        // -------------------------------------------------------------------------
        // @Override
        // public void chatStream(String message, String userId,
        //         Consumer<String> onNext, Consumer<Throwable> onError, Runnable onComplete) {
        //     Map<String, Object> body = new HashMap<>();
        //     body.put("model", "deepseek/deepseek-v4-flash");
        //     body.put("temperature", 0.3);
        //     body.put("max_tokens", 800);
        //     body.put("stream", true);
        //     body.put("messages", List.of(
        //             Map.of("role", "system", "content",
        //                     "Bạn là SmartMoney AI.\nNhiệm vụ:\n- Tối đa 800 chữ.\n..."),
        //             Map.of("role", "user", "content", message)));
        //     ...
        // }
        // -------------------------------------------------------------------------

        try {
            // UC3: systemPrompt carries personalised tone from ChatbotPromptBuilder
            Map<String, Object> body = new HashMap<>();
            body.put("model", MODEL);
            body.put("temperature", 0.3);
            body.put("max_tokens", 800);
            body.put("stream", true);
            body.put("messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userMessage)));

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(OPENROUTER_URL))
                    .timeout(Duration.ofMinutes(2))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .header("HTTP-Referer", "https://smartmoney.ai")
                    .header("X-Title", "SmartMoney AI")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // Send asynchronously; parse the SSE stream line-by-line
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            onError.accept(new RuntimeException("AI API error: " + response.statusCode()));
                            return;
                        }
                        // Accumulate the full reply so onNext always delivers the growing text,
                        // not just the delta — this lets the frontend do a simple text replace
                        StringBuilder fullReply = new StringBuilder();
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.isBlank() || !line.startsWith("data: ")) continue;
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) {
                                    onComplete.run();
                                    break;
                                }
                                try {
                                    JsonNode json = objectMapper.readTree(data);
                                    JsonNode delta = json.path("choices").get(0).path("delta");
                                    String content = delta.path("content").asText(null);
                                    if (content != null && !content.isEmpty()) {
                                        fullReply.append(content);
                                        onNext.accept(fullReply.toString());
                                    }
                                } catch (Exception ignored) {
                                    // Skip malformed SSE frames
                                }
                            }
                        } catch (Exception e) {
                            onError.accept(e);
                        }
                    })
                    .exceptionally(e -> {
                        onError.accept(e);
                        return null;
                    });

        } catch (Exception e) {
            onError.accept(e);
        }
    }
}
