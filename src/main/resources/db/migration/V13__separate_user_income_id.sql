-- Add user_id column to hold the reference to users(id)
ALTER TABLE user_income ADD COLUMN user_id UUID;

-- Populate user_id from current id values (id was previously the user FK)
UPDATE user_income SET user_id = id;

-- Make user_id NOT NULL
ALTER TABLE user_income ALTER COLUMN user_id SET NOT NULL;

-- Drop the old FK constraint that tied id -> users(id)
ALTER TABLE user_income DROP CONSTRAINT fk_user_income_user;

-- Regenerate id as independent UUIDs
UPDATE user_income SET id = gen_random_uuid();

-- Add FK constraint on user_id -> users(id)
ALTER TABLE user_income
    ADD CONSTRAINT fk_user_income_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

-- Enforce one income record per user
ALTER TABLE user_income ADD CONSTRAINT uq_user_income_user_id UNIQUE (user_id);

CREATE INDEX idx_user_income_user_id ON user_income (user_id);
