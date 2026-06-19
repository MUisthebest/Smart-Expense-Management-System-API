package com.example.smartmoney.application.dtos.responses.project;

/**
 * How the project's pace-aware {@code monthLeft} compares to the calendar months
 * remaining until the deadline. Drives the on-track / behind indicator in the UI.
 * The FE owns all display copy and colour — this is a machine-readable signal only.
 */
public enum PaceStatus {
    /** Will reach the goal before the deadline ({@code monthLeft < monthsToDeadline}). */
    AHEAD,
    /** Tracking exactly to the deadline ({@code monthLeft == monthsToDeadline}). */
    ON_TRACK,
    /** Falling behind ({@code monthLeft > monthsToDeadline}). */
    BEHIND,
    /** Terminal project or no usable deadline — pace is meaningless. */
    NOT_APPLICABLE
}
