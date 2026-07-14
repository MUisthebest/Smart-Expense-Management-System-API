package com.example.smartmoney.application.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MessageLanguageDetector")
class MessageLanguageDetectorTest {

    @Test
    @DisplayName("detects a proper Vietnamese message (with diacritics) as not English")
    void vietnameseWithDiacriticsIsNotEnglish() {
        assertThat(MessageLanguageDetector.isEnglish("tôi muốn rút ngắn dự án mua xe 3 tháng"))
                .isFalse();
    }

    @Test
    @DisplayName("detects diacritic-free Vietnamese containing an English loanword as not English")
    void diacriticFreeVietnameseWithLoanwordIsNotEnglish() {
        // Real-world "gõ không dấu" typing habit, mixed with common finance/tech loanwords —
        // previously misdetected as English purely because "project" matched ENGLISH_HINTS while
        // no diacritics were present.
        assertThat(MessageLanguageDetector.isEnglish("rut ngan project mua xe 3 thang")).isFalse();
        assertThat(MessageLanguageDetector.isEnglish("toi muon tang budget an uong thang nay")).isFalse();
    }

    @Test
    @DisplayName("detects a genuine English message as English")
    void genuineEnglishIsEnglish() {
        assertThat(MessageLanguageDetector.isEnglish("what is my budget for this month")).isTrue();
        assertThat(MessageLanguageDetector.isEnglish("i want to shorten the deadline for my car project"))
                .isTrue();
    }

    @Test
    @DisplayName("defaults to not-English (Vietnamese) when inconclusive")
    void inconclusiveDefaultsToNotEnglish() {
        assertThat(MessageLanguageDetector.isEnglish("123456")).isFalse();
        assertThat(MessageLanguageDetector.isEnglish("")).isFalse();
        assertThat(MessageLanguageDetector.isEnglish(null)).isFalse();
    }
}
