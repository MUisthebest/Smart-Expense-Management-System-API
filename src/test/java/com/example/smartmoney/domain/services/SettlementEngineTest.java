package com.example.smartmoney.domain.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link SettlementEngine}.
 * No Spring context — just math.
 */
@DisplayName("SettlementEngine")
class SettlementEngineTest {

    // ─────────────────────────────────────────────────────────────────────────
    // calculatePenalty
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("calculatePenalty")
    class CalculatePenalty {

        @Test
        @DisplayName("returns ZERO when spending is exactly at the safe limit")
        void noOverspend_exactlyAtLimit() {
            BigDecimal penalty = SettlementEngine.calculatePenalty(
                    new BigDecimal("2000000"), new BigDecimal("2000000"));

            assertThat(penalty).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("returns ZERO when spending is below the safe limit")
        void noOverspend_belowLimit() {
            BigDecimal penalty = SettlementEngine.calculatePenalty(
                    new BigDecimal("1500000"), new BigDecimal("2000000"));

            assertThat(penalty).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("returns positive penalty when spending exceeds the safe limit")
        void overspend_returnsCorrectPenalty() {
            // Spent 3 000 000, allowed 2 000 000 → penalty = 1 000 000
            BigDecimal penalty = SettlementEngine.calculatePenalty(
                    new BigDecimal("3000000"), new BigDecimal("2000000"));

            assertThat(penalty).isEqualByComparingTo(new BigDecimal("1000000.00"));
        }

        @Test
        @DisplayName("penalty is scaled to 2 decimal places")
        void penalty_scaleIsTwo() {
            BigDecimal penalty = SettlementEngine.calculatePenalty(
                    new BigDecimal("2000001"), new BigDecimal("2000000"));

            assertThat(penalty.scale()).isEqualTo(2);
            assertThat(penalty).isEqualByComparingTo(new BigDecimal("1.00"));
        }

        @Test
        @DisplayName("returns ZERO (scale=2) when no spending at all")
        void noSpending_returnsZero() {
            BigDecimal penalty = SettlementEngine.calculatePenalty(
                    BigDecimal.ZERO, new BigDecimal("2000000"));

            assertThat(penalty.scale()).isEqualTo(2);
            assertThat(penalty).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("penalty rounded correctly with HALF_UP on fractional amounts")
        void penalty_halfUpRounding() {
            // Spent 2000000.505, safe 2000000 → penalty = 0.51 (rounded HALF_UP)
            BigDecimal penalty = SettlementEngine.calculatePenalty(
                    new BigDecimal("2000000.505"), new BigDecimal("2000000"));

            assertThat(penalty).isEqualByComparingTo(new BigDecimal("0.51"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // recalculateMonthLeft
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("recalculateMonthLeft")
    class RecalculateMonthLeft {

        @Test
        @DisplayName("decrements by exactly 1 when the user is on track")
        void onTrack_decrementsBy1() {
            // target=12 000 000, saved=2 000 000 (1 month in), monthly=2 000 000
            // remaining=10 000 000, calculatedLeft=5.0, oldMonthLeft=6
            // diff = floor(6 - 5.0) = 1 → since diff > 0: newLeft = 6 - 1 = 5
            int result = SettlementEngine.recalculateMonthLeft(
                    new BigDecimal("12000000"),
                    new BigDecimal("2000000"),
                    new BigDecimal("2000000"),
                    6
            );

            assertThat(result).isEqualTo(5);
        }

        @Test
        @DisplayName("decrements faster when the user saved ahead of schedule")
        void aheadOfSchedule_decrementsMoreThan1() {
            // target=10 000 000, saved=5 000 000, monthly=1 000 000
            // remaining=5 000 000, calculatedLeft=5.0, oldMonthLeft=8
            // diff = floor(8 - 5.0) = 3 → newLeft = 8 - 3 = 5
            int result = SettlementEngine.recalculateMonthLeft(
                    new BigDecimal("10000000"),
                    new BigDecimal("5000000"),
                    new BigDecimal("1000000"),
                    8
            );

            assertThat(result).isEqualTo(5);
        }

        @Test
        @DisplayName("decrements by 1 when the user is slightly behind schedule")
        void behindSchedule_decrementsBy1() {
            // target=12 000 000, saved=1 000 000, monthly=2 000 000
            // remaining=11 000 000, calculatedLeft=5.5, oldMonthLeft=6
            // diff = floor(6 - 5.5) = 0 → since diff <= 0: newLeft = 6 - 1 = 5
            int result = SettlementEngine.recalculateMonthLeft(
                    new BigDecimal("12000000"),
                    new BigDecimal("1000000"),
                    new BigDecimal("2000000"),
                    6
            );

            assertThat(result).isEqualTo(5);
        }

        @Test
        @DisplayName("returns 0 when saved amount equals target")
        void savedEqualsTarget_returnsZero() {
            int result = SettlementEngine.recalculateMonthLeft(
                    new BigDecimal("10000000"),
                    new BigDecimal("10000000"),
                    new BigDecimal("1000000"),
                    3
            );

            assertThat(result).isZero();
        }

        @Test
        @DisplayName("returns 0 when saved exceeds target (overshoot guard)")
        void savedExceedsTarget_returnsZero() {
            int result = SettlementEngine.recalculateMonthLeft(
                    new BigDecimal("10000000"),
                    new BigDecimal("12000000"),
                    new BigDecimal("1000000"),
                    2
            );

            assertThat(result).isZero();
        }

        @Test
        @DisplayName("returns 0 and never goes negative on the last month")
        void lastMonth_neverGoesNegative() {
            // target=10 000 000, saved=9 000 000, monthly=2 000 000
            // remaining=1 000 000, calculatedLeft=0.5, oldMonthLeft=1
            // diff = floor(1 - 0.5) = 0 → newLeft = 1 - 1 = 0
            int result = SettlementEngine.recalculateMonthLeft(
                    new BigDecimal("10000000"),
                    new BigDecimal("9000000"),
                    new BigDecimal("2000000"),
                    1
            );

            assertThat(result).isZero();
        }

        @Test
        @DisplayName("returns max(0, oldMonthLeft - 1) when monthlySaving is zero (edge case)")
        void zeroMonthlySaving_decrementsBy1() {
            int result = SettlementEngine.recalculateMonthLeft(
                    new BigDecimal("10000000"),
                    new BigDecimal("1000000"),
                    BigDecimal.ZERO,
                    4
            );

            assertThat(result).isEqualTo(3);
        }

        @Test
        @DisplayName("returns 0 when monthlySaving is zero and oldMonthLeft is already 0")
        void zeroMonthlySaving_alreadyAtZero_staysZero() {
            int result = SettlementEngine.recalculateMonthLeft(
                    new BigDecimal("10000000"),
                    new BigDecimal("1000000"),
                    BigDecimal.ZERO,
                    0
            );

            assertThat(result).isZero();
        }

        @Test
        @DisplayName("handles very small monthly saving relative to remaining amount")
        void verySmallMonthlySaving_stilDecrementsGracefully() {
            // target=10 000 000, saved=100, monthly=1
            // remaining≈10 000 000, calculatedLeft≈9 999 900, oldMonthLeft=5
            // diff = floor(5 - 9999900) < 0 → newLeft = 5 - 1 = 4
            int result = SettlementEngine.recalculateMonthLeft(
                    new BigDecimal("10000000"),
                    new BigDecimal("100"),
                    new BigDecimal("1"),
                    5
            );

            assertThat(result).isEqualTo(4);
        }
    }
}
