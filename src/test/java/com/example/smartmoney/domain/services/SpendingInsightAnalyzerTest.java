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

    // ---- recurring cost by category (feeds the deterministic allocator) ------------------

    @Test
    @DisplayName("recurringCostByCategory returns the per-category monthly recurring amount")
    void recurringCostByCategoryGroupsPerCategory() {
        List<Transaction> history = new ArrayList<>();
        // A stable Netflix charge in UTILITIES across 3 months → recurring.
        history.add(expense(Category.UTILITIES, 2026, 3, new BigDecimal("200000"), "netflix"));
        history.add(expense(Category.UTILITIES, 2026, 4, new BigDecimal("200000"), "netflix"));
        history.add(expense(Category.UTILITIES, 2026, 5, new BigDecimal("200000"), "netflix"));
        // A one-off FOOD charge → not recurring, must be absent.
        history.add(expense(Category.FOOD, 2026, 5, new BigDecimal("999000"), "dinner"));

        var byCategory = analyzer.recurringCostByCategory(history, PERIOD);

        assertEquals(0, new BigDecimal("200000").compareTo(byCategory.get(Category.UTILITIES)));
        assertFalse(byCategory.containsKey(Category.FOOD));
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
    @DisplayName("early-month guard: on day 3 the projection resumes, credibility-weighted (and still no divide-by-zero)")
    void paceResumesOnMinDay() {
        // The guard opens on day 3, but three days is still a thin sample, so the observed rate carries
        // only w = 3/30 of the weight and the budget's own allowance carries the rest:
        //   rate = 0.1 × (200/3) + 0.9 × (1000/30) = 36.67 → projected = 200 + 27 × 36.67 = 1190
        // The raw extrapolation would have claimed 2000 — twice the budget off three days of data.
        Insight insight = paceOn(budget(Category.FOOD, new BigDecimal("1000"), new BigDecimal("200")),
                LocalDate.of(2026, 6, 3)).orElseThrow();
        assertEquals(Severity.WARN, insight.getSeverity()); // ratio 1.19, not the old 2.0
        assertEquals(1190.0, ((BigDecimal) insight.getMetrics().get("projectedEndOfMonth")).doubleValue(), 0.001);
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
        // Half the month observed, so the smoothed 40/day is blended half-and-half with the budget's
        // own 33.33/day allowance: rate = 36.67 → 600 + 15 × 36.67 = 1150. Below the raw 1200 trend by
        // design — credibility weighting trades some responsiveness for not blowing up on thin data —
        // and the smoothedDailyRate metric still reports the unshrunk 40 it measured.
        assertEquals(1150.0, ((BigDecimal) insight.getMetrics().get("projectedEndOfMonth")).doubleValue(), 0.001);
        assertEquals(40.0, ((BigDecimal) insight.getMetrics().get("smoothedDailyRate")).doubleValue(), 0.001);
    }

    @Test
    @DisplayName("exp-smoothing falls back to linear when there is no daily series (budget-only spend)")
    void expSmoothingFallsBackWithoutDailyData() {
        // spent tracked only on the budget; no transactions to derive a daily rate from.
        Insight insight = expPace(List.of(),
                budget(Category.FOOD, new BigDecimal("1000"), new BigDecimal("600"))).orElseThrow();
        assertEquals(Severity.WARN, insight.getSeverity());
        // rate = 0.5 × (600/15) + 0.5 × (1000/30) = 36.67 → 600 + 15 × 36.67 = 1150, and the method is
        // honestly reported as LINEAR.
        assertEquals(1150.0, ((BigDecimal) insight.getMetrics().get("projectedEndOfMonth")).doubleValue(), 0.001);
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

    // ---- Wave 1: budget underspend -------------------------------------------------------

    private Optional<Insight> underspend(List<Transaction> history, Budget budget) {
        return analyzer.analyze(history, List.of(budget), AS_OF).stream()
                .filter(i -> i.getType() == InsightType.BUDGET_UNDERSPEND)
                .findFirst();
    }

    /** Prior months [100,120,80] → trailing-3 median 100 (well under a 1000 limit). */
    private List<Transaction> lowFoodHistory() {
        List<Transaction> history = new ArrayList<>();
        history.add(expense(Category.FOOD, 2026, 5, new BigDecimal("100")));
        history.add(expense(Category.FOOD, 2026, 4, new BigDecimal("120")));
        history.add(expense(Category.FOOD, 2026, 3, new BigDecimal("80")));
        return history;
    }

    @Test
    @DisplayName("history + pace both well under the limit → INFO underspend with median metric")
    void underspendDetected() {
        // spent 100 by day 15 of 30 → projected 200; threshold 0.6×1000 = 600.
        Budget b = budget(Category.FOOD, new BigDecimal("1000"), new BigDecimal("100"));
        Insight insight = underspend(lowFoodHistory(), b).orElseThrow();
        assertEquals(Severity.INFO, insight.getSeverity());
        assertEquals(Category.FOOD, insight.getCategory());
        assertEquals("insight.budget_underspend.food", insight.getCode());
        // median of [100,120,80] = 100
        assertEquals(0, new BigDecimal("100.00").compareTo(
                (BigDecimal) insight.getMetrics().get("medianActual")));
    }

    @Test
    @DisplayName("trailing spend near the limit → no underspend")
    void underspendNotWhenHistoryNearLimit() {
        List<Transaction> history = new ArrayList<>();
        for (int m = 3; m <= 5; m++) {
            history.add(expense(Category.FOOD, 2026, m, new BigDecimal("700"))); // median 700 > 600
        }
        assertTrue(underspend(history, budget(Category.FOOD, new BigDecimal("1000"), new BigDecimal("100"))).isEmpty());
    }

    @Test
    @DisplayName("this month is on pace to use the budget → no underspend even with a low history")
    void underspendNotWhenPaceCatchesUp() {
        // spent 400 by day 15 → projected 800 > 600 threshold, despite the low trailing median.
        assertTrue(underspend(lowFoodHistory(), budget(Category.FOOD, new BigDecimal("1000"), new BigDecimal("400"))).isEmpty());
    }

    @Test
    @DisplayName("no spend history → no underspend (never trim a budget the user hasn't used)")
    void underspendNoHistoryNone() {
        assertTrue(underspend(List.of(), budget(Category.FOOD, new BigDecimal("1000"), new BigDecimal("100"))).isEmpty());
    }

    // ---- Wave 1: large transaction (per-charge outlier) ----------------------------------

    private Transaction expenseAmt(Category category, String amount) {
        return Transaction.create(userId, new Amount(new BigDecimal(amount)), TransactionType.EXPENSE, category,
                "charge", LocalDateTime.of(2026, 6, 10, 12, 0));
    }

    /** Four typical ~100 SHOPPING charges (mean 100, non-zero variance). */
    private List<Transaction> typicalShoppingCharges() {
        List<Transaction> history = new ArrayList<>();
        history.add(expenseAmt(Category.SHOPPING, "100"));
        history.add(expenseAmt(Category.SHOPPING, "110"));
        history.add(expenseAmt(Category.SHOPPING, "90"));
        history.add(expenseAmt(Category.SHOPPING, "100"));
        return history;
    }

    @Test
    @DisplayName("an expense far above the category's typical charge → LARGE_TRANSACTION")
    void largeTransactionDetected() {
        Insight insight = analyzer.detectLargeTransaction(typicalShoppingCharges(),
                expenseAmt(Category.SHOPPING, "1000")).orElseThrow();
        assertEquals(InsightType.LARGE_TRANSACTION, insight.getType());
        assertEquals(Category.SHOPPING, insight.getCategory());
        assertEquals("insight.large_transaction.shopping", insight.getCode());
        assertEquals(0, new BigDecimal("1000.00").compareTo((BigDecimal) insight.getMetrics().get("amount")));
    }

    @Test
    @DisplayName("too few prior charges → no large-transaction call")
    void largeTransactionNeedsHistory() {
        List<Transaction> history = List.of(
                expenseAmt(Category.SHOPPING, "100"), expenseAmt(Category.SHOPPING, "110"));
        assertTrue(analyzer.detectLargeTransaction(history, expenseAmt(Category.SHOPPING, "1000")).isEmpty());
    }

    @Test
    @DisplayName("a normal-sized charge within the usual band → no large-transaction call")
    void normalChargeNotLarge() {
        assertTrue(analyzer.detectLargeTransaction(typicalShoppingCharges(),
                expenseAmt(Category.SHOPPING, "105")).isEmpty());
    }

    // ---- Wave 1: duplicate charge --------------------------------------------------------

    private Transaction expenseAt(Category category, int day, int hour, String amount, String description) {
        return Transaction.create(userId, new Amount(new BigDecimal(amount)), TransactionType.EXPENSE, category,
                description, LocalDateTime.of(2026, 6, day, hour, 0));
    }

    @Test
    @DisplayName("same category + amount + description within the window → DUPLICATE_CHARGE (INFO)")
    void duplicateChargeDetected() {
        Transaction earlier = expenseAt(Category.FOOD, 10, 9, "50", "Coffee");
        Transaction candidate = expenseAt(Category.FOOD, 10, 11, "50", "Coffee"); // 2h later
        Insight insight = analyzer.detectDuplicateCharge(List.of(earlier), candidate).orElseThrow();
        assertEquals(InsightType.DUPLICATE_CHARGE, insight.getType());
        assertEquals(Severity.INFO, insight.getSeverity());
        assertEquals("insight.duplicate_charge.food", insight.getCode());
    }

    @Test
    @DisplayName("a different amount is not a duplicate")
    void differentAmountNotDuplicate() {
        Transaction earlier = expenseAt(Category.FOOD, 10, 9, "50", "Coffee");
        Transaction candidate = expenseAt(Category.FOOD, 10, 11, "60", "Coffee");
        assertTrue(analyzer.detectDuplicateCharge(List.of(earlier), candidate).isEmpty());
    }

    @Test
    @DisplayName("an identical charge outside the window is not a duplicate")
    void outsideWindowNotDuplicate() {
        Transaction earlier = expenseAt(Category.FOOD, 5, 12, "50", "Coffee"); // 5 days = 120h > 72h
        Transaction candidate = expenseAt(Category.FOOD, 10, 12, "50", "Coffee");
        assertTrue(analyzer.detectDuplicateCharge(List.of(earlier), candidate).isEmpty());
    }

    // ---- Wave 2: subscription price hike ---------------------------------------------------

    private List<Insight> hikes(List<Transaction> history) {
        return analyzer.analyze(history, List.of(), AS_OF).stream()
                .filter(i -> i.getType() == InsightType.SUBSCRIPTION_PRICE_HIKE)
                .toList();
    }

    /** A stable "Netflix" charge of 200 for {@code months}, then {@code current} this month (June). */
    private List<Transaction> subscriptionHistory(String current) {
        List<Transaction> history = new ArrayList<>();
        for (int m = 3; m <= 5; m++) {
            history.add(expense(Category.ENTERTAINMENT, 2026, m, new BigDecimal("200"), "Netflix"));
        }
        if (current != null) {
            history.add(expense(Category.ENTERTAINMENT, 2026, 6, new BigDecimal(current), "Netflix"));
        }
        return history;
    }

    @Test
    @DisplayName("a stable recurring charge jumping past the tolerance band → WARN price hike, and no RECURRING for the group")
    void priceHikeDetected() {
        List<Transaction> history = subscriptionHistory("260"); // +30% > 15% tolerance
        List<Insight> found = hikes(history);
        assertEquals(1, found.size());
        Insight insight = found.get(0);
        assertEquals(Severity.WARN, insight.getSeverity());
        assertEquals(Category.ENTERTAINMENT, insight.getCategory());
        assertEquals("insight.subscription_price_hike.entertainment", insight.getCode());
        assertEquals("netflix", insight.getMetrics().get("description"));
        assertEquals(0, new BigDecimal("200.00").compareTo(
                (BigDecimal) insight.getMetrics().get("previousAmount")));
        assertEquals(0, new BigDecimal("260.00").compareTo(
                (BigDecimal) insight.getMetrics().get("newAmount")));
        assertEquals(0.30, insight.getMetrics().get("increaseRatio"));
        assertEquals(3, insight.getMetrics().get("monthsObserved"));
        // The hiked month breaks the group's all-months tolerance → the hike replaces RECURRING.
        assertTrue(recurring(history).isEmpty());
    }

    @Test
    @DisplayName("a rise still inside the tolerance band is not a hike (and stays RECURRING)")
    void withinToleranceNotAHike() {
        List<Transaction> history = subscriptionHistory("220"); // +10% ≤ 15%
        assertTrue(hikes(history).isEmpty());
        assertEquals(1, recurring(history).size());
    }

    @Test
    @DisplayName("a price drop is not a hike")
    void priceDropNotAHike() {
        assertTrue(hikes(subscriptionHistory("150")).isEmpty());
    }

    @Test
    @DisplayName("fewer than 3 stable prior months → no hike (never was a subscription)")
    void tooFewPriorMonthsNoHike() {
        List<Transaction> history = new ArrayList<>();
        history.add(expense(Category.ENTERTAINMENT, 2026, 4, new BigDecimal("200"), "Netflix"));
        history.add(expense(Category.ENTERTAINMENT, 2026, 5, new BigDecimal("200"), "Netflix"));
        history.add(expense(Category.ENTERTAINMENT, 2026, 6, new BigDecimal("260"), "Netflix"));
        assertTrue(hikes(history).isEmpty());
    }

    @Test
    @DisplayName("no charge posted this month → nothing new to judge, no hike")
    void noCurrentChargeNoHike() {
        assertTrue(hikes(subscriptionHistory(null)).isEmpty());
    }

    @Test
    @DisplayName("prior months that were never stable → a jump means nothing")
    void unstablePriorMonthsNoHike() {
        List<Transaction> history = new ArrayList<>();
        history.add(expense(Category.SHOPPING, 2026, 3, new BigDecimal("100"), "Amazon"));
        history.add(expense(Category.SHOPPING, 2026, 4, new BigDecimal("200"), "Amazon"));
        history.add(expense(Category.SHOPPING, 2026, 5, new BigDecimal("400"), "Amazon"));
        history.add(expense(Category.SHOPPING, 2026, 6, new BigDecimal("600"), "Amazon"));
        assertTrue(hikes(history).isEmpty());
    }

    // ---- Wave 3: income drop (closed-month, low tail of the income series) ------------------

    /** Income of {@code amounts[i]} in Mar+i 2026; pass June's amount as the judged month's income. */
    private List<Transaction> incomeMonths(String... amounts) {
        List<Transaction> history = new ArrayList<>();
        for (int i = 0; i < amounts.length; i++) {
            if (amounts[i] != null) {
                history.add(income(2026, 3 + i, new BigDecimal(amounts[i])));
            }
        }
        return history;
    }

    @Test
    @DisplayName("income far below a varying baseline → WARN drop with z-score metrics")
    void incomeDropDetected() {
        // trailing [4000,5000,6000]: mean 5000, stddev ~816.5; June 3200 → z ~ -2.2 → WARN
        List<Transaction> history = incomeMonths("4000", "5000", "6000", "3200");
        Insight insight = analyzer.detectIncomeDrop(history, PERIOD).orElseThrow();
        assertEquals(InsightType.INCOME_DROP, insight.getType());
        assertEquals(Severity.WARN, insight.getSeverity());
        assertNull(insight.getCategory()); // account-wide
        assertEquals("insight.income_drop", insight.getCode());
        assertEquals(0, new BigDecimal("5000.00").compareTo((BigDecimal) insight.getMetrics().get("baseline")));
        assertEquals(0, new BigDecimal("3200.00").compareTo((BigDecimal) insight.getMetrics().get("actual")));
        assertEquals(0.64, insight.getMetrics().get("dropRatio"));
    }

    @Test
    @DisplayName("flat salary baseline: half or less arrived → WARN (z-score is undefined at stddev 0)")
    void incomeDropFlatBaselineFallback() {
        List<Transaction> history = incomeMonths("5000", "5000", "5000", "2000"); // 40% of usual
        Insight insight = analyzer.detectIncomeDrop(history, PERIOD).orElseThrow();
        assertEquals(Severity.WARN, insight.getSeverity());
        assertEquals(0.4, insight.getMetrics().get("dropRatio"));
        assertNull(insight.getMetrics().get("zScore")); // no variance → no z
    }

    @Test
    @DisplayName("flat salary baseline: NO income at all this month → CRITICAL")
    void incomeVanishedIsCritical() {
        List<Transaction> history = incomeMonths("5000", "5000", "5000"); // nothing in June
        Insight insight = analyzer.detectIncomeDrop(history, PERIOD).orElseThrow();
        assertEquals(Severity.CRITICAL, insight.getSeverity());
        assertEquals(0.0, insight.getMetrics().get("dropRatio"));
    }

    @Test
    @DisplayName("a small dip on a flat baseline is not a drop")
    void smallDipOnFlatBaselineIgnored() {
        // 70% of usual > the 50% flat-baseline floor
        assertTrue(analyzer.detectIncomeDrop(incomeMonths("5000", "5000", "5000", "3500"), PERIOD).isEmpty());
    }

    @Test
    @DisplayName("income at/above the baseline → no drop")
    void steadyIncomeNoDrop() {
        assertTrue(analyzer.detectIncomeDrop(incomeMonths("5000", "5000", "5000", "5000"), PERIOD).isEmpty());
    }

    @Test
    @DisplayName("fewer than 3 baseline months → no drop call")
    void incomeDropNeedsBaseline() {
        List<Transaction> history = new ArrayList<>();
        history.add(income(2026, 4, new BigDecimal("5000")));
        history.add(income(2026, 5, new BigDecimal("5000")));
        assertTrue(analyzer.detectIncomeDrop(history, PERIOD).isEmpty());
    }

    // ---- Wave 3: cashflow negative (closed-month expenses vs income) ------------------------

    @Test
    @DisplayName("month expenses over its income → WARN with deficit metrics")
    void cashflowNegativeDetected() {
        List<Transaction> history = new ArrayList<>();
        history.add(income(2026, 6, new BigDecimal("5000")));
        history.add(expense(Category.FOOD, 2026, 6, new BigDecimal("6000"))); // ratio 1.2 < 1.25
        Insight insight = analyzer.detectCashflowNegative(history, PERIOD).orElseThrow();
        assertEquals(InsightType.CASHFLOW_NEGATIVE, insight.getType());
        assertEquals(Severity.WARN, insight.getSeverity());
        assertNull(insight.getCategory());
        assertEquals("insight.cashflow_negative", insight.getCode());
        assertEquals(0, new BigDecimal("1000.00").compareTo((BigDecimal) insight.getMetrics().get("deficit")));
        assertEquals(1.2, insight.getMetrics().get("overshootRatio"));
    }

    @Test
    @DisplayName("expenses at 125%+ of income escalate to CRITICAL (same ratio as budget pace)")
    void cashflowDeepDeficitIsCritical() {
        List<Transaction> history = new ArrayList<>();
        history.add(income(2026, 6, new BigDecimal("5000")));
        history.add(expense(Category.FOOD, 2026, 6, new BigDecimal("7000"))); // ratio 1.4
        assertEquals(Severity.CRITICAL,
                analyzer.detectCashflowNegative(history, PERIOD).orElseThrow().getSeverity());
    }

    @Test
    @DisplayName("a month that pays for itself → no cashflow insight")
    void cashflowPositiveNone() {
        List<Transaction> history = new ArrayList<>();
        history.add(income(2026, 6, new BigDecimal("5000")));
        history.add(expense(Category.FOOD, 2026, 6, new BigDecimal("4000")));
        assertTrue(analyzer.detectCashflowNegative(history, PERIOD).isEmpty());
    }

    @Test
    @DisplayName("no income signal in the month → no cashflow verdict (INCOME_DROP's territory)")
    void cashflowNeedsIncomeSignal() {
        List<Transaction> history = new ArrayList<>();
        history.add(expense(Category.FOOD, 2026, 6, new BigDecimal("4000")));
        assertTrue(analyzer.detectCashflowNegative(history, PERIOD).isEmpty());
    }
}
