package com.example.smartmoney.application.guardrails;

import jakarta.annotation.PostConstruct;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Order(2) — prompt-injection guardrail.
 * Detects instruction overrides, jailbreak attempts, role manipulation,
 * and information-extraction attacks. Returns position + context snippet
 * on failure for auditability.
 */
@Component
@Order(2)
public class PromptInjectionGuardrail implements PromptGuardrail {

    private Pattern[] injectionPatterns;

    @PostConstruct
    void init() {
        String[] patterns = {
            // Instruction overrides
            "ignore (previous|all|prior|above) instruction",
            "forget (your|all|previous) instruction",
            "disregard (your|all|previous|the)",
            "new instruction[s]?[:\\s]",
            "override (your|the) (previous|system|prior)",

            // Jailbreak
            "act as (a|an|if)",
            "pretend (you are|to be)",
            "you are now",
            "\\bDAN\\b",
            "jailbreak",
            "developer mode",
            "unrestricted mode",

            // Role / identity manipulation
            "your (real|true|actual) (identity|self|purpose|instructions)",
            "you('re| are) (actually|really) (not|a|an)",
            "reveal (your|the) (system|original|hidden) prompt",

            // Information extraction
            "what are your (instructions|rules|guidelines|constraints)",
            "show me your (prompt|system message|instructions)",
            "repeat (everything|all|your instructions) (above|before|that)"
        };
        injectionPatterns = new Pattern[patterns.length];
        for (int i = 0; i < patterns.length; i++) {
            injectionPatterns[i] = Pattern.compile(patterns[i],
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        }
    }

    @Override
    public void validate(String message, UUID userId) {
        String lower = message.toLowerCase();
        for (Pattern p : injectionPatterns) {
            Matcher m = p.matcher(lower);
            if (m.find()) {
                int pos = m.start();
                int snippetEnd = Math.min(lower.length(), pos + 60);
                String snippet = lower.substring(pos, snippetEnd);
                throw new GuardrailViolationException(
                        "Phát hiện nội dung can thiệp prompt tại vị trí " + pos +
                        ": \"" + snippet + "\"");
            }
        }
    }
}
