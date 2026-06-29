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
import java.util.Locale;
import java.util.Map;
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

    private static final BigDecimal Z_WARN = BigDecimal.valueOf(2);
    private static final BigDecimal Z_CRITICAL = BigDecimal.valueOf(3);
    private static final double PACE_CRITICAL_RATIO = 1.25d;

    /**
     * @param history expense (and other) transactions covering at least the trailing window
     * @param budgets the budgets in effect for {@code asOf}'s month
     * @param asOf    the reference date ("today"); the analyzed period is its month. Passed in
     *                (rather than read from the clock) so the service stays pure and testable.
     */
    public List<Insight> analyze(List<Transaction> history, List<Budget> budgets, LocalDate asOf) {
        List<Insight> insights = new ArrayList<>();
        insights.addAll(detectOverspendAnomalies(history, asOf));
        insights.addAll(detectBudgetPace(budgets, asOf));
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

    // ---- Method B: budget pace projection (linear) ---------------------------------------

    private List<Insight> detectBudgetPace(List<Budget> budgets, LocalDate asOf) {
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

            // projected = spent / dayOfMonth * daysInMonth
            double projected = spent.doubleValue() / dayOfMonth * daysInMonth;
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
        sample.sort(BigDecimal::compareTo);
        int n = sample.size();
        if (n % 2 == 1) {
            return round(sample.get(n / 2));
        }
        BigDecimal lo = sample.get(n / 2 - 1);
        BigDecimal hi = sample.get(n / 2);
        return round(lo.add(hi).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP));
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
