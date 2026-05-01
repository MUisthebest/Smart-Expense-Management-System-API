CREATE TABLE project_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid (),
    project_id UUID,
    user_id UUID,
    month INTEGER,
    year INTEGER,
    money_saved_before NUMERIC(19, 2),
    money_saved_after NUMERIC(19, 2),
    monthly_saving NUMERIC(19, 2),
    penalty NUMERIC(19, 2),
    surplus_invested NUMERIC(19, 2),
    month_left_before INTEGER,
    month_left_after INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_project_history_project_id ON project_history (project_id);

CREATE INDEX idx_project_history_user_id ON project_history (user_id);

CREATE INDEX idx_project_history_year_month ON project_history (year, month);