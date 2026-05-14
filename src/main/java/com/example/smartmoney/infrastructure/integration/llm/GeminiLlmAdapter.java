package com.example.smartmoney.infrastructure.integration.llm;

import com.example.smartmoney.application.ports.llm.LlmPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Infrastructure adapter — calls Google Gemini generateContent REST API.
 * Implements {@link LlmPort} so the application layer stays decoupled from Gemini specifics.
 */
@Slf4j
@Component
public class GeminiLlmAdapter implements LlmPort {

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash-lite:generateContent?key={apiKey}";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public GeminiLlmAdapter(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${app.gemini.api-key}") String apiKey
    ) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    @Override
    public String call(String prompt) {
        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", prompt)))
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    GEMINI_URL,
                    HttpMethod.POST,
                    request,
                    String.class,
                    apiKey
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            return root
                    .path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text")
                    .asText();

        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage(), e);
            throw new RuntimeException("LLM service unavailable: " + e.getMessage(), e);
        }
    }
}
