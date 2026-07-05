package com.example.smartmoney.application.guardrails;

import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Output guardrail for POST /chat.
 * Validates that the Gemini response is parseable JSON with required fields
 * (intent, reply) and a valid intent enum. Retries once via the supplier on
 * failure, then falls back to a GENERAL_ADVICE reply. Prevents NPE downstream.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JsonSchemaGuardrail {

    private static final Set<String> VALID_INTENTS = EnumSet.allOf(ChatResponse.ChatIntent.class)
            .stream().map(Enum::name).collect(java.util.stream.Collectors.toSet());

    private final ObjectMapper objectMapper;

    /**
     * Validates and parses the LLM response JSON.
     * On first failure, calls {@code retrySupplier} and validates again.
     * On second failure, returns a safe GENERAL_ADVICE fallback.
     *
     * @param rawResponse    JSON string from Gemini
     * @param retrySupplier  callable that re-invokes Gemini for a fresh response
     * @return validated ChatResponse (intent + reply only; structured payloads added by caller)
     */
    public ParsedLlmResponse validate(String rawResponse, Supplier<String> retrySupplier) {
        ParsedLlmResponse result = tryParse(rawResponse);
        if (result != null) return result;

        log.warn("JsonSchemaGuardrail: initial parse failed, retrying once");
        try {
            String retried = retrySupplier.get();
            result = tryParse(retried);
            if (result != null) return result;
        } catch (Exception e) {
            log.warn("JsonSchemaGuardrail: retry call failed: {}", e.getMessage());
        }

        // Fallback — safe default prevents NPE in use case
        log.warn("JsonSchemaGuardrail: falling back to GENERAL_ADVICE");
        return new ParsedLlmResponse(
                ChatResponse.ChatIntent.GENERAL_ADVICE,
                "Xin lỗi, tôi không thể xử lý yêu cầu này lúc này. Vui lòng thử lại sau.");
    }

    private ParsedLlmResponse tryParse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String clean = stripMarkdownFences(raw.trim());
        try {
            JsonNode root = objectMapper.readTree(clean);
            JsonNode intentNode = root.get("intent");
            JsonNode replyNode = root.get("reply");
            if (intentNode == null || replyNode == null) return null;
            String intentStr = intentNode.asText();
            if (!VALID_INTENTS.contains(intentStr)) return null;
            return new ParsedLlmResponse(
                    ChatResponse.ChatIntent.valueOf(intentStr),
                    replyNode.asText());
        } catch (Exception e) {
            return null;
        }
    }

    private String stripMarkdownFences(String raw) {
        if (raw.startsWith("```")) {
            int firstNewline = raw.indexOf('\n');
            int lastFence = raw.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                return raw.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return raw;
    }

    public record ParsedLlmResponse(ChatResponse.ChatIntent intent, String reply) {}
}
