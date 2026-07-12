package com.example.smartmoney.infrastructure.integration.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Gemini, called directly against Google's Generative Language API — routed to by
 * {@code LlmRouter} for BUDGET_UPDATE, SIMULATION, and PROJECT_UPDATE-shaped messages
 * (budget/project adjustment or creation).
 */
@Component("geminiTransactionalAdapter")
public class GeminiTransactionalAdapter extends AbstractGeminiAdapter {

    private final String model;

    public GeminiTransactionalAdapter(
            ObjectMapper objectMapper,
            GeminiProperties properties,
            @Value("${app.gemini.model-transactional:gemini-2.5-flash}") String model) {
        super(objectMapper, properties);
        this.model = model;
    }

    @Override
    protected String model() {
        return model;
    }
}
