-- V10: Drop safe spending database triggers and functions to move logic to Java

DROP TRIGGER IF EXISTS trg_user_income_biu_set_safe_spending ON user_income;

DROP TRIGGER IF EXISTS trg_project_tracking_aiud_recalc_user_income ON project_tracking;

DROP TRIGGER IF EXISTS trg_projects_aiud_recalc_user_income ON projects;

DROP FUNCTION IF EXISTS calc_user_safe_spending (UUID, INTEGER);

DROP FUNCTION IF EXISTS trg_user_income_biu_set_safe_spending ();

DROP FUNCTION IF EXISTS refresh_user_safe_spending (UUID);

DROP FUNCTION IF EXISTS trg_project_tracking_recalc_user_income ();

DROP FUNCTION IF EXISTS trg_projects_owner_recalc_user_income ();