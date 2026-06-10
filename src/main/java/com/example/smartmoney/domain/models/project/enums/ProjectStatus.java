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
     * Project is terminally cancelled. All past contributions are reversed via
     * compensating PROJECT_REFUND transactions, restoring members' safe spending.
     * No further mutations allowed.
     */
    ABANDONED
}
