-- All-auto project tracking (see docs/project-tracking-all-auto-plan.md)
--
-- 1. frozen_months: consecutive months a project has been FROZEN with no recovery.
--    Settlement increments it each frozen month and EXPIRES the project once it
--    reaches app.project.max-frozen-months. Reset to 0 on freeze/unfreeze.
--
-- 2. No change needed for the new ProjectStatus value EXPIRED — projects.status is
--    a plain VARCHAR(20) with no CHECK constraint, so the enum string fits as-is.

ALTER TABLE project_tracking
    ADD COLUMN IF NOT EXISTS frozen_months INTEGER NOT NULL DEFAULT 0;
