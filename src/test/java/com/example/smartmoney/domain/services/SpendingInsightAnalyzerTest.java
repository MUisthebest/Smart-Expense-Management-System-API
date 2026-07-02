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

    private Transaction expense(Category category, int year, int month, BigDecimal amount, String description) {
        return Transaction.create(userId, new Amount(amount), TransactionType.EXPENSE, category,
                description, LocalDateTime.of(year, month, 10, 12, 0));
    }

    private Transaction income(int year, int month, BigDecimal amount) {
        return Transaction.create(userId, new Amount(amount), TransactionType.INCOME, Category.OTHER,
                "salary", LocalDateTime.of(year, month, 5, 12, 0));
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

    private Optional<Insight> paceOn(Budget budget, LocalDate asOf) {
        return analyzer.analyze(List.of(), List.of(budget), asOf).stream()
                .filter(i -> i.getType() == InsightType.BUDGET_PACE).findFirst();
    }

    @Test
    @DisplayName("early-month guard: days 1–2 produce no pace insight (one purchase is not a trend)")
    void earlyMonthNoPace() {
        // 100 spent on day 1 would linearly project to 3000 (>> 1000 limit) — the exact day-1 blowup
        // the guard exists to suppress. It must stay silent on days 1 and 2.
        Budget b = budget(Category.FOOD, new BigDecimal("1000"), new BigDecimal("100"));
        assertTrue(paceOn(b, LocalDate.of(2026, 6, 1)).isEmpty());
        assertTrue(paceOn(b, LocalDate.of(2026, 6, 2)).isEmpty());
    }

    @Test
    @DisplayName("early-month guard: on day 3 the projection is trusted again (and still no divide-by-zero)")
    void paceResumesOnMinDay() {
        // day 3 of 30: projected = 100/3*30 = 1000 → exactly at the limit → not over → no insight,
        // but a clear overshoot does surface, proving the guard opened rather than the math breaking.
        Insight insight = paceOn(budget(Category.FOOD, new BigDecimal("1000"), new BigDecimal("200")),
                LocalDate.of(2026, 6, 3)).orElseThrow();
        // projected = 200/3*30 = 2000 → ratio 2.0 → CRITICAL
        assertEquals(Severity.CRITICAL, insight.getSeverity());
        assertEquals(2000.0, ((BigDecimal) insight.getMetrics().get("projectedEndOfMonth")).doubleValue(), 0.001);
    }

    // ---- P3-B: exponential-smoothing forecast --------------------------------------------

    private final SpendingInsightAnalyzer expAnalyzer =
            new SpendingInsightAnalyzer(ForecastMethod.EXP_SMOOTHING);

    private Transaction expenseOn(Category category, int year, int month, int day, BigDecimal amount) {
        return Transaction.create(userId, new Amount(amount), TransactionType.EXPENSE, category,
                "test", LocalDateTime.of(year, month, day, 12, 0));
    }

    private Optional<Insight> expPace(List<Transaction> history, Budget budget) {
        return expAnalyzer.analyze(history, List.of(budget), AS_OF).stream()
                .filter(i -> i.getType() == InsightType.BUDGET_PACE)
                .findFirst();
    }

    @Test
    @DisplayName("exp-smoothing: a single early spike no longer projects an overshoot (linear would WARN)")
    void expSmoothingDampensEarlySpike() {
        // 600 spent, all on day 2 of a 30-day month; nothing since. By day 15 the burn rate has decayed.
        Budget budget = budget(Category.FOOD, new BigDecimal("1000"), new BigDecimal("600"));
        List<Transaction> history = new ArrayList<>();
        history.add(expenseOn(Category.FOOD, 2026, 6, 2, new BigDecimal("600")));

        // Control: the linear method extrapolates the spike to 1200 → WARN.
        assertEquals(Severity.WARN, pace(budget).orElseThrow().getSeverity());
        // Treatment: exp-smoothing sees the spike as a one-off → no overshoot, no insight.
        assertTrue(expPace(history, budget).isEmpty());
    }

    @Test
    @DisplayName("exp-smoothing: sustained daily spend still projects the overshoot (~matches linear)")
    void expSmoothingTracksSustainedSpend() {
        // 40/day for the first 15 days = 600 spent; smoothed rate 40 → projected 600 + 40×15 = 1200.
        Budget budget = budget(Category.FOOD, new BigDecimal("1000"), new BigDecimal("600"));
        List<Transaction> history = new ArrayList<>();
        for (int day = 1; day <= 15; day++) {
            history.add(expenseOn(Category.FOOD, 2026, 6, day, new BigDecimal("40")));
        }

        Insight insight = expPace(history, budget).orElseThrow();
        assertEquals(Severity.WARN, insight.getSeverity());
        assertEquals("EXP_SMOOTHING", insight.getMetrics().get("forecastMethod"));
        assertEquals(1200.0, ((BigDecimal) insight.getMetrics().get("projectedEndOfMonth")).doubleValue(), 0.001);
        assertEquals(40.0, ((BigDecimal) insight.getMetrics().get("smoothedDailyRate")).doubleValue(), 0.001);
    }

    @Test
    @DisplayName("exp-smoothing falls back to linear when there is no daily series (budget-only spend)")
    void expSmoothingFallsBackWithoutDailyData() {
        // spent tracked only on the budget; no transactions to derive a daily rate from.
        Insight insight = expPace(List.of(),
                budget(Category.FOOD, new BigDecimal("1000"), new BigDecimal("600"))).orElseThrow();
        assertEquals(Severity.WARN, insight.getSeverity());
        // projected = 600/15×30 = 1200, and the method is honestly reported as LINEAR.
        assertEquals(1200.0, ((BigDecimal) insight.getMetrics().get("projectedEndOfMonth")).doubleValue(), 0.001);
        assertEquals("LINEAR", insight.getMetrics().get("forecastMethod"));
        assertNull(insight.getMetrics().get("smoothedDailyRate"));
    }

    @Test
    @DisplayName("linear method labels its forecast LINEAR in the metrics")
    void linearForecastIsLabelled() {
        Insight insight = pace(budget(Category.FOOD, new BigDecimal("1000"), new BigDecimal("600"))).orElseThrow();
        assertEquals("LINEAR", insight.getMetrics().get("forecastMethod"));
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

    // ---- P2-C: category trend ------------------------------------------------------------

    private Optional<Insight> trend(List<Transaction> history) {
        return analyzer.analyze(history, List.of(), AS_OF).stream()
                .filter(i -> i.getType() == InsightType.CATEGORY_TREND)
                .findFirst();
    }

    @Test
    @DisplayName("steadily rising category (~13%/mo) → WARN trend")
    void risingTrendIsWarn() {
        List<Transaction> history = new ArrayList<>();
        int[] amounts = {100, 120, 140, 160, 180, 200}; // Jan..Jun, slope 20 / mean 150 = 0.133
        for (int i = 0; i < amounts.length; i++) {
            history.add(expense(Category.FOOD, 2026, i + 1, new BigDecimal(amounts[i])));
        }
        Insight insight = trend(history).orElseThrow();
        assertEquals(Severity.WARN, insight.getSeverity());
        assertEquals("insight.category_trend.food", insight.getCode());
    }

    @Test
    @DisplayName("sharply rising category (~40%/mo) → CRITICAL trend")
    void sharpTrendIsCritical() {
        List<Transaction> history = new ArrayList<>();
        int[] amounts = {100, 200, 300, 400}; // Mar..Jun, slope 100 / mean 250 = 0.4
        for (int i = 0; i < amounts.length; i++) {
            history.add(expense(Category.FOOD, 2026, i + 3, new BigDecimal(amounts[i])));
        }
        assertEquals(Severity.CRITICAL, trend(history).orElseThrow().getSeverity());
    }

    @Test
    @DisplayName("flat category → no trend")
    void flatTrendNone() {
        List<Transaction> history = new ArrayList<>();
        for (int m = 3; m <= 6; m++) {
            history.add(expense(Category.FOOD, 2026, m, new BigDecimal("100")));
        }
        assertTrue(trend(history).isEmpty());
    }

    @Test
    @DisplayName("declining category → no trend (only upward creep is surfaced)")
    void decliningTrendNone() {
        List<Transaction> history = new ArrayList<>();
        history.add(expense(Category.FOOD, 2026, 4, new BigDecimal("300")));
        history.add(expense(Category.FOOD, 2026, 5, new BigDecimal("200")));
        history.add(expense(Category.FOOD, 2026, 6, new BigDecimal("100")));
        assertTrue(trend(history).isEmpty());
    }

    @Test
    @DisplayName("fewer than 3 months → no trend")
    void shortSeriesNoTrend() {
        List<Transaction> history = new ArrayList<>();
        history.add(expense(Category.FOOD, 2026, 5, new BigDecimal("100")));
        history.add(expense(Category.FOOD, 2026, 6, new BigDecimal("300")));
        assertTrue(trend(history).isEmpty());
    }

    // ---- P2-C: recurring -----------------------------------------------------------------

    private List<Insight> recurring(List<Transaction> history) {
        return analyzer.analyze(history, List.of(), AS_OF).stream()
                .filter(i -> i.getType() == InsightType.RECURRING)
                .toList();
    }

    @Test
    @DisplayName("near-equal charge across 3 months → recurring (INFO)")
    void recurringDetected() {
        List<Transaction> history = new ArrayList<>();
        history.add(expense(Category.ENTERTAINMENT, 2026, 4, new BigDecimal("200"), "Netflix"));
        history.add(expense(Category.ENTERTAINMENT, 2026, 5, new BigDecimal("210"), "Netflix"));
        history.add(expense(Category.ENTERTAINMENT, 2026, 6, new BigDecimal("190"), "Netflix"));

        List<Insight> found = recurring(history);
        assertEquals(1, found.size());
        Insight insight = found.get(0);
        assertEquals(Severity.INFO, insight.getSeverity());
        assertEquals(Category.ENTERTAINMENT, insight.getCategory());
        assertEquals(3, insight.getMetrics().get("monthsObserved"));
        // median of 200/210/190 = 200
        assertEquals(0, new BigDecimal("200.00").compareTo(
                (BigDecimal) insight.getMetrics().get("monthlyAmount")));
    }

    @Test
    @DisplayName("widely varying amounts → not recurring")
    void varyingAmountsNotRecurring() {
        List<Transaction> history = new ArrayList<>();
        history.add(expense(Category.SHOPPING, 2026, 4, new BigDecimal("100"), "Amazon"));
        history.add(expense(Category.SHOPPING, 2026, 5, new BigDecimal("200"), "Amazon"));
        history.add(expense(Category.SHOPPING, 2026, 6, new BigDecimal("400"), "Amazon"));
        assertTrue(recurring(history).isEmpty());
    }

    @Test
    @DisplayName("charges without a description cannot be identified as recurring")
    void blankDescriptionNotRecurring() {
        List<Transaction> history = new ArrayList<>();
        for (int m = 4; m <= 6; m++) {
            history.add(Transaction.create(userId, new Amount(new BigDecimal("200")),
                    TransactionType.EXPENSE, Category.ENTERTAINMENT, "  ",
                    LocalDateTime.of(2026, m, 10, 12, 0)));
        }
        assertTrue(recurring(history).isEmpty());
    }

    @Test
    @DisplayName("estimatedMonthlyRecurringCost sums every detected recurring charge")
    void estimatedRecurringCostSums() {
        List<Transaction> history = new ArrayList<>();
        for (int m = 4; m <= 6; m++) {
            history.add(expense(Category.ENTERTAINMENT, 2026, m, new BigDecimal("200"), "Netflix"));
            history.add(expense(Category.HEALTH, 2026, m, new BigDecimal("50"), "Gym"));
        }
        assertEquals(0, new BigDecimal("250.00").compareTo(
                analyzer.estimatedMonthlyRecurringCost(history, PERIOD)));
    }

    // ---- P2-C: savings capacity ----------------------------------------------------------

    private Optional<Insight> capacity(List<Transaction> history, List<Budget> budgets) {
        return analyzer.analyze(history, budgets, AS_OF).stream()
                .filter(i -> i.getType() == InsightType.SAVINGS_CAPACITY)
                .findFirst();
    }

    @Test
    @DisplayName("income beyond recurring + budgets → positive savings capacity (INFO)")
    void positiveCapacity() {
        List<Transaction> history = new ArrayList<>();
        for (int m = 4; m <= 6; m++) {
            history.add(income(2026, m, new BigDecimal("5000")));
            history.add(expense(Category.ENTERTAINMENT, 2026, m, new BigDecimal("200"), "Netflix"));
        }
        List<Budget> budgets = List.of(Budget.builder().id(UUID.randomUUID()).userId(userId)
                .category(Category.FOOD).amountLimit(new BigDecimal("1000")).month(6).year(2026).build());

        Insight insight = capacity(history, budgets).orElseThrow();
        assertEquals(Severity.INFO, insight.getSeverity());
        assertNull(insight.getCategory());
        // disposable = 5000 - 200 (recurring) - 1000 (budget) = 3800
        assertEquals(0, new BigDecimal("3800.00").compareTo(
                (BigDecimal) insight.getMetrics().get("disposable")));
    }

    @Test
    @DisplayName("no income signal → no savings capacity")
    void noIncomeNoCapacity() {
        List<Transaction> history = new ArrayList<>();
        history.add(expense(Category.FOOD, 2026, 6, new BigDecimal("100")));
        assertTrue(capacity(history, List.of()).isEmpty());
    }

    @Test
    @DisplayName("committed beyond income → no capacity")
    void overCommittedNoCapacity() {
        List<Transaction> history = new ArrayList<>();
        history.add(income(2026, 6, new BigDecimal("1000")));
        List<Budget> budgets = List.of(Budget.builder().id(UUID.randomUUID()).userId(userId)
                .category(Category.FOOD).amountLimit(new BigDecimal("2000")).month(6).year(2026).build());
        assertTrue(capacity(history, budgets).isEmpty());
    }

    // ---- soft budget envelope (budget limits vs declared income) -------------------------

    @Test
    @DisplayName("budget limits over declared income → WARN, account-wide, with over-by metrics")
    void budgetsOverIncomeWarn() {
        List<Budget> budgets = List.of(
                budget(Category.FOOD, new BigDecimal("8000"), BigDecimal.ZERO),
                budget(Category.SHOPPING, new BigDecimal("5000"), BigDecimal.ZERO));

        Insight insight = analyzer.detectBudgetOverIncome(budgets, new BigDecimal("10000"), PERIOD)
                .orElseThrow();
        assertEquals(InsightType.BUDGET_OVER_INCOME, insight.getType());
        assertEquals(Severity.WARN, insight.getSeverity());
        assertNull(insight.getCategory()); // account-wide
        assertEquals("insight.budget_over_income", insight.getCode());
        // committed 13000 vs income 10000 → overBy 3000, ratio 1.30
        assertEquals(0, new BigDecimal("3000.00").compareTo(
                (BigDecimal) insight.getMetrics().get("overBy")));
        assertEquals(1.30, insight.getMetrics().get("overshootRatio"));
    }

    @Test
    @DisplayName("budget limits within income → no envelope insight")
    void budgetsWithinIncomeNone() {
        List<Budget> budgets = List.of(budget(Category.FOOD, new BigDecimal("4000"), BigDecimal.ZERO));
        assertTrue(analyzer.detectBudgetOverIncome(budgets, new BigDecimal("10000"), PERIOD).isEmpty());
    }

    @Test
    @DisplayName("budget total exactly equal to income → within the envelope (no insight)")
    void budgetsEqualIncomeNone() {
        List<Budget> budgets = List.of(budget(Category.FOOD, new BigDecimal("10000"), BigDecimal.ZERO));
        assertTrue(analyzer.detectBudgetOverIncome(budgets, new BigDecimal("10000"), PERIOD).isEmpty());
    }

    @Test
    @DisplayName("income unset (cold start) → no envelope insight even when budgets exist")
    void budgetsNoIncomeNone() {
        List<Budget> budgets = List.of(budget(Category.FOOD, new BigDecimal("9999"), BigDecimal.ZERO));
        assertTrue(analyzer.detectBudgetOverIncome(budgets, null, PERIOD).isEmpty());
        assertTrue(analyzer.detectBudgetOverIncome(budgets, BigDecimal.ZERO, PERIOD).isEmpty());
    }
}
