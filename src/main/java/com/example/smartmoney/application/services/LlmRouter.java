package com.example.smartmoney.application.services;

import com.example.smartmoney.application.ports.chat.AIChatPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Resolves which {@link AIChatPort} handles a chat turn. Historically routed between two
 * Gemini-backed tiers by a coarse keyword heuristic; now a single Claude Haiku 4.5 adapter
 * handles every call (main chat, history summarisation, the semantic scope guardrail, and
 * suggestion chips), so {@link #forMessage(String)} always returns it. Kept as a class — rather
 * than injecting {@code AIChatPort} directly everywhere — so call sites don't change if routing
 * between models is reintroduced later.
 */
@Service
public class LlmRouter {

    private final AIChatPort claudeHaikuAdapter;

    public LlmRouter(@Qualifier("claudeHaikuAdapter") AIChatPort claudeHaikuAdapter) {
        this.claudeHaikuAdapter = claudeHaikuAdapter;
    }

    /**
     * @param messageLow the user's message, already lower-cased (unused now that there is only
     *                   one model, kept in the signature for call-site compatibility)
     */
    public AIChatPort forMessage(String messageLow) {
        return claudeHaikuAdapter;
    }
}
