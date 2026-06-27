-- Phase 1: retire old GROUP project data and drop project_members
UPDATE projects SET status = 'ABANDONED' WHERE type = 'GROUP';
DROP TABLE IF EXISTS project_members;

-- Phase 2: group lobby
CREATE TABLE groups (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    admin_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status      VARCHAR(20) NOT NULL DEFAULT 'FORMING',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_groups_admin_id ON groups(admin_id);

-- Phase 3: group membership
CREATE TABLE group_members (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id          UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id           UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role              VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    invite_status     VARCHAR(20) NOT NULL DEFAULT 'INVITED',
    capacity_snapshot NUMERIC(19,2),
    joined_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_group_member UNIQUE (group_id, user_id)
);
CREATE INDEX idx_group_members_group_id ON group_members(group_id);
CREATE INDEX idx_group_members_user_id  ON group_members(user_id);

-- Phase 4: group-level project (separate table, admin creates after lock)
CREATE TABLE group_projects (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id        UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    target_amount   NUMERIC(19,2) NOT NULL,
    total_capacity  NUMERIC(19,2) NOT NULL,
    currency        VARCHAR(10)   NOT NULL,
    total_months    INTEGER       NOT NULL,
    deadline        DATE          NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_group_project_group UNIQUE (group_id)
);
CREATE INDEX idx_group_projects_group_id ON group_projects(group_id);

-- Phase 5: link personal sub-projects back to their group project
ALTER TABLE projects
    ADD COLUMN group_project_id UUID REFERENCES group_projects(id) ON DELETE SET NULL;
CREATE INDEX idx_projects_group_project_id ON projects(group_project_id);
