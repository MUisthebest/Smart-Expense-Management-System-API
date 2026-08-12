package com.example.smartmoney.application.usecases.insight;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NarrationCurrencyGuard — amounts reach the FE as VND, or not at all")
class NarrationCurrencyGuardTest {

    @Nested
    @DisplayName("rewrites a mislabelled amount (the digits are dong either way)")
    class Rewrites {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource(delimiter = '|', value = {
                // The reported bug: an English narrative reaching for the dollar sign.
                "You are on track to spend $1,200,000 this month.|You are on track to spend 1,200,000 VND this month.",
                // Prefix with a space, and the US$ form.
                "Spending hit $ 1,200,000 in June.|Spending hit 1,200,000 VND in June.",
                "Spending hit US$1,200,000 in June.|Spending hit 1,200,000 VND in June.",
                // Suffix forms.
                "You saved 800,000 USD last month.|You saved 800,000 VND last month.",
                "You saved 800,000$ last month.|You saved 800,000 VND last month.",
                "You saved 800,000 dollars last month.|You saved 800,000 VND last month.",
                // Both at once — the model's favourite once the prompt mentions VND. Collapses to one.
                "Budget is $1,200,000 VND for food.|Budget is 1,200,000 VND for food.",
                // Vietnamese spellings fold onto the same token, so EN and VI quote money alike.
                "Bạn đã chi 1.200.000 đồng cho ăn uống.|Bạn đã chi 1.200.000 VND cho ăn uống.",
                "Bạn đã chi 1.200.000đ cho ăn uống.|Bạn đã chi 1.200.000 VND cho ăn uống.",
                "Bạn đã chi 1.200.000 ₫ cho ăn uống.|Bạn đã chi 1.200.000 VND cho ăn uống.",
                "Bạn đã chi 1.200.000 VNĐ cho ăn uống.|Bạn đã chi 1.200.000 VND cho ăn uống.",
                // Several amounts in one sentence.
                "You spent $600,000 of your $1,000,000 limit.|You spent 600,000 VND of your 1,000,000 VND limit.",
        })
        void rewritesToVnd(String reply, String expected) {
            assertEquals(expected, NarrationCurrencyGuard.enforce(reply));
        }

        @Test
        @DisplayName("an amount already in VND is passed through untouched")
        void alreadyCorrectIsUnchanged() {
            String clean = "You are on track to spend 1,200,000 VND this month.";
            assertEquals(clean, NarrationCurrencyGuard.enforce(clean));
        }

        @Test
        @DisplayName("a leading 'VND 1,200,000' is normalized to the trailing form")
        void prefixVndMovesToSuffix() {
            assertEquals("Your limit is 1,200,000 VND.",
                    NarrationCurrencyGuard.enforce("Your limit is VND 1,200,000."));
        }
    }

    @Nested
    @DisplayName("leaves alone what isn't money")
    class LeavesNonMoneyAlone {

        /**
         * Sample counts, day-of-month, z-scores and percentages travel in the same metrics map as
         * the amounts. Suffixing those with VND would invent a claim the insight never made.
         */
        @ParameterizedTest
        @ValueSource(strings = {
                "Food spending is 2.1 standard deviations above your 6-month baseline.",
                "You are 18 days into a 30-day month and 85% through your budget.",
                "This is the 3rd month in a row you have stayed under your limit.",
                "Bạn đã dùng 85% ngân sách ăn uống trong 18 ngày.",
        })
        void bareNumbersAreUntouched(String reply) {
            assertEquals(reply, NarrationCurrencyGuard.enforce(reply));
        }

        @Test
        @DisplayName("a bare 'đ' inside a Vietnamese word is not mistaken for the dong sign")
        void dongSignDoesNotEatWords() {
            // "3 đợt" — the number is followed by a word starting with đ, not by a currency token.
            String reply = "Bạn có 3 đợt chi lớn trong tháng này.";
            assertEquals(reply, NarrationCurrencyGuard.enforce(reply));
        }
    }

    @Nested
    @DisplayName("rejects what it cannot honestly rewrite")
    class Rejects {

        @Test
        @DisplayName("a currency word with no number attached → null (FE falls back to the code)")
        void unattachedForeignCurrencyIsDropped() {
            assertNull(NarrationCurrencyGuard.enforce("Your budget is set in USD this month."));
            assertNull(NarrationCurrencyGuard.enforce("Amounts below are shown in dollars."));
            assertNull(NarrationCurrencyGuard.enforce("The $ figures are estimates."));
        }

        @Test
        @DisplayName("an explicit conversion is dropped rather than relabelled")
        void conversionIsDropped() {
            // Relabelling "50 USD" to "50 VND" here would keep a converted figure and call it dong.
            // The residual "$" in the same sentence is what forces the drop.
            assertNull(NarrationCurrencyGuard.enforce(
                    "You spent 1,200,000 VND, which is about 50 USD at today's $ rate."));
        }

        @Test
        @DisplayName("null / blank in → null out")
        void nullAndBlank() {
            assertNull(NarrationCurrencyGuard.enforce(null));
            assertNull(NarrationCurrencyGuard.enforce("   "));
        }
    }
}
