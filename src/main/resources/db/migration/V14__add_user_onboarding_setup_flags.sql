ALTER TABLE users
    ADD COLUMN income_setup_completed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN financial_setup_completed BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users u
SET income_setup_completed = TRUE
WHERE EXISTS (
    SELECT 1
    FROM user_income ui
    WHERE ui.user_id = u.id
);

UPDATE users u
SET financial_setup_completed = TRUE
WHERE EXISTS (
    SELECT 1
    FROM user_financial_profiles fp
    WHERE fp.user_id = u.id
);
