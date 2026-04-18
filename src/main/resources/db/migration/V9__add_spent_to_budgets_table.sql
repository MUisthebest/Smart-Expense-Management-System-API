-- Add spent column to budgets table
ALTER TABLE budgets ADD COLUMN spent NUMERIC(19, 2) NOT NULL DEFAULT 0;

-- Add check constraint to ensure spent is non-negative and doesn't exceed limit
ALTER TABLE budgets ADD CONSTRAINT ck_spent_positive CHECK (spent >= 0);
ALTER TABLE budgets ADD CONSTRAINT ck_spent_not_exceed_limit CHECK (spent <= amount_limit);
