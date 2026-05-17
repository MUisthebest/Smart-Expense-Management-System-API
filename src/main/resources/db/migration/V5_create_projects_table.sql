CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL, -- PERSONAL / GROUP
    name VARCHAR(255) NOT NULL,
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


CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL, -- PERSONAL / GROUP
    name VARCHAR(255) NOT NULL,
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