package com.example.smartmoney.domain.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The URGENT / RELAXED plan behind both the project-creation screen
 * ({@code ProjectBudgetAdvisorUseCase}) and the chatbot's create flow. They must agree: a chat
 * that quotes 12 months while the screen would say 6 is describing a project the backend is not
 * going to build.
 */
@DisplayName("ProjectPacePlanner")
class ProjectPacePlannerTest {

    @Test
    @DisplayName("URGENT spends the whole free capacity, so it finishes in the fewest months")
    void urgentUsesFullCapacity() {
        var plan = ProjectPacePlanner.plan(
                BigDecimal.valueOf(120_000_000), BigDecimal.valueOf(20_000_000),
                ProjectPacePlanner.Mode.URGENT);

        assertThat(plan.months()).isEqualTo(6);
        assertThat(plan.monthlySaving()).isEqualByComparingTo("20000000");
    }

    @Test
    @DisplayName("RELAXED spends half of it, so the same goal takes twice as long")
    void relaxedUsesHalfCapacity() {
        var plan = ProjectPacePlanner.plan(
                BigDecimal.valueOf(120_000_000), BigDecimal.valueOf(20_000_000),
                ProjectPacePlanner.Mode.RELAXED);

        assertThat(plan.months()).isEqualTo(12);
        assertThat(plan.monthlySaving()).isEqualByComparingTo("10000000");
    }

    @Test
    @DisplayName("the horizon is capped at 60 months for BOTH modes")
    void bothModesAreCapped() {
        // 100M against 100k/month is 1000 months of saving — far past the ceiling.
        BigDecimal target = BigDecimal.valueOf(100_000_000);
        BigDecimal tiny = BigDecimal.valueOf(100_000);

        var urgent = ProjectPacePlanner.plan(target, tiny, ProjectPacePlanner.Mode.URGENT);
        var relaxed = ProjectPacePlanner.plan(target, tiny, ProjectPacePlanner.Mode.RELAXED);

        assertThat(urgent.months()).isEqualTo(ProjectPacePlanner.MAX_MONTHS);
        assertThat(relaxed.months()).isEqualTo(ProjectPacePlanner.MAX_MONTHS);
        // Capping only the relaxed plan used to let a squeezed budget push the urgent plan out
        // PAST the relaxed one, which is the opposite of what the two words mean.
        assertThat(urgent.months()).isLessThanOrEqualTo(relaxed.months());
    }

    @Test
    @DisplayName("a capacity too small to halve falls back to the full amount rather than dividing by zero")
    void tinyCapacityFallsBackToFull() {
        var plan = ProjectPacePlanner.plan(
                BigDecimal.valueOf(1_000_000), new BigDecimal("0.01"),
                ProjectPacePlanner.Mode.RELAXED);

        assertThat(plan.months()).isEqualTo(ProjectPacePlanner.MAX_MONTHS);
    }

    @Test
    @DisplayName("the monthly amount always covers the target — rounding never leaves a shortfall")
    void monthlyAmountCoversTheTarget() {
        var plan = ProjectPacePlanner.plan(
                BigDecimal.valueOf(100_000_000), BigDecimal.valueOf(7_000_000),
                ProjectPacePlanner.Mode.URGENT);

        assertThat(plan.monthlySaving().multiply(BigDecimal.valueOf(plan.months())))
                .isGreaterThanOrEqualTo(BigDecimal.valueOf(100_000_000));
    }

    @Test
    @DisplayName("no capacity left is a caller error, not a 0-month plan")
    void exhaustedCapacityIsRejected() {
        assertThatThrownBy(() -> ProjectPacePlanner.plan(
                BigDecimal.valueOf(1_000_000), BigDecimal.ZERO, ProjectPacePlanner.Mode.URGENT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
