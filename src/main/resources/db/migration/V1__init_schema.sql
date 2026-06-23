-- ================================================================
-- V1__init_schema_final.sql
-- Tổng hợp từ ERD thực tế (Hibernate) + FK/Index từ migration
-- Dùng cho schema testing, có prefix tường minh
-- ================================================================

DROP SCHEMA IF EXISTS testing CASCADE;
CREATE SCHEMA testing;
GRANT ALL ON SCHEMA testing TO neondb_owner;
GRANT ALL ON SCHEMA testing TO public;

-- ════════════════════════════════════════════════════════════════
-- 1. USERS
-- Cột: theo ERD thực tế (username VARCHAR(30), thêm 2 cột setup)
-- ════════════════════════════════════════════════════════════════
CREATE TABLE testing.users (
                               id                          UUID             PRIMARY KEY DEFAULT gen_random_uuid(),
                               username                    VARCHAR(30)      UNIQUE NOT NULL,   -- ERD: 30, migration: 20
                               full_name                   VARCHAR(40)      NOT NULL,
                               avatar                      VARCHAR(500),
                               password_hash               VARCHAR(100)     NOT NULL,
                               date_of_birth               DATE             NOT NULL,
                               phone                       VARCHAR(20)      NOT NULL,
                               email                       VARCHAR(30)      UNIQUE NOT NULL,
                               coin                        INTEGER          NOT NULL DEFAULT 0,
                               role                        VARCHAR(10)      NOT NULL DEFAULT 'USER',
                               refresh_token               VARCHAR(500),
                               refresh_token_expiry        TIMESTAMP        WITHOUT TIME ZONE,
                               rate                        DOUBLE PRECISION NOT NULL DEFAULT 5.0,
                               is_email_verified           BOOLEAN          NOT NULL DEFAULT false,
                               notification_enabled        BOOLEAN          NOT NULL DEFAULT true,
                               is_active                   BOOLEAN          NOT NULL DEFAULT true,
                               income_setup_completed      BOOLEAN          NOT NULL DEFAULT false,  -- ERD only
                               financial_setup_completed   BOOLEAN          NOT NULL DEFAULT false,  -- ERD only
                               created_at                  TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               updated_at                  TIMESTAMP        NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               CONSTRAINT chk_role CHECK (role IN ('USER', 'ADMIN'))
);
CREATE INDEX idx_users_email             ON testing.users(email);
CREATE INDEX idx_users_username          ON testing.users(username);
CREATE INDEX idx_users_is_active         ON testing.users(is_active);
CREATE INDEX idx_users_is_email_verified ON testing.users(is_email_verified);


-- ════════════════════════════════════════════════════════════════
-- 2. OTPS
-- ════════════════════════════════════════════════════════════════
CREATE TABLE testing.otps (
                              id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                              email      VARCHAR(100) NOT NULL,
                              otp        VARCHAR(10)  NOT NULL,
                              expired_at TIMESTAMP    NOT NULL,
                              verified   BOOLEAN      NOT NULL DEFAULT false,
                              type       VARCHAR(10)  NOT NULL,
                              CONSTRAINT chk_otp_type CHECK (type IN ('VERIFY', 'UPDATE'))
);
CREATE INDEX idx_otps_email      ON testing.otps(email);
CREATE INDEX idx_otps_expired_at ON testing.otps(expired_at);
CREATE INDEX idx_otps_email_type ON testing.otps(email, type, verified);


-- ════════════════════════════════════════════════════════════════
-- 3. NOTIFICATIONS
-- user_id: NOT NULL (theo ERD thực tế), content VARCHAR(255)
-- ════════════════════════════════════════════════════════════════
CREATE TABLE testing.notifications (
                                       id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                       user_id    UUID         NOT NULL,                -- ERD: NOT NULL
                                       content    VARCHAR(255) NOT NULL,                -- ERD: VARCHAR(255)
                                       created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       CONSTRAINT fk_notifications_user
                                           FOREIGN KEY (user_id) REFERENCES testing.users(id) ON DELETE CASCADE
);
CREATE INDEX idx_notifications_user_created_at ON testing.notifications(user_id, created_at DESC);


-- ════════════════════════════════════════════════════════════════
-- 4. PUSH TOKENS
-- id/user_id: VARCHAR(255) theo ERD thực tế (Hibernate dùng String)
-- ════════════════════════════════════════════════════════════════
CREATE TABLE testing.push_tokens (
                                     id         VARCHAR(255) PRIMARY KEY,             -- ERD: VARCHAR(255)
                                     user_id    VARCHAR(255) NOT NULL,                -- ERD: VARCHAR(255)
                                     push_token VARCHAR(255) NOT NULL UNIQUE,
                                     platform   VARCHAR(255),
                                     created_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                                     updated_at TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
    -- FK không thể add vì user_id là VARCHAR, users.id là UUID
    -- Cần fix entity Java để dùng UUID
);
CREATE INDEX idx_push_tokens_user_id ON testing.push_tokens(user_id);


-- ════════════════════════════════════════════════════════════════
-- 5. TRANSACTIONS
-- ════════════════════════════════════════════════════════════════
CREATE TABLE testing.transactions (
                                      id          UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
                                      user_id     UUID           NOT NULL,
                                      amount      NUMERIC(38, 2) NOT NULL,             -- ERD: 38,2
                                      type        VARCHAR(20)    NOT NULL,
                                      category    VARCHAR(20)    NOT NULL,
                                      description VARCHAR(255),                        -- ERD: VARCHAR(255)
                                      date        TIMESTAMP      NOT NULL,
                                      created_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      updated_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                      CONSTRAINT fk_transactions_user
                                          FOREIGN KEY (user_id) REFERENCES testing.users(id) ON DELETE CASCADE
);
CREATE INDEX idx_transactions_user_id   ON testing.transactions(user_id);
CREATE INDEX idx_transactions_date      ON testing.transactions(date);
CREATE INDEX idx_transactions_type      ON testing.transactions(type);
CREATE INDEX idx_transactions_category  ON testing.transactions(category);
CREATE INDEX idx_transactions_user_date ON testing.transactions(user_id, date);


-- ════════════════════════════════════════════════════════════════
-- 6. BUDGETS
-- ════════════════════════════════════════════════════════════════
CREATE TABLE testing.budgets (
                                 id           UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
                                 user_id      UUID           NOT NULL,
                                 category     VARCHAR(50)    NOT NULL,
                                 amount_limit NUMERIC(38, 2) NOT NULL,             -- ERD: 38,2
                                 spent        NUMERIC(38, 2) NOT NULL DEFAULT 0,   -- ERD: 38,2
                                 month        INT            NOT NULL,
                                 year         INT            NOT NULL,
                                 created_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 updated_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 CONSTRAINT fk_budgets_user          FOREIGN KEY (user_id) REFERENCES testing.users(id) ON DELETE CASCADE,
                                 CONSTRAINT ck_month_valid           CHECK (month >= 1 AND month <= 12),
                                 CONSTRAINT ck_year_valid            CHECK (year > 1900),
                                 CONSTRAINT ck_amount_limit_positive CHECK (amount_limit > 0),
                                 CONSTRAINT ck_spent_positive        CHECK (spent >= 0),
                                 CONSTRAINT uk_budget_unique         UNIQUE (user_id, category, month, year)
);
CREATE INDEX idx_budgets_user_id         ON testing.budgets(user_id);
CREATE INDEX idx_budgets_month_year      ON testing.budgets(month, year);
CREATE INDEX idx_budgets_user_month_year ON testing.budgets(user_id, month, year);
CREATE INDEX idx_budgets_category        ON testing.budgets(category);


-- ════════════════════════════════════════════════════════════════
-- 7. USER FINANCIAL PROFILES
-- VARCHAR(255) theo ERD thực tế
-- ════════════════════════════════════════════════════════════════
CREATE TABLE testing.user_financial_profiles (
                                                 id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                                 user_id         UUID         NOT NULL UNIQUE,
                                                 role            VARCHAR(255) NOT NULL,            -- ERD: 255
                                                 living_status   VARCHAR(255) NOT NULL,
                                                 income_level    VARCHAR(255) NOT NULL,
                                                 transport_mode  VARCHAR(255) NOT NULL,
                                                 spending_style  VARCHAR(255) NOT NULL,
                                                 work_style      VARCHAR(255) NOT NULL,
                                                 family_status   VARCHAR(255) NOT NULL,
                                                 study_intensity VARCHAR(255) NOT NULL,
                                                 health_need     VARCHAR(255) NOT NULL,
                                                 created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 CONSTRAINT fk_user_financial_profiles_user
                                                     FOREIGN KEY (user_id) REFERENCES testing.users(id) ON DELETE CASCADE
);
CREATE INDEX idx_user_financial_profiles_user_id ON testing.user_financial_profiles(user_id);


-- ════════════════════════════════════════════════════════════════
-- 8. USER INCOME
-- net/usable/safe: INTEGER theo ERD thực tế (Hibernate mapping)
-- ════════════════════════════════════════════════════════════════
CREATE TABLE testing.user_income (
                                     id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                                     user_id             UUID         NOT NULL UNIQUE,
                                     net_income          INTEGER,                      -- ERD: INTEGER
                                     usable_income       INTEGER,                      -- ERD: INTEGER
                                     safe_spending       INTEGER,                      -- ERD: INTEGER
                                     currency            VARCHAR(10)  NOT NULL DEFAULT 'VND',
                                     auto_invest_surplus BOOLEAN      DEFAULT FALSE,
                                     calculation_note    VARCHAR(255),
                                     last_calculated_at  TIMESTAMP WITH TIME ZONE,
                                     created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     CONSTRAINT fk_user_income_user_id
                                         FOREIGN KEY (user_id) REFERENCES testing.users(id) ON DELETE CASCADE
);
CREATE INDEX idx_user_income_user_id            ON testing.user_income(user_id);
CREATE INDEX idx_user_income_usable_income      ON testing.user_income(usable_income);
CREATE INDEX idx_user_income_last_calculated_at ON testing.user_income(last_calculated_at);


-- ════════════════════════════════════════════════════════════════
-- 9. PROJECTS
-- deadline NOT NULL, description VARCHAR(500), name VARCHAR(120)
-- ════════════════════════════════════════════════════════════════
CREATE TABLE testing.projects (
                                  id            UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
                                  owner_id      UUID           NOT NULL,
                                  type          VARCHAR(20)    NOT NULL,
                                  name          VARCHAR(120)   NOT NULL,            -- ERD: 120
                                  status        VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
                                  priority      VARCHAR(20)    NOT NULL DEFAULT 'MEDIUM',
                                  description   VARCHAR(500),                       -- ERD: VARCHAR(500)
                                  target_amount NUMERIC(19, 2) NOT NULL,
                                  currency      VARCHAR(10)    NOT NULL,
                                  deadline      DATE           NOT NULL,            -- ERD: NOT NULL
                                  created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  CONSTRAINT fk_projects_owner
                                      FOREIGN KEY (owner_id) REFERENCES testing.users(id) ON DELETE CASCADE
);
CREATE INDEX idx_projects_owner_id ON testing.projects(owner_id);
CREATE INDEX idx_projects_type     ON testing.projects(type);
CREATE INDEX idx_projects_deadline ON testing.projects(deadline);


-- ════════════════════════════════════════════════════════════════
-- 10. PROJECT MEMBERS
-- timestamps WITH TIME ZONE theo ERD
-- ════════════════════════════════════════════════════════════════
CREATE TABLE testing.project_members (
                                         id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                         project_id  UUID        NOT NULL,
                                         user_id     UUID        NOT NULL,
                                         join_status VARCHAR(20) NOT NULL,
                                         is_admin    BOOLEAN     NOT NULL DEFAULT FALSE,
                                         created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                         CONSTRAINT fk_project_members_project
                                             FOREIGN KEY (project_id) REFERENCES testing.projects(id) ON DELETE CASCADE,
                                         CONSTRAINT fk_project_members_user
                                             FOREIGN KEY (user_id) REFERENCES testing.users(id) ON DELETE CASCADE,
                                         CONSTRAINT uk_project_member UNIQUE (project_id, user_id)
);
CREATE INDEX idx_project_members_project_id  ON testing.project_members(project_id);
CREATE INDEX idx_project_members_user_id     ON testing.project_members(user_id);
CREATE INDEX idx_project_members_join_status ON testing.project_members(join_status);


-- ════════════════════════════════════════════════════════════════
-- 11. PROJECT CONTRIBUTIONS
-- note VARCHAR(255), created_at WITH TIME ZONE theo ERD
-- ════════════════════════════════════════════════════════════════
CREATE TABLE testing.project_contributions (
                                               id         UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
                                               project_id UUID           NOT NULL,
                                               user_id    UUID           NOT NULL,
                                               amount     NUMERIC(19, 2) NOT NULL,
                                               currency   VARCHAR(10)    NOT NULL,
                                               note       VARCHAR(255),                          -- ERD: VARCHAR(255)
                                               created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                               CONSTRAINT fk_project_contributions_project
                                                   FOREIGN KEY (project_id) REFERENCES testing.projects(id) ON DELETE CASCADE,
                                               CONSTRAINT fk_project_contributions_user
                                                   FOREIGN KEY (user_id) REFERENCES testing.users(id) ON DELETE CASCADE
);
CREATE INDEX idx_project_contributions_project_id ON testing.project_contributions(project_id);
CREATE INDEX idx_project_contributions_user_id    ON testing.project_contributions(user_id);
CREATE INDEX idx_project_contributions_date       ON testing.project_contributions(created_at);


-- ════════════════════════════════════════════════════════════════
-- 12. PROJECT TRACKING
-- timestamps WITH TIME ZONE theo ERD
-- ════════════════════════════════════════════════════════════════
CREATE TABLE testing.project_tracking (
                                          id             UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
                                          project_id     UUID           NOT NULL,
                                          money_saved    NUMERIC(19, 2) NOT NULL DEFAULT 0,
                                          monthly_saving NUMERIC(19, 2) NOT NULL,
                                          current_month  INTEGER        NOT NULL DEFAULT 1,
                                          month_left     INTEGER        NOT NULL,
                                          money_owed     NUMERIC(19, 2) NOT NULL DEFAULT 0,
                                          created_at     TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                          updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          CONSTRAINT fk_project_tracking_project
                                              FOREIGN KEY (project_id) REFERENCES testing.projects(id) ON DELETE CASCADE,
                                          CONSTRAINT uk_project_tracking_project UNIQUE (project_id)
);
CREATE INDEX idx_project_tracking_project_id    ON testing.project_tracking(project_id);
CREATE INDEX idx_project_tracking_current_month ON testing.project_tracking(current_month);
CREATE INDEX idx_project_tracking_month_left    ON testing.project_tracking(month_left);


-- ════════════════════════════════════════════════════════════════
-- 13. PROJECT HISTORY
-- timestamps WITH TIME ZONE theo ERD
-- ════════════════════════════════════════════════════════════════
CREATE TABLE testing.project_history (
                                         id                 UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
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
                                         created_at         TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                                         CONSTRAINT fk_project_history_project
                                             FOREIGN KEY (project_id) REFERENCES testing.projects(id) ON DELETE SET NULL,
                                         CONSTRAINT fk_project_history_user
                                             FOREIGN KEY (user_id) REFERENCES testing.users(id) ON DELETE SET NULL
);
CREATE INDEX idx_project_history_project_id ON testing.project_history(project_id);
CREATE INDEX idx_project_history_user_id    ON testing.project_history(user_id);
CREATE INDEX idx_project_history_year_month ON testing.project_history(year, month);