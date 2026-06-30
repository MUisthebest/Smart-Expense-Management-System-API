package com.example.smartmoney.domain.services;

import com.example.smartmoney.domain.models.budget.Budget;
import com.example.smartmoney.domain.models.insight.Insight;
import com.example.smartmoney.domain.models.insight.InsightType;
import com.example.smartmoney.domain.models.insight.Severity;
import com.example.smartmoney.domain.models.transaction.Transaction;
import com.example.smartmoney.domain.models.transaction.enums.Category;
import com.example.smartmoney.domain.models.transaction.enums.TransactionType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Pure domain service that derives deterministic {@link Insight}s from a user's
 * transaction history and current budgets. No Spring, no I/O — the caller loads the
 * data and passes it in, which makes every rule here unit-testable in isolation.
 *
 * <p>Guiding principle (docs/redesign-workstream-c-plan.md §0): compute facts here;
 * only phrase them (later, optionally) with an LLM. The analyzer never narrates and
 * never does arithmetic on the LLM's behalf.
 */
public class SpendingInsightAnalyzer {

    /** Trailing window (months) used for the overspend baseline. */
    public static final int TRAILING_WINDOW = 6;
    /** Minimum months of real data required before an anomaly can be judged. */
    static final int MIN_SAMPLE = 3;
    /** Months used for the robust median that backs a RAISE_BUDGET resolved value. */
    static final int MEDIAN_WINDOW = 3;
    /** Minimum months of data before a trend slope is meaningful. */
    static final int MIN_TREND_SAMPLE = 3;
    /** Minimum distinct months a charge must appear in to count as recurring. */
    static final int RECURRING_MIN_MONTHS = 3;

    private static final BigDecimal Z_WARN = BigDecimal.valueOf(2);
    private static final BigDecimal Z_CRITICAL = BigDecimal.valueOf(3);
    private static final double PACE_CRITICAL_RATIO = 1.25d;
    /** Smoothing factor for the EXP_SMOOTHING budget-pace forecast (weight on the most recent day). */
    private static final double EXP_SMOOTHING_ALPHA = 0.5d;
    /** Monthly growth rate (slope / mean) thresholds for an upward category trend. */
    private static final double TREND_WARN_RATE = 0.10d;
    private static final double TREND_CRITICAL_RATE = 0.25d;
    /** A recurring charge's monthly amounts must all sit within ±this fraction of their median. */
    private static final double RECURRING_TOLERANCE = 0.15d;

    /** Which end-of-month projection the budget-pace rule uses (P3-B). */
    private final ForecastMethod forecastMethod;

    /** Default analyzer — uses the {@link ForecastMethod#LINEAR} pace projection (the control). */
    public SpendingInsightAnalyzer() {
        this(ForecastMethod.LINEAR);
    }

    /** @param forecastMethod which budget-pace projection to use; {@code null} → {@code LINEAR}. */
    public SpendingInsightAnalyzer(ForecastMethod forecastMethod) {
        this.forecastMethod = forecastMethod == null ? ForecastMethod.LINEAR : forecastMethod;
    }

    /**
     * @param history expense (and other) transactions covering at least the trailing window
     * @param budgets the budgets in effect for {@code asOf}'s month
     * @param asOf    the reference date ("today"); the analyzed period is its month. Passed in
     *                (rather than read from the clock) so the service stays pure and testable.
     */
    public List<Insight> analyze(List<Transaction> history, List<Budget> budgets, LocalDate asOf) {
        List<Insight> insights = new ArrayList<>();
        insights.addAll(detectOverspendAnomalies(history, asOf));
        insights.addAll(detectBudgetPace(budgets, history, asOf));
        insights.addAll(detectCategoryTrends(history, asOf));
        insights.addAll(detectRecurring(history, asOf));
        detectSavingsCapacity(history, budgets, asOf).ifPresent(insights::add);
        return insights;
    }

    // ---- Method A: overspend anomaly (rolling z-score) -----------------------------------

    private List<Insight> detectOverspendAnomalies(List<Transaction> history, LocalDate asOf) {
        YearMonth period = YearMonth.from(asOf);
        Map<Category, Map<YearMonth, BigDecimal>> byCategory = monthlyExpenseTotalsByCategory(history);

        List<Insight> insights = new ArrayList<>();
        for (Map.Entry<Category, Map<YearMonth, BigDecimal>> entry : byCategory.entrySet()) {
            Category category = entry.getKey();
            Map<YearMonth, BigDecimal> totals = entry.getValue();

            List<BigDecimal> trailing = trailingTotals(totals, period, TRAILING_WINDOW);
            // Cold start: no baseline to judge against — this is exactly why traits exist.
            if (trailing.size() < MIN_SAMPLE) {
                continue;
            }

            double mean = mean(trailing);
            double stddev = populationStddev(trailing, mean);
            // No variance → every month is identical; a z-score would divide by zero.
            if (stddev == 0d) {
                continue;
            }

            BigDecimal actual = totals.getOrDefault(period, BigDecimal.ZERO);
            double zScore = (actual.doubleValue() - mean) / stddev;
            Severity severity = severityForZ(zScore);

            Map<String, Object> metrics = new java.util.LinkedHashMap<>();
            metrics.put("baseline", round(BigDecimal.valueOf(mean)));
            metrics.put("stddev", round(BigDecimal.valueOf(stddev)));
            metrics.put("actual", round(actual));
            metrics.put("zScore", round2(zScore));
            metrics.put("sampleMonths", trailing.size());

            insights.add(Insight.builder()
                    .type(InsightType.OVERSPEND_ANOMALY)
                    .category(category)
                    .severity(severity)
                    .period(period)
                    .metrics(metrics)
                    .code(code("overspend_anomaly", category))
                    .build());
        }
        return insights;
    }

    private static Severity severityForZ(double zScore) {
        BigDecimal z = BigDecimal.valueOf(zScore);
        if (z.compareTo(Z_CRITICAL) >= 0) {
            return Severity.CRITICAL;
        }
        if (z.compareTo(Z_WARN) >= 0) {
            return Severity.WARN;
        }
        return Severity.INFO;
    }

    // ---- Method B: budget pace projection (linear or exp-smoothing, P3-B) ----------------

    private List<Insight> detectBudgetPace(List<Budget> budgets, List<Transaction> history, LocalDate asOf) {
        if (budgets == null) {
            return List.of();
        }
        YearMonth period = YearMonth.from(asOf);
        int dayOfMonth = Math.max(1, asOf.getDayOfMonth());
        int daysInMonth = period.lengthOfMonth();

        List<Insight> insights = new ArrayList<>();
        for (Budget budget : budgets) {
            if (budget == null || budget.getAmountLimit() == null
                    || budget.getAmountLimit().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal spent = budget.getSpent() != null ? budget.getSpent() : BigDecimal.ZERO;

            Projection projection = projectEndOfMonth(
                    history, budget.getCategory(), period, spent.doubleValue(), dayOfMonth, daysInMonth);
            double projected = projection.projectedEndOfMonth();
            double limit = budget.getAmountLimit().doubleValue();
            if (projected <= limit) {
                continue; // on track — nothing actionable to surface
            }
            double overshootRatio = projected / limit;
            Severity severity = overshootRatio >= PACE_CRITICAL_RATIO ? Severity.CRITICAL : Severity.WARN;

            Map<String, Object> metrics = new java.util.LinkedHashMap<>();
            metrics.put("spent", round(spent));
            metrics.put("limit", round(budget.getAmountLimit()));
            metrics.put("dayOfMonth", dayOfMonth);
            metrics.put("daysInMonth", daysInMonth);
            metrics.put("projectedEndOfMonth", round(BigDecimal.valueOf(projected)));
            metrics.put("overshootRatio", round2(overshootRatio));
            metrics.put("forecastMethod", projection.method().name());
            if (projection.smoothedDailyRate() != null) {
                metrics.put("smoothedDailyRate", round(BigDecimal.valueOf(projection.smoothedDailyRate())));
            }

            insights.add(Insight.builder()
                    .type(InsightType.BUDGET_PACE)
                    .category(budget.getCategory())
                    .severity(severity)
                    .period(period)
                    .metrics(metrics)
                    .code(code("budget_pace", budget.getCategory()))
                    .build());
        }
        return insights;
    }

    /**
     * Project a category's end-of-month spend by the configured {@link #forecastMethod}.
     * EXP_SMOOTHING needs a daily series; when none is available (the spend is only on the budget),
     * it degrades to the LINEAR projection so a budget-only caller still gets a forecast.
     */
    private Projection projectEndOfMonth(List<Transaction> history, Category category, YearMonth period,
                                         double spent, int dayOfMonth, int daysInMonth) {
        if (forecastMethod == ForecastMethod.EXP_SMOOTHING) {
            double[] daily = dailyExpenseSeries(history, category, period, dayOfMonth);
            double observed = 0d;
            for (double d : daily) {
                observed += d;
            }
            if (observed > 0d) {
                double rate = exponentiallySmoothedRate(daily);
                double projected = spent + rate * (daysInMonth - dayOfMonth);
                return new Projection(projected, ForecastMethod.EXP_SMOOTHING, rate);
            }
            // No daily granularity → fall back to the linear projection (still labelled honestly).
        }
        double projected = spent / dayOfMonth * daysInMonth;
        return new Projection(projected, ForecastMethod.LINEAR, null);
    }

    /** Per-day expense totals for {@code category} in {@code period}, for days 1..{@code upToDay}. */
    private static double[] dailyExpenseSeries(List<Transaction> history, Category category,
                                               YearMonth period, int upToDay) {
        double[] series = new double[upToDay];
        if (history == null) {
            return series;
        }
        for (Transaction tx : history) {
            if (tx == null || tx.getType() != TransactionType.EXPENSE
                    || tx.getCategory() != category || tx.getAmount() == null || tx.getDate() == null) {
                continue;
            }
            if (!YearMonth.from(tx.getDate()).equals(period)) {
                continue;
            }
            int dom = tx.getDate().getDayOfMonth();
            if (dom >= 1 && dom <= upToDay) {
                series[dom - 1] += tx.getAmount().getValue().doubleValue();
            }
        }
        return series;
    }

    /**
     * Simple exponential smoothing of a daily-spend series (including zero-spend days): the final
     * level is an EWMA of the daily burn rate, so an isolated spike decays away over later days
     * rather than dominating the forecast the way the linear rate does.
     */
    private static double exponentiallySmoothedRate(double[] daily) {
        double level = daily[0];
        for (int i = 1; i < daily.length; i++) {
            level = EXP_SMOOTHING_ALPHA * daily[i] + (1 - EXP_SMOOTHING_ALPHA) * level;
        }
        return level;
    }

    // ---- C1.4: trailing-median helper (robust resolved value for RAISE_BUDGET) -----------

    /**
     * Median of the category's monthly expense totals over the {@value #MEDIAN_WINDOW}
     * months immediately before {@code period}. Robust to a single large one-off purchase.
     * Returns {@link BigDecimal#ZERO} when there is no prior data.
     */
    public BigDecimal trailing3MonthMedian(List<Transaction> history, Category category, YearMonth period) {
        Map<Category, Map<YearMonth, BigDecimal>> byCategory = monthlyExpenseTotalsByCategory(history);
        Map<YearMonth, BigDecimal> totals = byCategory.get(category);
        if (totals == null) {
            return BigDecimal.ZERO;
        }
        List<BigDecimal> sample = trailingTotals(totals, period, MEDIAN_WINDOW);
        if (sample.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return round(median(sample));
    }

    // ---- P2-C Method D: category trend (least-squares slope) -----------------------------

    private List<Insight> detectCategoryTrends(List<Transaction> history, LocalDate asOf) {
        YearMonth period = YearMonth.from(asOf);
        Map<Category, Map<YearMonth, BigDecimal>> byCategory = monthlyExpenseTotalsByCategory(history);

        List<Insight> insights = new ArrayList<>();
        for (Map.Entry<Category, Map<YearMonth, BigDecimal>> entry : byCategory.entrySet()) {
            Category category = entry.getKey();
            Map<YearMonth, BigDecimal> totals = entry.getValue();

            // Months with data within [period - window, period] (inclusive), oldest first.
            List<YearMonth> months = new ArrayList<>();
            for (int i = TRAILING_WINDOW; i >= 0; i--) {
                YearMonth m = period.minusMonths(i);
                if (totals.containsKey(m)) {
                    months.add(m);
                }
            }
            if (months.size() < MIN_TREND_SAMPLE) {
                continue;
            }

            double mean = months.stream().mapToDouble(m -> totals.get(m).doubleValue()).average().orElse(0d);
            if (mean <= 0d) {
                continue;
            }
            double slope = leastSquaresSlope(months, totals);
            if (slope <= 0d) {
                continue; // only upward "creeping" trends are actionable here
            }
            double growthRatePerMonth = slope / mean;
            if (growthRatePerMonth < TREND_WARN_RATE) {
                continue; // flat-ish — not worth surfacing
            }
            Severity severity = growthRatePerMonth >= TREND_CRITICAL_RATE ? Severity.CRITICAL : Severity.WARN;

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("slope", round(BigDecimal.valueOf(slope)));
            metrics.put("meanMonthly", round(BigDecimal.valueOf(mean)));
            metrics.put("growthRatePerMonth", round2(growthRatePerMonth));
            metrics.put("sampleMonths", months.size());

            insights.add(Insight.builder()
                    .type(InsightType.CATEGORY_TREND)
                    .category(category)
                    .severity(severity)
                    .period(period)
                    .metrics(metrics)
                    .code(code("category_trend", category))
                    .build());
        }
        return insights;
    }

    // ---- P2-C Method E: recurring-charge detector ----------------------------------------

    private List<Insight> detectRecurring(List<Transaction> history, LocalDate asOf) {
        YearMonth period = YearMonth.from(asOf);
        List<Insight> insights = new ArrayList<>();
        for (RecurringCharge charge : findRecurringCharges(history, period)) {
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("monthlyAmount", round(charge.monthlyAmount()));
            metrics.put("monthsObserved", charge.monthsObserved());
            metrics.put("description", charge.description());

            insights.add(Insight.builder()
                    .type(InsightType.RECURRING)
                    .category(charge.category())
                    .severity(Severity.INFO) // a fact that feeds disposable, not a problem
                    .period(period)
                    .metrics(metrics)
                    .code(code("recurring", charge.category()))
                    .build());
        }
        return insights;
    }

    /**
     * Total estimated monthly recurring cost — sum of every detected recurring charge.
     * Public so the savings-capacity calc (and future Pivot-A {@code disposable}) can reuse it.
     */
    public BigDecimal estimatedMonthlyRecurringCost(List<Transaction> history, YearMonth period) {
        return findRecurringCharges(history, period).stream()
                .map(RecurringCharge::monthlyAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<RecurringCharge> findRecurringCharges(List<Transaction> history, YearMonth period) {
        if (history == null) {
            return List.of();
        }
        // Group identifiable charges by (category, normalized description); sum per month.
        Map<RecurringKey, Map<YearMonth, BigDecimal>> groups = new LinkedHashMap<>();
        for (Transaction tx : history) {
            if (tx == null || tx.getType() != TransactionType.EXPENSE
                    || tx.getCategory() == null || tx.getAmount() == null || tx.getDate() == null
                    || tx.getDescription() == null || tx.getDescription().isBlank()) {
                continue; // can't identify a specific recurring item without a description
            }
            YearMonth month = YearMonth.from(tx.getDate());
            if (month.isAfter(period) || month.isBefore(period.minusMonths(TRAILING_WINDOW))) {
                continue;
            }
            RecurringKey key = new RecurringKey(tx.getCategory(),
                    tx.getDescription().trim().toLowerCase(Locale.ROOT));
            groups.computeIfAbsent(key, k -> new TreeMap<>())
                    .merge(month, tx.getAmount().getValue(), BigDecimal::add);
        }

        List<RecurringCharge> charges = new ArrayList<>();
        for (Map.Entry<RecurringKey, Map<YearMonth, BigDecimal>> entry : groups.entrySet()) {
            Map<YearMonth, BigDecimal> monthly = entry.getValue();
            if (monthly.size() < RECURRING_MIN_MONTHS) {
                continue;
            }
            List<BigDecimal> amounts = new ArrayList<>(monthly.values());
            BigDecimal median = median(amounts);
            if (median.compareTo(BigDecimal.ZERO) <= 0 || !allWithinTolerance(amounts, median)) {
                continue; // amounts vary too much to be a stable recurring cost
            }
            charges.add(new RecurringCharge(entry.getKey().category(), entry.getKey().description(),
                    median, monthly.size()));
        }
        return charges;
    }

    // ---- P2-C Method F: savings capacity (disposable) ------------------------------------

    private Optional<Insight> detectSavingsCapacity(List<Transaction> history, List<Budget> budgets, LocalDate asOf) {
        YearMonth period = YearMonth.from(asOf);
        BigDecimal avgIncome = averageMonthlyIncome(history, period);
        if (avgIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty(); // no income signal → can't judge capacity
        }
        BigDecimal recurring = estimatedMonthlyRecurringCost(history, period);
        BigDecimal committed = committedBudgetTotal(budgets);
        BigDecimal disposable = avgIncome.subtract(recurring).subtract(committed);
        if (disposable.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty(); // no room to save this month
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("avgMonthlyIncome", round(avgIncome));
        metrics.put("recurringCosts", round(recurring));
        metrics.put("committedBudgets", round(committed));
        metrics.put("disposable", round(disposable));

        return Optional.of(Insight.builder()
                .type(InsightType.SAVINGS_CAPACITY)
                .category(null) // not category-scoped
                .severity(Severity.INFO) // an opportunity, not a warning
                .period(period)
                .metrics(metrics)
                .code("insight.savings_capacity")
                .build());
    }

    // ---- shared helpers ------------------------------------------------------------------

    private static Map<Category, Map<YearMonth, BigDecimal>> monthlyExpenseTotalsByCategory(List<Transaction> history) {
        Map<Category, Map<YearMonth, BigDecimal>> byCategory = new EnumMap<>(Category.class);
        if (history == null) {
            return byCategory;
        }
        for (Transaction tx : history) {
            if (tx == null || tx.getType() != TransactionType.EXPENSE
                    || tx.getCategory() == null || tx.getAmount() == null || tx.getDate() == null) {
                continue;
            }
            YearMonth month = YearMonth.from(tx.getDate());
            byCategory
                    .computeIfAbsent(tx.getCategory(), c -> new TreeMap<>())
                    .merge(month, tx.getAmount().getValue(), BigDecimal::add);
        }
        return byCategory;
    }

    /** Totals for the up-to-{@code window} months strictly before {@code period} that have data. */
    private static List<BigDecimal> trailingTotals(Map<YearMonth, BigDecimal> totals, YearMonth period, int window) {
        List<BigDecimal> sample = new ArrayList<>();
        for (int i = 1; i <= window; i++) {
            BigDecimal total = totals.get(period.minusMonths(i));
            if (total != null) {
                sample.add(total);
            }
        }
        return sample;
    }

    private static double mean(List<BigDecimal> values) {
        double sum = 0d;
        for (BigDecimal v : values) {
            sum += v.doubleValue();
        }
        return sum / values.size();
    }

    private static double populationStddev(List<BigDecimal> values, double mean) {
        double sumSq = 0d;
        for (BigDecimal v : values) {
            double d = v.doubleValue() - mean;
            sumSq += d * d;
        }
        return Math.sqrt(sumSq / values.size());
    }

    /** Median of a non-empty list (unrounded); average of the two middles for even counts. */
    private static BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(BigDecimal::compareTo);
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2))
                .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    /** Least-squares slope of (monthIndex, total) over the given months. */
    private static double leastSquaresSlope(List<YearMonth> months, Map<YearMonth, BigDecimal> totals) {
        long base = monthIndex(months.get(0));
        int n = months.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (YearMonth m : months) {
            double x = monthIndex(m) - base;
            double y = totals.get(m).doubleValue();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        double denominator = n * sumX2 - sumX * sumX;
        if (denominator == 0d) {
            return 0d;
        }
        return (n * sumXY - sumX * sumY) / denominator;
    }

    private static long monthIndex(YearMonth m) {
        return m.getYear() * 12L + (m.getMonthValue() - 1);
    }

    private static boolean allWithinTolerance(List<BigDecimal> amounts, BigDecimal median) {
        double med = median.doubleValue();
        for (BigDecimal a : amounts) {
            if (Math.abs(a.doubleValue() - med) / med > RECURRING_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    /** Average monthly INCOME over the months in [period - window, period] that have income. */
    private static BigDecimal averageMonthlyIncome(List<Transaction> history, YearMonth period) {
        if (history == null) {
            return BigDecimal.ZERO;
        }
        Map<YearMonth, BigDecimal> incomeByMonth = new TreeMap<>();
        for (Transaction tx : history) {
            if (tx == null || tx.getType() != TransactionType.INCOME
                    || tx.getAmount() == null || tx.getDate() == null) {
                continue;
            }
            YearMonth month = YearMonth.from(tx.getDate());
            if (month.isAfter(period) || month.isBefore(period.minusMonths(TRAILING_WINDOW))) {
                continue;
            }
            incomeByMonth.merge(month, tx.getAmount().getValue(), BigDecimal::add);
        }
        if (incomeByMonth.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = incomeByMonth.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(incomeByMonth.size()), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal committedBudgetTotal(List<Budget> budgets) {
        if (budgets == null) {
            return BigDecimal.ZERO;
        }
        return budgets.stream()
                .filter(b -> b != null && b.getAmountLimit() != null)
                .map(Budget::getAmountLimit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Grouping key for recurring detection: a specific item within a category. */
    private record RecurringKey(Category category, String description) {
    }

    /** A detected recurring charge and its stable monthly amount. */
    private record RecurringCharge(Category category, String description, BigDecimal monthlyAmount, int monthsObserved) {
    }

    /**
     * Result of a budget-pace projection: the figure, the method that actually produced it (which
     * may be LINEAR even under EXP_SMOOTHING if it fell back), and the smoothed daily rate when one
     * was used ({@code null} for the linear path).
     */
    private record Projection(double projectedEndOfMonth, ForecastMethod method, Double smoothedDailyRate) {
    }

    private static String code(String typeKey, Category category) {
        return "insight." + typeKey + "." + category.name().toLowerCase(Locale.ROOT);
    }

    private static BigDecimal round(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
