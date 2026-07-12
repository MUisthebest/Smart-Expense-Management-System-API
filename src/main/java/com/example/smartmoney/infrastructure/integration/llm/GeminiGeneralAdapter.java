package com.example.smartmoney.infrastructure.integration.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Gemini, called directly against Google's Generative Language API — routed to by
 * {@code LlmRouter} for GENERAL_ADVICE-shaped messages, and used directly (no routing) for
 * {@code /suggestions}, the semantic scope classifier, and chat-history summarisation — all
 * cheap, non-transactional calls.
 */
@Component("geminiFlashAdapter")
public class GeminiGeneralAdapter extends AbstractGeminiAdapter {

    private final String model;

    public GeminiGeneralAdapter(
            ObjectMapper objectMapper,
            GeminiProperties properties,
            @Value("${app.gemini.model-general:gemini-2.5-flash-lite}") String model) {
        super(objectMapper, properties);
        this.model = model;
    }

    @Override
    protected String model() {
        return model;
    }
}
