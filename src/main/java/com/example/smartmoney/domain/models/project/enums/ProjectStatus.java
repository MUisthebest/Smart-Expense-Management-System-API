package com.example.smartmoney.domain.models.project.enums;

public enum ProjectStatus {
    ACTIVE,
    COMPLETED,
    /**
     * @deprecated Use {@link #ABANDONED} for new abandonments. Kept for backward
     * compatibility with rows already written under the previous schema.
     */
    @Deprecated
    CANCELLED,
    /**
     * Project is temporarily halted (no contributions accepted) — typically because
     * the admin/owner hit the 3-month default threshold. Resolvable by catching up
     * and unfreezing.
     */
    FROZEN,
    /**
     * Project is terminally cancelled by the owner/admin (abandon / dissolve).
     * The obligation is released so the user's safe spending recovers.
     * No further mutations allowed.
     */
    ABANDONED,
    /**
     * Project failed to meet its target: either the deadline passed while still
     * under target, or it stayed FROZEN beyond {@code app.project.max-frozen-months}
     * with no recovery. Terminal; the obligation is released (debt stops affecting
     * safe spending) so a failed goal does not haunt the user. See
     * docs/project-tracking-all-auto-plan.md §3.
     */
    EXPIRED
}
