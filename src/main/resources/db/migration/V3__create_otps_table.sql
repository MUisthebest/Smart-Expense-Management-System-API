-- Create otps table
CREATE TABLE otps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(100) NOT NULL,
    otp VARCHAR(10) NOT NULL,
    expired_at TIMESTAMP NOT NULL,
    verified BOOLEAN NOT NULL DEFAULT false,
    type VARCHAR(10) NOT NULL,
    
    CONSTRAINT chk_otp_type CHECK (type IN ('VERIFY', 'UPDATE'))
);

-- Indexes for performance
CREATE INDEX idx_otps_email ON otps(email);
CREATE INDEX idx_otps_expired_at ON otps(expired_at);
CREATE INDEX idx_otps_email_type ON otps(email, type, verified);
