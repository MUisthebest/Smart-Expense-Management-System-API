package com.example.smartmoney.application.usecases.insight;

import com.example.smartmoney.application.ports.llm.LlmPort;
import com.example.smartmoney.domain.models.insight.Insight;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * P3-C — the <em>only</em> place an LLM enters the insight pipeline. Turns a fully-computed
 * {@link Insight} (structured {@code metrics} + stable {@code code}) into one localized sentence.
 *
 * <p>Hard guarantees (docs/redesign-phase2-3-plan.md §P3-C):
 * <ul>
 *   <li><b>The LLM phrases; it never computes.</b> The prompt hands the model the exact figures and
 *       forbids inventing or recomputing any number — the deterministic facts are authoritative.</li>
 *   <li><b>Best-effort, never on the critical path.</b> Disabled by default; on any failure (or a
 *       blank reply) {@link #narrate} returns {@code null} and the caller falls back to the i18n
 *       {@code code} (the Phase-1 behaviour).</li>
 * </ul>
 */
@Service
@Slf4j
public class InsightNarrator {

    /** Default narration language until a per-user locale is plumbed through. */
    static final String DEFAULT_LANGUAGE = "English";

    private final LlmPort llmPort;
    private final boolean enabled;

    public InsightNarrator(LlmPort llmPort,
                           @Value("${app.insight.narration.enabled:false}") boolean enabled) {
        this.llmPort = llmPort;
        this.enabled = enabled;
    }

    /** Localized one-liner for an insight, or {@code null} to fall back to the i18n code. */
    public String narrate(Insight insight) {
        return narrate(insight, DEFAULT_LANGUAGE);
    }

    /**
     * Localized one-liner in {@code language}, or {@code null} when narration is disabled, the
     * insight is absent, or the LLM is unavailable / returns nothing usable. Never throws.
     */
    public String narrate(Insight insight, String language) {
        if (!enabled || insight == null) {
            return null;
        }
        try {
            String reply = llmPort.call(buildPrompt(insight, language));
            if (reply == null || reply.isBlank()) {
                return null;
            }
            // The prompt asks for whole dong grouped in threes; this enforces it. The FE renders
            // `narrative` verbatim — skipping the formatting it applies to every other money field —
            // so a "$" or a raw "1200000.00" the model slipped in reaches the screen unaltered.
            String cleaned = reply.trim();
            String guarded = NarrationCurrencyGuard.enforce(cleaned);
            if (guarded == null) {
                log.warn("[Narration] dropped a {} narrative carrying a foreign currency — "
                        + "falling back to code", insight.getCode());
            } else if (!guarded.equals(cleaned)) {
                // Not fatal — the amount was relabelled and regrouped, not changed in value — but a
                // steady stream of these means the prompt's CURRENCY rule has stopped landing.
                log.debug("[Narration] normalized an amount in a {} narrative", insight.getCode());
            }
            return guarded;
        } catch (Exception e) {
            // Narration is a garnish: a model outage must never break the insight response or a push.
            log.warn("[Narration] LLM narration failed for {} — falling back to code: {}",
                    insight.getCode(), e.getMessage());
            return null;
        }
    }

    /**
     * Deterministic prompt that embeds the already-computed facts and forbids the model from
     * touching the numbers. Package-visible so the guardrail test can assert the template path.
     */
    String buildPrompt(Insight insight, String language) {
        StringBuilder facts = new StringBuilder();
        for (Map.Entry<String, Object> e : insight.getMetrics().entrySet()) {
            facts.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        return "You are a personal-finance assistant. Rephrase the pre-computed insight below as ONE "
                + "short, friendly sentence in " + language + " for the user.\n"
                + "STRICT RULES: use ONLY the numbers given; do not invent, recompute, convert, round "
                + "differently, or add any figure; do not give advice beyond the insight; output only "
                + "the sentence.\n"
                + "CURRENCY: every amount below is Vietnamese dong. Write amounts as a whole number "
                + "with a comma every three digits, followed by VND — e.g. \"1,200,000 VND\", never "
                + "\"1200000\", \"1.200.000\" or \"1,200,000.00\"; drop any decimal part (dong has no "
                + "minor unit). NEVER write \"$\", \"USD\", \"US$\", \"dollar\", \"₫\" or \"đ\" — the "
                + "figures are not dollars and must not be converted.\n\n"
                + "Insight code: " + insight.getCode() + "\n"
                + "Type: " + insight.getType()
                + "; severity: " + insight.getSeverity()
                + "; category: " + (insight.getCategory() == null ? "none" : insight.getCategory())
                + "; period: " + (insight.getPeriod() == null ? "none" : insight.getPeriod()) + "\n"
                + "Facts:\n" + facts;
    }
}
