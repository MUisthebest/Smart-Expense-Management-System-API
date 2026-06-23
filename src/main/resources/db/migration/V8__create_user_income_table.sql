CREATE TABLE user_income (
    id UUID PRIMARY KEY,
    net_income NUMERIC(19, 2),
    usable_income NUMERIC(19, 2),
    safe_spending NUMERIC(19, 2),
    currency VARCHAR(10) NOT NULL DEFAULT 'VND',
    -- NEW: Defines if underspending goes to projects or stays in pocket
    auto_invest_surplus BOOLEAN DEFAULT FALSE,
    calculation_note VARCHAR(255),
    last_calculated_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_income_user FOREIGN KEY (id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_income_usable_income ON user_income (usable_income);

CREATE INDEX idx_user_income_last_calculated_at ON user_income (last_calculated_at);