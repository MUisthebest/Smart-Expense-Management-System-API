package com.example.smartmoney.domain.services;

/**
 * How {@link SpendingInsightAnalyzer} projects a budget's end-of-month spend (redesign Phase 3 P3-B).
 *
 * <ul>
 *   <li>{@link #LINEAR} — the original naive extrapolation {@code spent / dayOfMonth × daysInMonth}.
 *       Kept as the <em>control</em>: simple, but a single early spike inflates the projection.</li>
 *   <li>{@link #EXP_SMOOTHING} — exponentially-smoothed daily burn rate carried over the remaining
 *       days. Steadier and far less sensitive to a one-off purchase, while still reacting to a
 *       sustained change in recent days. Falls back to {@code LINEAR} when no daily series is
 *       available (e.g. spend is tracked only on the budget).</li>
 * </ul>
 */
public enum ForecastMethod {
    LINEAR,
    EXP_SMOOTHING
}
