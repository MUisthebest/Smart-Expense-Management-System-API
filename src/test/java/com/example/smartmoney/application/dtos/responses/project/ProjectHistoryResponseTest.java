package com.example.smartmoney.application.dtos.responses.project;

import com.example.smartmoney.domain.models.project.ProjectHistory;
import com.example.smartmoney.domain.models.project.valueobjects.ProjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProjectHistoryResponse.fromDomain — outcome classification")
class ProjectHistoryResponseTest {

    private static ProjectHistory snapshot(BigDecimal before, BigDecimal after,
                                           BigDecimal penalty, BigDecimal surplus) {
        return ProjectHistory.createSnapshot(
                ProjectId.newId(), UUID.randomUUID(), 5, 2026,
                before, after, new BigDecimal("100.00"),
                penalty, surplus, 6, 5);
    }

    @Test
    @DisplayName("penalty > 0 → OVERSPENT")
    void overspent() {
        ProjectHistoryResponse r = ProjectHistoryResponse.fromDomain(
                snapshot(new BigDecimal("500"), new BigDecimal("500"),
                        new BigDecimal("160"), BigDecimal.ZERO));
        assertThat(r.getOutcome()).isEqualTo(HistoryOutcome.OVERSPENT);
    }

    @Test
    @DisplayName("surplus invested with no penalty → UNDERSPENT_BONUS")
    void underspentBonus() {
        ProjectHistoryResponse r = ProjectHistoryResponse.fromDomain(
                snapshot(new BigDecimal("500"), new BigDecimal("650"),
                        BigDecimal.ZERO, new BigDecimal("90")));
        assertThat(r.getOutcome()).isEqualTo(HistoryOutcome.UNDERSPENT_BONUS);
    }

    @Test
    @DisplayName("savings grew, no penalty/surplus → CLEAN_MONTH")
    void cleanMonth() {
        ProjectHistoryResponse r = ProjectHistoryResponse.fromDomain(
                snapshot(new BigDecimal("500"), new BigDecimal("600"),
                        BigDecimal.ZERO, BigDecimal.ZERO));
        assertThat(r.getOutcome()).isEqualTo(HistoryOutcome.CLEAN_MONTH);
    }

    @Test
    @DisplayName("savings unchanged (frozen) → FROZEN_NO_SAVING")
    void frozenNoSaving() {
        ProjectHistoryResponse r = ProjectHistoryResponse.fromDomain(
                snapshot(new BigDecimal("500"), new BigDecimal("500"),
                        BigDecimal.ZERO, BigDecimal.ZERO));
        assertThat(r.getOutcome()).isEqualTo(HistoryOutcome.FROZEN_NO_SAVING);
    }

    @Test
    @DisplayName("netChange = after − before")
    void netChange() {
        ProjectHistoryResponse r = ProjectHistoryResponse.fromDomain(
                snapshot(new BigDecimal("500"), new BigDecimal("600"),
                        BigDecimal.ZERO, BigDecimal.ZERO));
        assertThat(r.getNetChange()).isEqualByComparingTo("100");
    }
}
