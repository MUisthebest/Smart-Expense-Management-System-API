CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL, -- PERSONAL / GROUP
    name VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE / COMPLETED / CANCELLED
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM', -- LOW / MEDIUM / HIGH
    description TEXT,
    target_amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    deadline DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_projects_owner
        FOREIGN KEY (owner_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_projects_owner_id ON projects(owner_id);
CREATE INDEX idx_projects_type ON projects(type);
CREATE INDEX idx_projects_deadline ON projects(deadline);

CREATE TABLE project_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    user_id UUID NOT NULL,
    join_status VARCHAR(20) NOT NULL,
    is_admin BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_project_members_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_project_members_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_project_member UNIQUE (project_id, user_id)
);

-- Indexes
CREATE INDEX idx_project_members_project_id ON project_members(project_id);
CREATE INDEX idx_project_members_user_id ON project_members(user_id);
CREATE INDEX idx_project_members_join_status ON project_members(join_status);

CREATE TABLE project_contributions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    user_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    note TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_project_contributions_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_project_contributions_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

-- Indexes
CREATE INDEX idx_project_contributions_project_id 
    ON project_contributions(project_id);

CREATE INDEX idx_project_contributions_user_id 
    ON project_contributions(user_id);

CREATE INDEX idx_project_contributions_date 
    ON project_contributions(created_at);

CREATE TABLE project_tracking (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    money_saved NUMERIC(19, 2) NOT NULL DEFAULT 0,
    monthly_saving NUMERIC(19, 2) NOT NULL,
    current_month INTEGER NOT NULL DEFAULT 1,
    month_left INTEGER NOT NULL,
    money_owed NUMERIC(19, 2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_tracking_project
        FOREIGN KEY (project_id)
        REFERENCES projects(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_project_tracking_project UNIQUE (project_id)
);

CREATE INDEX idx_project_tracking_project_id ON project_tracking(project_id);
CREATE INDEX idx_project_tracking_current_month ON project_tracking(current_month);
CREATE INDEX idx_project_tracking_month_left ON project_tracking(month_left);
