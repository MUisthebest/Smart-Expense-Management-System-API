package com.example.smartmoney.infrastructure.integration.llm;

import com.example.smartmoney.application.ports.chat.AIChatPort;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OpenRouterAIAdapter implements AIChatPort {

    private final ObjectMapper objectMapper = new ObjectMapper();
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
                    "messages", List.of(
                    Map.of(
                        "role",
                        "system",
                        "content",
                        """
                        Bạn là SmartMoney AI.

                        Nhiệm vụ:
                        - Trả lời chính xác câu hỏi của người dùng.
                        - Nếu câu hỏi liên quan tài chính cá nhân thì ưu tiên tư vấn tài chính.
                        - Nếu câu hỏi không liên quan tài chính thì vẫn trả lời bình thường.
                        - Không cố gắng liên hệ mọi câu trả lời với tài chính.
                        - Trả lời ngắn gọn, tự nhiên, bằng tiếng Việt.
                        """
                    ),
                    Map.of("role", "user", "content", message)
                    )
            );

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
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new RuntimeException("AI API error: " + response.body());
            }

            Map<String, Object> res =
                    objectMapper.readValue(response.body(), Map.class);

            List choices = (List) res.get("choices");
            Map choice = (Map) choices.get(0);
            Map msg = (Map) choice.get("message");

            return msg.get("content").toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to call AI service", e);
        }
    }
}