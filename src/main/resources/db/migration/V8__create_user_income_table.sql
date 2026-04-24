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

    CONSTRAINT fk_user_income_user
        FOREIGN KEY (id)
        REFERENCES users(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_user_income_usable_income ON user_income(usable_income);
CREATE INDEX idx_user_income_last_calculated_at ON user_income(last_calculated_at);

CREATE OR REPLACE FUNCTION calc_user_safe_spending(p_user_id UUID, p_usable_income INTEGER)
RETURNS INTEGER AS $$
DECLARE
    v_monthly_obligation NUMERIC(19, 2);
    v_cap INTEGER;
    v_candidate INTEGER;
BEGIN
    IF p_usable_income IS NULL OR p_usable_income <= 0 THEN
        RETURN 0;
    END IF;

    SELECT COALESCE(
        SUM(pt.monthly_saving),
        0
    )
    INTO v_monthly_obligation
    FROM projects p
    JOIN project_tracking pt ON pt.project_id = p.id
    WHERE p.owner_id = p_user_id;

    -- Strictly less than 80% of usable_income.
    v_cap := GREATEST(0, FLOOR(p_usable_income * 0.8)::INTEGER - 1);

    -- Reserve monthly obligation from tracking data.
    v_candidate := GREATEST(0, p_usable_income - FLOOR(v_monthly_obligation)::INTEGER);

    RETURN LEAST(v_cap, v_candidate);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION trg_user_income_biu_set_safe_spending()
RETURNS TRIGGER AS $$
BEGIN
    NEW.safe_spending := calc_user_safe_spending(NEW.id, NEW.usable_income);
    NEW.last_calculated_at := CURRENT_TIMESTAMP;
    NEW.updated_at := CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_user_income_biu_set_safe_spending
BEFORE INSERT OR UPDATE OF net_income, usable_income, currency, calculation_note
ON user_income
FOR EACH ROW
EXECUTE FUNCTION trg_user_income_biu_set_safe_spending();

CREATE OR REPLACE FUNCTION refresh_user_safe_spending(p_user_id UUID)
RETURNS VOID AS $$
DECLARE
    v_usable_income INTEGER;
BEGIN
    SELECT usable_income
    INTO v_usable_income
    FROM user_income
    WHERE id = p_user_id;

    IF NOT FOUND THEN
        RETURN;
    END IF;

    UPDATE user_income ui
    SET safe_spending = calc_user_safe_spending(ui.id, v_usable_income),
        last_calculated_at = CURRENT_TIMESTAMP,
        updated_at = CURRENT_TIMESTAMP
    WHERE ui.id = p_user_id;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION trg_project_tracking_recalc_user_income()
RETURNS TRIGGER AS $$
DECLARE
    v_project_id UUID;
    v_owner_id UUID;
BEGIN
    v_project_id := COALESCE(NEW.project_id, OLD.project_id);

    SELECT p.owner_id
    INTO v_owner_id
    FROM projects p
    WHERE p.id = v_project_id;

    IF v_owner_id IS NOT NULL THEN
        PERFORM refresh_user_safe_spending(v_owner_id);
    END IF;

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_project_tracking_aiud_recalc_user_income
AFTER INSERT OR UPDATE OR DELETE ON project_tracking
FOR EACH ROW
EXECUTE FUNCTION trg_project_tracking_recalc_user_income();

CREATE OR REPLACE FUNCTION trg_projects_owner_recalc_user_income()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        PERFORM refresh_user_safe_spending(NEW.owner_id);
        RETURN NEW;
    ELSIF TG_OP = 'UPDATE' THEN
        PERFORM refresh_user_safe_spending(OLD.owner_id);
        PERFORM refresh_user_safe_spending(NEW.owner_id);
        RETURN NEW;
    ELSE
        PERFORM refresh_user_safe_spending(OLD.owner_id);
        RETURN OLD;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_projects_aiud_recalc_user_income
AFTER INSERT OR UPDATE OR DELETE ON projects
FOR EACH ROW
EXECUTE FUNCTION trg_projects_owner_recalc_user_income();
