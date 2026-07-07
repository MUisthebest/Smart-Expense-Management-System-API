package com.example.smartmoney.application.ports.chat;

import java.util.List;
import java.util.function.Consumer;

/**
 * Port (interface) for calling an external LLM service.
 *
 * UC3 requires injecting a personalised system prompt, so the interface was
 * updated to accept a separate {@code systemPrompt} parameter alongside the
 * user message. The old single-string signatures are kept as comments for
 * reference but are no longer called by any use case.
 */
public interface AIChatPort {

    // ── Old signature (single combined message, hard-coded system prompt) ───
    // No longer used — kept for reference only.
    //
    // String chat(String message, String userId);
    //
    // void chatStream(String message, String userId,
    //             Consumer<String> onNext,
    //             Consumer<Throwable> onError,
    //             Runnable onComplete);

    /**
     * UC1 / UC2 / UC3 — Synchronous chat with a caller-supplied system prompt.
     *
     * @param systemPrompt UC3 personalised instructions built by {@code ChatbotPromptBuilder}
     * @param userMessage  the composed user turn (context + history + current message)
     * @param userId       used for logging / per-user rate-limit headers
     */
    String chat(String systemPrompt, String userMessage, String userId);

    /**
     * Generates proactive question chips shown in the frontend suggestion bar.
     * System prompt is controlled internally by the adapter for this call.
     */
    List<String> generateSuggestions(String userData, String userId);

    /**
     * UC1 / UC2 / UC3 — Streaming chat with a caller-supplied system prompt.
     * Each token is delivered via {@code onNext}; completion is signalled by {@code onComplete}.
     *
     * @param systemPrompt UC3 personalised instructions built by {@code ChatbotPromptBuilder}
     * @param userMessage  the composed user turn (context + history + current message)
     * @param userId       used for logging / per-user rate-limit headers
     * @param onNext       receives cumulative text so far on each token arrival
     * @param onError      receives the throwable on any HTTP or parse error
     * @param onComplete   called once when the stream ends normally
     */
    void chatStream(String systemPrompt, String userMessage, String userId,
                Consumer<String> onNext,
                Consumer<Throwable> onError,
                Runnable onComplete);
}
