package com.example.smartmoney.application.usecases.insight;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Post-generation currency guard for {@link InsightNarrator}. Every figure this app holds is
 * Vietnamese dong, but the metrics handed to the model are bare numbers, so a model asked to
 * phrase them in English reliably reaches for "$1,200,000" — and the front-end renders
 * {@code narrative} verbatim, skipping the number formatting it applies to everything else. The
 * wrong symbol therefore lands on the user's screen exactly as the model wrote it.
 *
 * <p>The prompt already forbids this; this is the net under it, because a prompt rule is a
 * request and the response has to be a guarantee.
 *
 * <p>Two stages, in order:
 * <ol>
 *   <li><b>Rewrite</b> the label. A currency token attached to a number is only ever a
 *       mislabelling — the digits came from our own metrics and are dong either way — so the token
 *       is normalized to {@code VND} and the number is left untouched. This also folds the
 *       legitimate Vietnamese forms ({@code đồng}, {@code ₫}, {@code VNĐ}) onto one spelling, so
 *       EN and VI narratives quote money the same way.</li>
 *   <li><b>Reject</b> whatever survives. A {@code $} or "USD" that isn't attached to a number
 *       can't be rewritten without guessing at meaning, so the sentence is discarded and the
 *       caller falls back to the i18n {@code code} — a localized template beats a sentence
 *       quoting the wrong currency.</li>
 * </ol>
 *
 * <p>Deliberately narrow: only numbers already carrying a currency token are touched. Bare
 * numbers are left alone, because most of them aren't money — sample months, day-of-month,
 * z-scores and percentages all travel in the same metrics map, and suffixing those with "VND"
 * would invent a claim the insight never made.
 *
 * <p>Applied at two points, because a narrative has two lifetimes. {@link InsightNarrator} runs it
 * at generation. {@code SuggestionResponseAssembler} runs it again on read, because a suggestion's
 * sentence is frozen into {@code payload.narrative} at ask time and the card carries no expiry —
 * so cards raised before this guard existed would otherwise keep showing their original symbol
 * forever. Idempotent, so the second pass is free on anything the first already cleaned.
 */
public final class NarrationCurrencyGuard {

    /** The one spelling every narrated amount ends up in. */
    static final String CURRENCY = "VND";

    /**
     * Currency tokens split by what has to follow them. Symbols need no word boundary — one after
     * a space ("800,000$ ") is bounded by non-word characters on both sides, where {@code \b}
     * matches nothing. Word tokens do need it, so that a bare {@code đ} can never match the first
     * letter of a longer Vietnamese word; {@code đồng} precedes {@code đ} in the alternation for
     * the same reason.
     */
    private static final String SYMBOL = "US\\$|\\$|₫";
    private static final String WORD = "USD|dollars?|VNĐ|VND|đồng|đ";
    private static final String TOKEN = "(?:" + SYMBOL + "|(?:" + WORD + ")\\b)";

    /** An amount: digits with optional grouping/decimal separators, always ending in a digit. */
    private static final String AMOUNT = "\\d[\\d.,]*\\d|\\d";

    /**
     * A number wearing a currency token — as a prefix, a suffix, or both (the model likes to write
     * "$1,200,000 VND" once the prompt mentions VND). Matched in one pass so a doubly-labelled
     * amount collapses to a single "VND" instead of leaving a dangling token behind.
     *
     * <p>{@code (?iu)} so the case-insensitivity reaches the non-ASCII spellings too ({@code VNĐ},
     * {@code Đồng}); plain {@code (?i)} folds ASCII only.
     */
    private static final Pattern LABELLED_AMOUNT = Pattern.compile(
            "(?iu)(?:" + TOKEN + "\\s*(" + AMOUNT + ")(?:\\s*" + TOKEN + ")?"
                    + "|(" + AMOUNT + ")\\s*" + TOKEN + ")");

    /** Anything foreign left after the rewrite — unattached to a number, so unsalvageable. */
    private static final Pattern RESIDUAL_FOREIGN = Pattern.compile("(?iu)\\$|\\bUSD\\b|\\bdollars?\\b");

    private NarrationCurrencyGuard() {
    }

    /**
     * The sentence with every amount labelled {@code VND}, or {@code null} when a foreign currency
     * survived the rewrite and the sentence must be dropped.
     */
    public static String enforce(String narrative) {
        if (narrative == null || narrative.isBlank()) {
            return null;
        }
        Matcher matcher = LABELLED_AMOUNT.matcher(narrative);
        StringBuilder rewritten = new StringBuilder();
        while (matcher.find()) {
            String amount = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(amount + " " + CURRENCY));
        }
        matcher.appendTail(rewritten);

        String result = rewritten.toString();
        return RESIDUAL_FOREIGN.matcher(result).find() ? null : result;
    }
}
