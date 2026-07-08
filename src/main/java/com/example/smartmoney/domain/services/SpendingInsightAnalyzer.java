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
import java.time.Duration;
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
    /**
     * Minimum elapsed days in the month before a budget-pace projection is trusted. A single
     * purchase on day 1–2 would otherwise extrapolate to an absurd month-end figure
     * (e.g. 2M spent on day 1 → 62M projected). Below this, pace is treated as too-early-to-tell.
     */
    static final int MIN_PACE_DAYS = 3;

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
    /**
     * A budgeted category is "under-provisioned" (REDUCE_BUDGET-worthy) only when both its trailing
     * median actual and its current-month projection sit at/below this fraction of the limit — a
     * comfortable margin so a normal-variance month never triggers a cut.
     */
    private static final double UNDERSPEND_RATIO = 0.60d;
    /** How close together two identical charges must fall to look like a double-charge. */
    static final int DUPLICATE_WINDOW_HOURS = 72;
    /**
     * Flat-baseline fallback for the income-drop rule: when the trailing income has zero variance
     * (a fixed salary), a z-score is undefined, so instead fire when the closed month brought in
     * at most this fraction of the usual amount.
     */
    private static final double INCOME_DROP_FLAT_RATIO = 0.5d;

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
        insights.addAll(detectBudgetUnderspend(budgets, history, asOf));
        insights.addAll(detectCategoryTrends(history, asOf));
        insights.addAll(detectRecurring(history, asOf));
        insights.addAll(detectSubscriptionPriceHikes(history, asOf));
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
        // Too early in the month to project a month-end figure from — one day's spend is not a trend.
        if (dayOfMonth < MIN_PACE_DAYS) {
            return List.of();
        }

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

    // ---- Wave 1: budget underspend (inverse of budget pace) ------------------------------

    /**
     * A budgeted category the user is consistently spending well under: the trailing-median actual
     * is at/below {@value #UNDERSPEND_RATIO}× the limit <em>and</em> the current-month pace confirms
     * it (so a month that's merely started slow but about to catch up is not flagged). INFO — it
     * backs a REDUCE_BUDGET nudge, never a warning. The early-month guard mirrors {@code detectBudgetPace}.
     */
    private List<Insight> detectBudgetUnderspend(List<Budget> budgets, List<Transaction> history, LocalDate asOf) {
        if (budgets == null) {
            return List.of();
        }
        YearMonth period = YearMonth.from(asOf);
        int dayOfMonth = Math.max(1, asOf.getDayOfMonth());
        int daysInMonth = period.lengthOfMonth();
        if (dayOfMonth < MIN_PACE_DAYS) {
            return List.of(); // too early to trust a pace read
        }

        List<Insight> insights = new ArrayList<>();
        for (Budget budget : budgets) {
            if (budget == null || budget.getCategory() == null || budget.getAmountLimit() == null
                    || budget.getAmountLimit().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Category category = budget.getCategory();
            BigDecimal limit = budget.getAmountLimit();
            double threshold = limit.doubleValue() * UNDERSPEND_RATIO;

            // Historical signal: a real, robust spend history that sits well under the limit. Require
            // a positive median so we never propose trimming a budget the user simply hasn't used yet.
            BigDecimal median = trailing3MonthMedian(history, category, period);
            if (median.compareTo(BigDecimal.ZERO) <= 0 || median.doubleValue() > threshold) {
                continue;
            }

            // Pace confirmation: this month must also be tracking under the limit — don't suggest a
            // cut in a month that's on pace to use the budget after all.
            BigDecimal spent = budget.getSpent() != null ? budget.getSpent() : BigDecimal.ZERO;
            double projected = spent.doubleValue() / dayOfMonth * daysInMonth;
            if (projected > threshold) {
                continue;
            }

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("limit", round(limit));
            metrics.put("medianActual", round(median));
            metrics.put("projectedEndOfMonth", round(BigDecimal.valueOf(projected)));
            metrics.put("underspendRatio", round2(median.doubleValue() / limit.doubleValue()));

            insights.add(Insight.builder()
                    .type(InsightType.BUDGET_UNDERSPEND)
                    .category(category)
                    .severity(Severity.INFO) // a saving opportunity, not a problem
                    .period(period)
                    .metrics(metrics)
                    .code(code("budget_underspend", category))
                    .build());
        }
        return insights;
    }

    // ---- Wave 1: per-transaction anomalies (notification-only, evaluated on create) ------

    /**
     * A single expense that is an outlier versus that category's typical <em>per-transaction</em>
     * size. Judged with the same z-score bands as the monthly overspend rule, but over prior
     * per-charge amounts in the category (the candidate excluded). Empty when there's too little
     * history, no variance, or the charge isn't notable (below the WARN band). Evaluated
     * synchronously on create and pushed directly — never added to {@code analyze()}.
     */
    public Optional<Insight> detectLargeTransaction(List<Transaction> history, Transaction candidate) {
        if (candidate == null || candidate.getType() != TransactionType.EXPENSE
                || candidate.getCategory() == null || candidate.getAmount() == null) {
            return Optional.empty();
        }
        List<BigDecimal> prior = new ArrayList<>();
        if (history != null) {
            for (Transaction tx : history) {
                if (tx == null || tx.getType() != TransactionType.EXPENSE
                        || tx.getCategory() != candidate.getCategory() || tx.getAmount() == null) {
                    continue;
                }
                if (isSameTransaction(tx, candidate)) {
                    continue;
                }
                prior.add(tx.getAmount().getValue());
            }
        }
        if (prior.size() < MIN_SAMPLE) {
            return Optional.empty(); // not enough charges to know what "typical" is
        }
        double mean = mean(prior);
        double stddev = populationStddev(prior, mean);
        if (stddev == 0d) {
            return Optional.empty(); // every prior charge identical — can't call this an outlier
        }
        double amount = candidate.getAmount().getValue().doubleValue();
        double zScore = (amount - mean) / stddev;
        Severity severity = severityForZ(zScore);
        if (severity == Severity.INFO) {
            return Optional.empty(); // within the normal band — not worth a ping
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("amount", round(candidate.getAmount().getValue()));
        metrics.put("typical", round(BigDecimal.valueOf(mean)));
        metrics.put("zScore", round2(zScore));
        metrics.put("sampleSize", prior.size());

        return Optional.of(Insight.builder()
                .type(InsightType.LARGE_TRANSACTION)
                .category(candidate.getCategory())
                .severity(severity)
                .period(YearMonth.from(candidate.getDate() != null ? candidate.getDate().toLocalDate() : LocalDate.now()))
                .metrics(metrics)
                .code(code("large_transaction", candidate.getCategory()))
                .build());
    }

    /**
     * A likely double-charge: another expense with the same category, exact amount, and normalized
     * description as {@code candidate} within {@value #DUPLICATE_WINDOW_HOURS} hours. Reuses the
     * recurring-detector's description normalization; requires an exact amount match and a
     * non-blank description to keep false positives near zero. INFO severity. Notification-only.
     */
    public Optional<Insight> detectDuplicateCharge(List<Transaction> history, Transaction candidate) {
        if (candidate == null || candidate.getType() != TransactionType.EXPENSE
                || candidate.getCategory() == null || candidate.getAmount() == null
                || candidate.getDate() == null
                || candidate.getDescription() == null || candidate.getDescription().isBlank()) {
            return Optional.empty();
        }
        if (history == null) {
            return Optional.empty();
        }
        String normDesc = candidate.getDescription().trim().toLowerCase(Locale.ROOT);
        BigDecimal amount = candidate.getAmount().getValue();
        for (Transaction tx : history) {
            if (tx == null || tx.getType() != TransactionType.EXPENSE
                    || tx.getCategory() != candidate.getCategory() || tx.getAmount() == null
                    || tx.getDate() == null || tx.getDescription() == null || tx.getDescription().isBlank()) {
                continue;
            }
            if (isSameTransaction(tx, candidate)) {
                continue;
            }
            if (amount.compareTo(tx.getAmount().getValue()) != 0) {
                continue; // exact-amount match only
            }
            if (!normDesc.equals(tx.getDescription().trim().toLowerCase(Locale.ROOT))) {
                continue;
            }
            long hours = Math.abs(Duration.between(tx.getDate(), candidate.getDate()).toHours());
            if (hours > DUPLICATE_WINDOW_HOURS) {
                continue;
            }

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("amount", round(amount));
            metrics.put("description", candidate.getDescription().trim());
            metrics.put("withinHours", hours);

            return Optional.of(Insight.builder()
                    .type(InsightType.DUPLICATE_CHARGE)
                    .category(candidate.getCategory())
                    .severity(Severity.INFO) // a heads-up, not a verdict — could be intentional
                    .period(YearMonth.from(candidate.getDate().toLocalDate()))
                    .metrics(metrics)
                    .code(code("duplicate_charge", candidate.getCategory()))
                    .build());
        }
        return Optional.empty();
    }

    /** Same persisted transaction? Compares ids when both are present (the candidate is already saved). */
    private static boolean isSameTransaction(Transaction a, Transaction b) {
        return a.getId() != null && b.getId() != null && a.getId().equals(b.getId());
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

    /**
     * Total EXPENSE spend already logged in {@code category} for {@code period}'s own month.
     * Unlike {@link #trailing3MonthMedian} this includes the current (partial) month, so a
     * proposed budget limit can be floored at it and never be born already-exceeded once
     * {@code CreateBudgetUseCase} backfills the month's spend. Returns {@link BigDecimal#ZERO}
     * when nothing has been spent in that category this month.
     */
    public BigDecimal currentMonthTotal(List<Transaction> history, Category category, YearMonth period) {
        Map<Category, Map<YearMonth, BigDecimal>> byCategory = monthlyExpenseTotalsByCategory(history);
        Map<YearMonth, BigDecimal> totals = byCategory.get(category);
        return totals == null ? BigDecimal.ZERO : round(totals.getOrDefault(period, BigDecimal.ZERO));
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

    // ---- Wave 2: subscription price hike (recurring group breaks its tolerance band) ------

    /**
     * A charge that was a stable recurring cost — at least {@value #RECURRING_MIN_MONTHS} prior
     * months all within ±{@code RECURRING_TOLERANCE} of their median — whose current-month amount
     * breaks that band <em>upward</em>: the subscription's price went up. WARN (actionable: review
     * or cancel it). Such a group no longer qualifies as RECURRING for the month (the hiked amount
     * fails the all-months tolerance check), so this insight replaces it rather than duplicating it.
     */
    private List<Insight> detectSubscriptionPriceHikes(List<Transaction> history, LocalDate asOf) {
        YearMonth period = YearMonth.from(asOf);
        List<Insight> insights = new ArrayList<>();
        for (Map.Entry<RecurringKey, Map<YearMonth, BigDecimal>> entry : recurringGroups(history, period).entrySet()) {
            Map<YearMonth, BigDecimal> monthly = entry.getValue();
            BigDecimal current = monthly.get(period);
            if (current == null) {
                continue; // the charge hasn't posted this month — nothing new to judge
            }
            // The group must have been a stable recurring charge BEFORE this month.
            List<BigDecimal> prior = new ArrayList<>();
            for (Map.Entry<YearMonth, BigDecimal> m : monthly.entrySet()) {
                if (m.getKey().isBefore(period)) {
                    prior.add(m.getValue());
                }
            }
            if (prior.size() < RECURRING_MIN_MONTHS) {
                continue;
            }
            BigDecimal priorMedian = median(prior);
            if (priorMedian.compareTo(BigDecimal.ZERO) <= 0 || !allWithinTolerance(prior, priorMedian)) {
                continue; // never was a stable subscription — a jump means nothing
            }
            // Only an upward break of the tolerance band is a hike (a drop is good news, not an ask).
            if (current.doubleValue() <= priorMedian.doubleValue() * (1 + RECURRING_TOLERANCE)) {
                continue;
            }

            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("description", entry.getKey().description());
            metrics.put("previousAmount", round(priorMedian));
            metrics.put("newAmount", round(current));
            metrics.put("increaseRatio", round2(current.doubleValue() / priorMedian.doubleValue() - 1d));
            metrics.put("monthsObserved", prior.size());

            insights.add(Insight.builder()
                    .type(InsightType.SUBSCRIPTION_PRICE_HIKE)
                    .category(entry.getKey().category())
                    .severity(Severity.WARN) // a price change worth acting on, not an emergency
                    .period(period)
                    .metrics(metrics)
                    .code(code("subscription_price_hike", entry.getKey().category()))
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

    /**
     * Identifiable charges grouped by (category, normalized description), summed per month over
     * [period - window, period]. Shared by the recurring detector and the price-hike detector.
     */
    private Map<RecurringKey, Map<YearMonth, BigDecimal>> recurringGroups(List<Transaction> history, YearMonth period) {
        Map<RecurringKey, Map<YearMonth, BigDecimal>> groups = new LinkedHashMap<>();
        if (history == null) {
            return groups;
        }
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
        return groups;
    }

    private List<RecurringCharge> findRecurringCharges(List<Transaction> history, YearMonth period) {
        List<RecurringCharge> charges = new ArrayList<>();
        for (Map.Entry<RecurringKey, Map<YearMonth, BigDecimal>> entry : recurringGroups(history, period).entrySet()) {
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

    // ---- soft budget envelope: total budget limits vs declared income --------------------

    /**
     * Account-wide "soft envelope": fires when the sum of {@code period}'s budget limits exceeds the
     * user's <em>declared</em> monthly income (not the learned average). Purely informational — the
     * system never blocks a budget write; this only surfaces the over-commitment so the user can
     * rebalance. Empty when income is unset/non-positive (cold-start, nothing to judge against) or
     * when committed budgets are within income. Public so the pull use case can call it directly:
     * income lives on the User, which the pure analyzer intentionally doesn't load.
     */
    public Optional<Insight> detectBudgetOverIncome(List<Budget> budgets, BigDecimal income, YearMonth period) {
        if (income == null || income.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        BigDecimal committed = committedBudgetTotal(budgets);
        if (committed.compareTo(income) <= 0) {
            return Optional.empty(); // within the envelope — nothing to surface
        }

        double overshootRatio = committed.doubleValue() / income.doubleValue();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("committedBudgets", round(committed));
        metrics.put("income", round(income));
        metrics.put("overBy", round(committed.subtract(income)));
        metrics.put("overshootRatio", round2(overshootRatio));

        return Optional.of(Insight.builder()
                .type(InsightType.BUDGET_OVER_INCOME)
                .category(null) // account-wide, not category-scoped
                .severity(Severity.WARN) // a soft warning, never enforced
                .period(period)
                .metrics(metrics)
                .code("insight.budget_over_income")
                .build());
    }

    // ---- Wave 3: closed-month recap facts (evaluated at month rollover, never in analyze()) ----

    /**
     * A closed month whose INCOME total collapsed versus the trailing baseline — the mirror of
     * {@code detectOverspendAnomalies} on the income series, judging the <em>low</em> tail. With a
     * flat baseline (fixed salary → stddev 0, where a z-score is undefined) it falls back to a
     * relative test: fire when at most {@value #INCOME_DROP_FLAT_RATIO} of the usual income arrived,
     * CRITICAL when none did. Mid-month the current total is always "low" (salary hasn't landed),
     * so this is only meaningful for a <b>completed</b> month — the month-rollover recap calls it
     * directly, like the pull use case calls {@code detectBudgetOverIncome}.
     */
    public Optional<Insight> detectIncomeDrop(List<Transaction> history, YearMonth month) {
        Map<YearMonth, BigDecimal> totals = monthlyIncomeTotals(history);
        List<BigDecimal> trailing = trailingTotals(totals, month, TRAILING_WINDOW);
        if (trailing.size() < MIN_SAMPLE) {
            return Optional.empty(); // no baseline to judge against
        }
        double mean = mean(trailing);
        if (mean <= 0d) {
            return Optional.empty();
        }
        BigDecimal actual = totals.getOrDefault(month, BigDecimal.ZERO);
        if (actual.doubleValue() >= mean) {
            return Optional.empty(); // income held up — nothing to report
        }
        double stddev = populationStddev(trailing, mean);
        double dropRatio = actual.doubleValue() / mean; // fraction of the usual income that arrived

        Severity severity;
        Double zScore = null;
        if (stddev == 0d) {
            if (dropRatio > INCOME_DROP_FLAT_RATIO) {
                return Optional.empty(); // small dip on a flat salary — not a drop
            }
            severity = actual.compareTo(BigDecimal.ZERO) == 0 ? Severity.CRITICAL : Severity.WARN;
        } else {
            double z = (actual.doubleValue() - mean) / stddev;
            if (z > -Z_WARN.doubleValue()) {
                return Optional.empty(); // within normal variance
            }
            severity = z <= -Z_CRITICAL.doubleValue() ? Severity.CRITICAL : Severity.WARN;
            zScore = round2(z);
        }

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("baseline", round(BigDecimal.valueOf(mean)));
        metrics.put("stddev", round(BigDecimal.valueOf(stddev)));
        metrics.put("actual", round(actual));
        metrics.put("dropRatio", round2(dropRatio));
        metrics.put("sampleMonths", trailing.size());
        if (zScore != null) {
            metrics.put("zScore", zScore);
        }

        return Optional.of(Insight.builder()
                .type(InsightType.INCOME_DROP)
                .category(null) // account-wide: income isn't category-scoped
                .severity(severity)
                .period(month)
                .metrics(metrics)
                .code("insight.income_drop")
                .build());
    }

    /**
     * A closed month whose expenses exceeded its income. Requires an income signal in the month —
     * a month with zero income is {@link #detectIncomeDrop}'s territory, not a cashflow verdict.
     * CRITICAL when expenses ran ≥ {@value #PACE_CRITICAL_RATIO}× income (the same escalation
     * ratio as budget pace). Like the income drop, only meaningful at month close.
     */
    public Optional<Insight> detectCashflowNegative(List<Transaction> history, YearMonth month) {
        BigDecimal income = monthlyIncomeTotals(history).getOrDefault(month, BigDecimal.ZERO);
        if (income.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        BigDecimal expenses = monthlyExpenseTotal(history, month);
        if (expenses.compareTo(income) <= 0) {
            return Optional.empty(); // the month paid for itself
        }
        double overshootRatio = expenses.doubleValue() / income.doubleValue();

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("income", round(income));
        metrics.put("expenses", round(expenses));
        metrics.put("deficit", round(expenses.subtract(income)));
        metrics.put("overshootRatio", round2(overshootRatio));

        return Optional.of(Insight.builder()
                .type(InsightType.CASHFLOW_NEGATIVE)
                .category(null) // account-wide
                .severity(overshootRatio >= PACE_CRITICAL_RATIO ? Severity.CRITICAL : Severity.WARN)
                .period(month)
                .metrics(metrics)
                .code("insight.cashflow_negative")
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
        Map<YearMonth, BigDecimal> incomeByMonth = new TreeMap<>();
        monthlyIncomeTotals(history).forEach((month, total) -> {
            if (!month.isAfter(period) && !month.isBefore(period.minusMonths(TRAILING_WINDOW))) {
                incomeByMonth.put(month, total);
            }
        });
        if (incomeByMonth.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = incomeByMonth.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(incomeByMonth.size()), 4, RoundingMode.HALF_UP);
    }

    /** INCOME totals per month across the provided history (callers bound the window themselves). */
    private static Map<YearMonth, BigDecimal> monthlyIncomeTotals(List<Transaction> history) {
        Map<YearMonth, BigDecimal> totals = new TreeMap<>();
        if (history == null) {
            return totals;
        }
        for (Transaction tx : history) {
            if (tx == null || tx.getType() != TransactionType.INCOME
                    || tx.getAmount() == null || tx.getDate() == null) {
                continue;
            }
            totals.merge(YearMonth.from(tx.getDate()), tx.getAmount().getValue(), BigDecimal::add);
        }
        return totals;
    }

    /** Total EXPENSE amount logged in {@code month}. */
    private static BigDecimal monthlyExpenseTotal(List<Transaction> history, YearMonth month) {
        BigDecimal total = BigDecimal.ZERO;
        if (history == null) {
            return total;
        }
        for (Transaction tx : history) {
            if (tx == null || tx.getType() != TransactionType.EXPENSE
                    || tx.getAmount() == null || tx.getDate() == null
                    || !YearMonth.from(tx.getDate()).equals(month)) {
                continue;
            }
            total = total.add(tx.getAmount().getValue());
        }
        return total;
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
