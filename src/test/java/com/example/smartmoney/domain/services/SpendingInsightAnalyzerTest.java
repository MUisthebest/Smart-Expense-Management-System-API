package com.example.smartmoney.domain.services;

import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.insight.Insight;
import com.example.smartmoney.domain.models.insight.InsightType;
import com.example.smartmoney.domain.models.insight.Severity;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.valueobjects.Amount;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpendingInsightAnalyzer — deterministic insight core")
class SpendingInsightAnalyzerTest {

    private final SpendingInsightAnalyzer analyzer = new SpendingInsightAnalyzer();
    private final UUID userId = UUID.randomUUID();

    // asOf in the middle of a 30-day month (June 2026) keeps pace math clean.
    private static final LocalDate AS_OF = LocalDate.of(2026, 6, 15);
    private static final YearMonth PERIOD = YearMonth.of(2026, 6);

    private Transaction expense(Category category, int year, int month, BigDecimal amount) {
        return Transaction.create(userId, new Amount(amount), TransactionType.EXPENSE, category,
                "test", LocalDateTime.of(year, month, 10, 12, 0));
    }

    /** Four trailing months [80,120,80,120] → mean 100, population stddev 20. */
    private List<Transaction> trailingBaseline(Category category) {
        List<Transaction> history = new ArrayList<>();
        history.add(expense(category, 2026, 5, new BigDecimal("80")));
        history.add(expense(category, 2026, 4, new BigDecimal("120")));
        history.add(expense(category, 2026, 3, new BigDecimal("80")));
        history.add(expense(category, 2026, 2, new BigDecimal("120")));
        return history;
    }

    private Optional<Insight> overspend(List<Transaction> history) {
        return analyzer.analyze(history, List.of(), AS_OF).stream()
                .filter(i -> i.getType() == InsightType.OVERSPEND_ANOMALY)
                .findFirst();
    }

    private Budget budget(Category category, BigDecimal limit, BigDecimal spent) {
        return Budget.builder()
                .id(UUID.randomUUID()).userId(userId).category(category)
                .amountLimit(limit).spent(spent).month(6).year(2026)
                .build();
    }

    // ---- overspend anomaly ---------------------------------------------------------------

    @Test
    @DisplayName("normal spend near the baseline → INFO only")
    void normalSpendIsInfo() {
        List<Transaction> history = trailingBaseline(Category.FOOD);
        history.add(expense(Category.FOOD, 2026, 6, new BigDecimal("110"))); // z = 0.5
        Insight insight = overspend(history).orElseThrow();
        assertEquals(Severity.INFO, insight.getSeverity());
        assertEquals(Category.FOOD, insight.getCategory());
        assertEquals("insight.overspend_anomaly.food", insight.getCode());
    }

    @Test
    @DisplayName("~2σ spike → WARN")
    void twoSigmaSpikeIsWarn() {
        List<Transaction> history = trailingBaseline(Category.FOOD);
        history.add(expense(Category.FOOD, 2026, 6, new BigDecimal("145"))); // z = 2.25
        assertEquals(Severity.WARN, overspend(history).orElseThrow().getSeverity());
    }

    @Test
    @DisplayName("~3σ spike → CRITICAL")
    void threeSigmaSpikeIsCritical() {
        List<Transaction> history = trailingBaseline(Category.FOOD);
        history.add(expense(Category.FOOD, 2026, 6, new BigDecimal("170"))); // z = 3.5
        assertEquals(Severity.CRITICAL, overspend(history).orElseThrow().getSeverity());
    }

    @Test
    @DisplayName("fewer than 3 months of data → no anomaly (cold start)")
    void coldStartProducesNoAnomaly() {
        List<Transaction> history = new ArrayList<>();
        history.add(expense(Category.FOOD, 2026, 5, new BigDecimal("80")));
        history.add(expense(Category.FOOD, 2026, 4, new BigDecimal("120")));
        history.add(expense(Category.FOOD, 2026, 6, new BigDecimal("500")));
        assertTrue(overspend(history).isEmpty());
    }

    @Test
    @DisplayName("zero variance baseline → no insight (no divide-by-zero)")
    void zeroVarianceProducesNoAnomaly() {
        List<Transaction> history = new ArrayList<>();
        history.add(expense(Category.FOOD, 2026, 5, new BigDecimal("100")));
        history.add(expense(Category.FOOD, 2026, 4, new BigDecimal("100")));
        history.add(expense(Category.FOOD, 2026, 3, new BigDecimal("100")));
        history.add(expense(Category.FOOD, 2026, 6, new BigDecimal("999")));
        assertTrue(overspend(history).isEmpty());
    }

    // ---- budget pace ---------------------------------------------------------------------

    private Optional<Insight> pace(Budget budget) {
        return analyzer.analyze(List.of(), List.of(budget), AS_OF).stream()
                .filter(i -> i.getType() == InsightType.BUDGET_PACE)
                .findFirst();
    }

    @Test
    @DisplayName("on track under the limit → no pace insight")
    void underLimitNoPace() {
        assertTrue(pace(budget(Category.FOOD, new BigDecimal("1000"), new BigDecimal("10"))).isEmpty());
    }

    @Test
    @DisplayName("projected to exceed (<1.25x) → WARN")
    void onTrackToExceedIsWarn() {
        // spent 600 by day 15 of 30 → projected 1200, ratio 1.2
        assertEquals(Severity.WARN,
                pace(budget(Category.FOOD, new BigDecimal("1000"), new BigDecimal("600"))).orElseThrow().getSeverity());
    }

    @Test
    @DisplayName("projected way over (>=1.25x) → CRITICAL")
    void wayOverIsCritical() {
        // spent 700 by day 15 of 30 → projected 1400, ratio 1.4
        assertEquals(Severity.CRITICAL,
                pace(budget(Category.FOOD, new BigDecimal("1000"), new BigDecimal("700"))).orElseThrow().getSeverity());
    }

    @Test
    @DisplayName("day-1 projection does not divide by zero")
    void dayOneEdge() {
        Insight insight = analyzer.analyze(List.of(),
                        List.of(budget(Category.FOOD, new BigDecimal("1000"), new BigDecimal("100"))),
                        LocalDate.of(2026, 6, 1)).stream()
                .filter(i -> i.getType() == InsightType.BUDGET_PACE).findFirst().orElseThrow();
        // projected = 100/1*30 = 3000 → CRITICAL
        assertEquals(Severity.CRITICAL, insight.getSeverity());
        assertEquals(3000.0, ((BigDecimal) insight.getMetrics().get("projectedEndOfMonth")).doubleValue(), 0.001);
    }

    // ---- trailing-3-month median ---------------------------------------------------------

    @Test
    @DisplayName("median over 3 months (odd count)")
    void medianOddCount() {
        List<Transaction> history = new ArrayList<>();
        history.add(expense(Category.FOOD, 2026, 5, new BigDecimal("300")));
        history.add(expense(Category.FOOD, 2026, 4, new BigDecimal("100")));
        history.add(expense(Category.FOOD, 2026, 3, new BigDecimal("200")));
        assertEquals(0, new BigDecimal("200.00").compareTo(
                analyzer.trailing3MonthMedian(history, Category.FOOD, PERIOD)));
    }

    @Test
    @DisplayName("median over 2 months (even count) → average of the two")
    void medianEvenCount() {
        List<Transaction> history = new ArrayList<>();
        history.add(expense(Category.FOOD, 2026, 5, new BigDecimal("300")));
        history.add(expense(Category.FOOD, 2026, 4, new BigDecimal("100")));
        assertEquals(0, new BigDecimal("200.00").compareTo(
                analyzer.trailing3MonthMedian(history, Category.FOOD, PERIOD)));
    }

    @Test
    @DisplayName("single big outlier is ignored by the median")
    void medianIgnoresOutlier() {
        List<Transaction> history = new ArrayList<>();
        history.add(expense(Category.FOOD, 2026, 5, new BigDecimal("100")));
        history.add(expense(Category.FOOD, 2026, 4, new BigDecimal("100")));
        history.add(expense(Category.FOOD, 2026, 3, new BigDecimal("10000")));
        assertEquals(0, new BigDecimal("100.00").compareTo(
                analyzer.trailing3MonthMedian(history, Category.FOOD, PERIOD)));
    }

    @Test
    @DisplayName("no prior data → median is zero")
    void medianNoData() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                analyzer.trailing3MonthMedian(List.of(), Category.FOOD, PERIOD)));
    }
}
