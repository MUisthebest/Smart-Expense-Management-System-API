package com.example.smartmoney.application.services;

import java.util.regex.Pattern;

/**
 * Best-effort heuristic language detection for the user's chat message, used to pick between
 * Vietnamese/English templates for server-computed suggestion {@code reason} strings (the LLM's
 * own {@code reply} field handles its own language matching via the system prompt — this class
 * only covers the hardcoded Java strings in {@code BudgetAdvisorService},
 * {@code WindfallAllocationService}, and {@code ProjectUpdateSuggestionService}).
 *
 * Not a full language-detection library — matches this codebase's existing regex-based approach
 * to message classification (see {@code ScopeGuardrail}, intent-detection patterns, etc.).
 * Defaults to Vietnamese whenever detection is inconclusive, matching this app's Vietnamese-first
 * convention.
 */
public final class MessageLanguageDetector {

    private MessageLanguageDetector() {}

    private static final Pattern VIETNAMESE_DIACRITICS = Pattern.compile(
            "[àáạảãăằắặẳẵâầấậẩẫđèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹ]",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ENGLISH_HINTS = Pattern.compile(
            "\\b(the|is|are|what|how|should|please|my|i|you|budget|project|save|saving|spend|" +
            "spending|income|money|debt|month|if|would|could|can)\\b",
            Pattern.CASE_INSENSITIVE);

    // Common Vietnamese words typed WITHOUT diacritics ("gõ không dấu") — a real-world habit that
    // defeats VIETNAMESE_DIACRITICS entirely. Without this check, a diacritic-free Vietnamese
    // message that happens to use an English loanword ("project", "budget" — both common in VN
    // finance/tech vernacular, e.g. "rut ngan project mua xe 3 thang") was misdetected as English
    // purely because ENGLISH_HINTS matched "project" while no diacritics were present.
    private static final Pattern VIETNAMESE_NO_DIACRITICS_HINTS = Pattern.compile(
            "\\b(toi|ban|minh|va|la|cua|nay|khong|duoc|giup|muon|can|thang|nam|tien|chi|thu|nhap|" +
            "du an|ngan sach|tiet kiem|rut ngan|keo dai|xoa|tao)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * True only when the message has no Vietnamese diacritics, matches common English words, and
     * doesn't also match common diacritic-free Vietnamese words (which take priority — this app is
     * Vietnamese-first, so an ambiguous mix defaults to Vietnamese).
     */
    public static boolean isEnglish(String messageLow) {
        if (messageLow == null || messageLow.isBlank()) return false;
        if (VIETNAMESE_DIACRITICS.matcher(messageLow).find()) return false;
        if (VIETNAMESE_NO_DIACRITICS_HINTS.matcher(messageLow).find()) return false;
        return ENGLISH_HINTS.matcher(messageLow).find();
    }
}
