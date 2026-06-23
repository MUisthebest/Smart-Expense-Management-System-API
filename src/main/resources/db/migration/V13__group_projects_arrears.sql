-- =========================================================
--  V13 — Hybrid Splitwise group projects + arrears tracking
-- =========================================================
--  Adds per-member monthly commitment and arrears tracking
--  on project_members. New ProjectStatus values (FROZEN,
--  ABANDONED), JoinStatus value (DEFAULTED), and Transaction
--  category (PROJECT_REFUND) are enum-only changes governed
--  by Java code — no DB-side constraints to alter because
--  the existing columns are already VARCHAR.
-- ---------------------------------------------------------

ALTER TABLE project_members
    ADD COLUMN IF NOT EXISTS monthly_commitment_amount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS arrears_balance           NUMERIC(19, 2) NOT NULL DEFAULT 0;

-- Partial index helps the monthly GroupDebtAssessmentWorker scan
-- only members currently in arrears.
CREATE INDEX IF NOT EXISTS idx_project_members_arrears
    ON project_members (arrears_balance)
    WHERE arrears_balance > 0;
