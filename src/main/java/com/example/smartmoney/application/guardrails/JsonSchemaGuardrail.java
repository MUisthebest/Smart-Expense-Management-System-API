package com.example.smartmoney.application.guardrails;

import com.example.smartmoney.application.dtos.responses.ChatResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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

    private static final int LOG_SNIPPET_LENGTH = 300;

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
        String retried = null;
        try {
            retried = retrySupplier.get();
            result = tryParse(retried);
            if (result != null) return result;
        } catch (Exception e) {
            log.warn("JsonSchemaGuardrail: retry call failed: {}", e.getMessage());
        }

        // Fallback — safe default prevents NPE in use case. Log a snippet of whichever
        // raw response we have so truncation/formatting drift can be diagnosed later
        // (the raw text itself is never returned to the user).
        String snippet = snippet(retried != null ? retried : rawResponse);
        log.warn("JsonSchemaGuardrail: falling back to GENERAL_ADVICE, raw response was: {}", snippet);
        return new ParsedLlmResponse(
                ChatResponse.ChatIntent.GENERAL_ADVICE,
                "Xin lỗi, tôi không thể xử lý yêu cầu này lúc này. Vui lòng thử lại sau.",
                List.of(),
                null);
    }

    private ParsedLlmResponse tryParse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String clean = extractJsonObject(stripMarkdownFences(raw.trim()));
        if (clean == null) return null;
        try {
            JsonNode root = objectMapper.readTree(clean);
            JsonNode intentNode = root.get("intent");
            JsonNode replyNode = root.get("reply");
            if (intentNode == null || replyNode == null) return null;
            String intentStr = intentNode.asText();
            if (!VALID_INTENTS.contains(intentStr)) return null;

            List<String> relatedQuestions = new ArrayList<>();
            JsonNode relatedNode = root.get("relatedQuestions");
            if (relatedNode != null && relatedNode.isArray()) {
                relatedNode.forEach(q -> {
                    if (q != null && !q.asText("").isBlank()) relatedQuestions.add(q.asText());
                });
            }

            return new ParsedLlmResponse(
                    ChatResponse.ChatIntent.valueOf(intentStr),
                    replyNode.asText(),
                    relatedQuestions,
                    parseTransactionDraft(root.get("transactionDraft")));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Reads the optional {@code transactionDraft} object — the running slot-fill state for a
     * step-by-step transaction create/update/delete. Deliberately lenient: every field is
     * optional and no value is validated here beyond "is it present". A draft that's missing
     * fields, names a category that doesn't exist, or claims {@code complete} when it isn't must
     * NOT invalidate the whole reply (which is otherwise perfectly good) — {@code
     * TransactionChangeService} is the single place that decides whether a draft is actually
     * executable, so a malformed one simply yields no confirm card and the assistant keeps
     * asking. Returns null when the field is absent, which is the case for every non-transaction
     * turn.
     */
    private TransactionDraft parseTransactionDraft(JsonNode node) {
        if (node == null || !node.isObject()) return null;
        return new TransactionDraft(
                text(node, "operation"),
                text(node, "transactionId"),
                node.hasNonNull("amount") ? node.get("amount").decimalValue() : null,
                text(node, "type"),
                text(node, "category"),
                text(node, "description"),
                text(node, "date"),
                node.path("complete").asBoolean(false));
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String s = value.asText("").trim();
        return s.isEmpty() ? null : s;
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

    /**
     * Extracts the first balanced {@code {...}} object from the text, tolerating stray
     * prose the model adds before/after the JSON despite instructions not to. Returns
     * null (rather than a partial string) when the braces never balance — e.g. output
     * truncated mid-object by a token-budget cutoff, which is unrecoverable here and
     * must surface as a parse failure so the retry/fallback path kicks in.
     */
    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        if (start < 0) return null;

        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) return text.substring(start, i + 1);
            }
        }
        return null;
    }

    private String snippet(String raw) {
        if (raw == null) return "<null>";
        return raw.length() <= LOG_SNIPPET_LENGTH ? raw : raw.substring(0, LOG_SNIPPET_LENGTH) + "...";
    }

    public record ParsedLlmResponse(ChatResponse.ChatIntent intent, String reply,
                                     List<String> relatedQuestions,
                                     TransactionDraft transactionDraft) {}

    /**
     * The assistant's running record of a transaction change it is collecting field by field.
     *
     * <p>Slot-filling lives with the LLM rather than in a Java regex parser or a separate Redis
     * draft key: the model already sees the conversation history every turn, so it can carry
     * forward what the user gave two turns ago and merge in what they just said, in either
     * language, without the backend having to hold multi-turn state of its own. What the backend
     * keeps is authority over the outcome — {@code complete} is the model's CLAIM that nothing is
     * missing, never permission to execute. Every field is re-validated (amount positive,
     * category real and legal for the type, date parseable, transaction owned by this user and
     * not project-linked) before any confirm card is offered.
     *
     * @param complete the model's claim that every field the operation needs is now known
     */
    public record TransactionDraft(String operation, String transactionId,
                                   java.math.BigDecimal amount, String type, String category,
                                   String description, String date, boolean complete) {}
}
