CREATE TABLE project_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    snapshot_date DATE NOT NULL,
    recorded_money_saved NUMERIC(19, 2),
    recorded_money_owed NUMERIC(19, 2),
    monthly_performance_status VARCHAR(20), -- OVERSPENT / DISCIPLINED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_history_project FOREIGN KEY (project_id) REFERENCES projects(id)
);