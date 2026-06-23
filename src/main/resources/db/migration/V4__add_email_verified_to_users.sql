-- Add is_email_verified column to users table
ALTER TABLE users ADD COLUMN is_email_verified BOOLEAN NOT NULL DEFAULT false;

-- Update existing users to have email verified = true (assuming they passed OTP verification)
-- If you want different behavior, modify this SQL
UPDATE users SET is_email_verified = false WHERE is_email_verified IS NULL;

-- Create index for better query performance
CREATE INDEX idx_users_is_email_verified ON users(is_email_verified);
