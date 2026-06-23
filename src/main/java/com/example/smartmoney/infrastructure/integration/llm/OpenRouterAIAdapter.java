package com.example.smartmoney.infrastructure.integration.llm;

import com.example.smartmoney.application.ports.chat.AIChatPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.function.Consumer;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class OpenRouterAIAdapter implements AIChatPort {

    private final ObjectMapper objectMapper;
    private final OpenRouterProperties properties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();

    @Override
    public String chat(String message, String userId) {

        try {
            String url = "https://openrouter.ai/api/v1/chat/completions";

            Map<String, Object> body = Map.of(
                    "model", "deepseek/deepseek-v4-flash",
                    "temperature", 0.3,
                    "max_tokens", 800,
                    "messages", List.of(
                            Map.of(
                                    "role",
                                    "system",
                                    "content",
                                    """
                                            Bạn là SmartMoney AI.

                                            Nhiệm vụ:
                                            - Tối đa 800 chữ.
                                            - Trả lời chính xác câu hỏi của người dùng.
                                            - Nếu câu hỏi không liên quan tài chính thì vẫn trả lời bình thường.
                                            - Không cố gắng liên hệ mọi câu trả lời với tài chính.
                                            - Trả lời ngắn gọn, tự nhiên, bằng tiếng Việt.
                                            - Khi phân tích mục tiêu tài chính:
                                                + Luôn đánh giá tiến độ hiện tại.
                                                + So sánh số tiền đã tiết kiệm với mục tiêu.
                                                + Xác định còn thiếu bao nhiêu.
                                                + Xác định số tháng còn lại.
                                                + Tính số tiền cần tiết kiệm mỗi tháng để hoàn thành đúng hạn.
                                                + Nếu tiến độ chậm, giải thích mức độ rủi ro và đề xuất điều chỉnh.
                                                + Nếu tiến độ tốt hoặc gần hoàn thành, hãy động viên người dùng.
                                                + Không chỉ lặp lại dữ liệu đầu vào.
                                            """),
                            Map.of("role", "user", "content", message)));

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
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

        } catch (Exception e) {
            throw new RuntimeException("Failed to call AI service", e);
        }
    }

    @Override
    public List<String> generateSuggestions(String userData, String userId) {
        try {
            String url = "https://openrouter.ai/api/v1/chat/completions";

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
                    "model", "deepseek/deepseek-v4-flash",
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userData)));

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
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

            // Parse the JSON array
            List<String> questions = objectMapper.readValue(content, new TypeReference<List<String>>() {
            });
            // Ensure exactly 3 questions, pad if necessary
            while (questions.size() < 3) {
                questions.add("Bạn có muốn kiểm tra lại ngân sách tháng này không?");
            }
            return questions.subList(0, 3);

        } catch (Exception e) {
            // Fallback questions
            return List.of(
                    "Bạn có dự định tiết kiệm cho mục tiêu nào trong tháng này không?",
                    "Hãy xem xét các khoản chi tiêu không cần thiết và cắt giảm chúng.",
                    "Bạn đã kiểm tra tiến độ các dự án tài chính của mình chưa?");
        }
    }

    @Override
    public void chatStream(String message, String userId,
            Consumer<String> onNext,
            Consumer<Throwable> onError,
            Runnable onComplete) {
        try {
            String url = "https://openrouter.ai/api/v1/chat/completions";

            Map<String, Object> body = new HashMap<>();
            body.put("model", "deepseek/deepseek-v4-flash");
            body.put("temperature", 0.3);
            body.put("max_tokens", 800);
            body.put("stream", true); // ⭐ BẬT CHẾ ĐỘ STREAM
            body.put("messages", List.of(
                    Map.of("role", "system", "content",
                            """
                                    Bạn là SmartMoney AI.
                                    Nhiệm vụ:
                                    - Tối đa 800 chữ.
                                    - Trả lời chính xác câu hỏi của người dùng.
                                    - Nếu câu hỏi không liên quan tài chính thì vẫn trả lời bình thường.
                                    - Không cố gắng liên hệ mọi câu trả lời với tài chính.
                                    - Trả lời ngắn gọn, tự nhiên, bằng tiếng Việt.
                                    - Khi phân tích mục tiêu tài chính: Luôn đánh giá tiến độ, so sánh, tính số tiền cần tiết kiệm mỗi tháng, đánh giá rủi ro và động viên.
                                    """),
                    Map.of("role", "user", "content", message)));

            String requestBody = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMinutes(2))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .header("HTTP-Referer", "https://smartmoney.ai")
                    .header("X-Title", "SmartMoney AI")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // Gửi request bất đồng bộ và xử lý luồng dữ liệu
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                    .thenAccept(response -> {
                        if (response.statusCode() != 200) {
                            onError.accept(new RuntimeException("AI API error: " + response.statusCode()));
                            return;
                        }
                        StringBuilder fullReply = new StringBuilder(); // ⬅️ Tích luỹ toàn bộ
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                if (line.isBlank() || !line.startsWith("data: "))
                                    continue;
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
                                        fullReply.append(content); // ⬅️ nối token
                                        onNext.accept(fullReply.toString()); // ⬅️ gửi TOÀN BỘ text
                                    }
                                } catch (Exception ignored) {
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