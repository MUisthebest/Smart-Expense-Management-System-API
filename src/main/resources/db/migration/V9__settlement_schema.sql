-- V9: Settlement service schema corrections
-- 1. Add missing created_at column to project_tracking
-- 2. Replace old project_history schema with settlement-ready columns

-- ─── 1. project_tracking: add created_at if not already present ──────────────
ALTER TABLE project_tracking
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;

-- Back-fill existing rows so the NOT NULL constraint won't block the app
UPDATE project_tracking
SET created_at = updated_at
WHERE created_at IS NULL;

-- ─── 2. project_history: drop old schema and create new one ──────────────────
-- The old table (V1__create_project_tracking_history.sql) had a different shape:
--   snapshot_date, recorded_money_saved, monthly_performance_status
-- The new settlement service writes: month, year, money_saved_before/after,
--   monthly_saving, penalty, surplus_invested, month_left_before/after

DROP TABLE IF EXISTS project_history;

CREATE TABLE project_history (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id         UUID,
    user_id            UUID,
    month              INTEGER,
    year               INTEGER,
    money_saved_before NUMERIC(19, 2),
    money_saved_after  NUMERIC(19, 2),
    monthly_saving     NUMERIC(19, 2),
    penalty            NUMERIC(19, 2),
    surplus_invested   NUMERIC(19, 2),
    month_left_before  INTEGER,
    month_left_after   INTEGER,
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_project_history_project_id ON project_history(project_id);
CREATE INDEX idx_project_history_user_id    ON project_history(user_id);
CREATE INDEX idx_project_history_year_month ON project_history(year, month);
