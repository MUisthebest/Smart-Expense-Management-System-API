package com.example.smartmoney.application.dtos.responses.project;

/**
 * Machine-readable explanation behind a project's status, so the UI can show
 * <em>why</em> a project is where it is rather than a bare status badge. The FE
 * maps each value to localized copy.
 */
public enum ProjectStatusReason {
    /** Live and tracking to (or ahead of) the deadline. */
    ON_TRACK,
    /** Live but pace-aware months left exceed the calendar months to deadline. */
    BEHIND_PACE,
    /** Paused by settlement because overspend debt hit the cap. */
    FROZEN_DEBT,
    /** Terminal: the deadline passed before the goal was reached. */
    EXPIRED_DEADLINE,
    /** Terminal: stayed frozen for the maximum consecutive months. */
    EXPIRED_FROZEN_TOO_LONG,
    /** Goal reached. */
    COMPLETED,
    /** Terminal: the owner abandoned the project. */
    ABANDONED_BY_USER,
    /** No specific reason (e.g. deprecated CANCELLED state). */
    NONE
}
