package com.example.smartmoney.infrastructure.integration.llm;

import com.example.smartmoney.application.ports.chat.AIChatPort;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AIChatPort implementation backed by Google Gemini.
 * Replaces OpenRouterAIAdapter for all chat and suggestion calls.
 *
 * Sync:   POST .../gemini-2.5-flash-lite:generateContent
 * Stream: POST .../gemini-2.5-flash-lite:streamGenerateContent?alt=sse
 */
@Slf4j
@Component
public class GeminiAIChatAdapter implements AIChatPort {

    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite";
    private static final int MAX_OUTPUT_TOKENS = 800;
    private static final double TEMPERATURE = 0.3;

    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final HttpClient httpClient;

    public GeminiAIChatAdapter(
            ObjectMapper objectMapper,
            @Value("${app.gemini.api-key}") String apiKey) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ── Synchronous chat ─────────────────────────────────────────────────────

    @Override
    public String chat(String systemPrompt, String userMessage, String userId) {
        try {
            Map<String, Object> body = buildBody(systemPrompt, userMessage, false);
            String rawBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + ":generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(2))
                    .POST(HttpRequest.BodyPublishers.ofString(rawBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Gemini API error [" + response.statusCode() + "]: " + response.body());
            }

            return extractText(response.body());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Gemini chat failed: " + e.getMessage(), e);
        }
    }

    // ── Streaming chat ───────────────────────────────────────────────────────

    @Override
    public void chatStream(String systemPrompt, String userMessage, String userId,
                           Consumer<String> onNext, Consumer<Throwable> onError, Runnable onComplete) {
        try {
            Map<String, Object> body = buildBody(systemPrompt, userMessage, false);
            String rawBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + ":streamGenerateContent?key=" + apiKey + "&alt=sse"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMinutes(2))
                    .POST(HttpRequest.BodyPublishers.ofString(rawBody))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            onError.accept(new RuntimeException(
                                    "Gemini stream error: " + response.statusCode()));
                            return;
                        }
                        StringBuilder cumulative = new StringBuilder();
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.isBlank() || !line.startsWith("data: ")) continue;
                                String data = line.substring(6).trim();
                                if ("[DONE]".equals(data)) break;
                                try {
                                    JsonNode chunk = objectMapper.readTree(data);
                                    String token = chunk.path("candidates")
                                            .path(0).path("content")
                                            .path("parts").path(0).path("text").asText(null);
                                    if (token != null && !token.isEmpty()) {
                                        cumulative.append(token);
                                        onNext.accept(cumulative.toString());
                                    }
                                } catch (Exception ignored) {
                                    // skip malformed SSE frames
                                }
                            }
                            onComplete.run();
                        } catch (Exception e) {
                            onError.accept(e);
                        }
                    })
                    .exceptionally(e -> { onError.accept(e); return null; });

        } catch (Exception e) {
            onError.accept(e);
        }
    }

    // ── Suggestion chips ─────────────────────────────────────────────────────

    @Override
    public List<String> generateSuggestions(String userData, String userId) {
        String systemInstruction = """
                Bạn là trợ lý tài chính SmartMoney. Dựa trên dữ liệu tài chính, tạo 3-5 câu hỏi tự phản tư.
                Yêu cầu: viết ngôi thứ nhất ("tôi"), tối đa 100 ký tự mỗi câu, cá nhân hoá theo dữ liệu.
                Trả về DUY NHẤT JSON array (không markdown):
                ["câu hỏi 1","câu hỏi 2","câu hỏi 3"]
                """;
        try {
            Map<String, Object> body = buildBody(systemInstruction, userData, false);
            String rawBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + ":generateContent?key=" + apiKey))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(rawBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new RuntimeException("Gemini error");

            String raw = extractText(response.body()).trim();
            if (raw.startsWith("```")) {
                int nl = raw.indexOf('\n');
                int last = raw.lastIndexOf("```");
                if (nl > 0 && last > nl) raw = raw.substring(nl + 1, last).trim();
            }
            List<String> questions = objectMapper.readValue(raw, new TypeReference<>() {});
            return questions.subList(0, Math.min(5, questions.size()));

        } catch (Exception e) {
            log.warn("Gemini suggestions failed, using fallback: {}", e.getMessage());
            return List.of(
                    "Tôi có thể cắt giảm chi tiêu ở danh mục nào tháng này?",
                    "Tiến độ các dự án tiết kiệm của tôi đang như thế nào?",
                    "Tôi có đang chi tiêu vượt ngân sách không?");
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> buildBody(String systemPrompt, String userMessage, boolean stream) {
        Map<String, Object> systemInstruction = Map.of(
                "parts", List.of(Map.of("text", systemPrompt)));

        Map<String, Object> content = Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userMessage)));

        Map<String, Object> genConfig = Map.of(
                "maxOutputTokens", MAX_OUTPUT_TOKENS,
                "temperature", TEMPERATURE);

        return Map.of(
                "systemInstruction", systemInstruction,
                "contents", List.of(content),
                "generationConfig", genConfig);
    }

    private String extractText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("candidates")
                .path(0).path("content")
                .path("parts").path(0)
                .path("text").asText();
    }
}
