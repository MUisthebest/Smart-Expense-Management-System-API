-- Hibernate auto-generates a CHECK constraint (projects_status_check) for the
-- @Enumerated(STRING) projects.status column, listing the enum values that existed
-- when the column was first created. The all-auto tracking work added a new value,
-- EXPIRED (see ProjectStatus), but `ddl-auto: update` never alters an existing check
-- constraint — so settlement's expire() fails with:
--   new row for relation "projects" violates check constraint "projects_status_check"
--
-- Recreate the constraint to allow every current ProjectStatus value.

ALTER TABLE projects DROP CONSTRAINT IF EXISTS projects_status_check;
