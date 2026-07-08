package com.example.smartmoney.domain.models.insight;

/**
 * Insight severity, ordered least → most urgent. Ordinal ordering is meaningful:
 * the producer's push gate compares against it (e.g. GENTLE pushes {@code >= WARN}).
 */
public enum Severity {
    INFO,
    WARN,
    CRITICAL;

    /** True when this severity is at least as urgent as {@code threshold}. */
    public boolean atLeast(Severity threshold) {
        return this.ordinal() >= threshold.ordinal();
    }
}
