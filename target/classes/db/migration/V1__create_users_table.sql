-- Create users table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(20) UNIQUE NOT NULL,
    full_name VARCHAR(40) NOT NULL,
    avatar VARCHAR(500),
    password_hash VARCHAR(100) NOT NULL,
    date_of_birth DATE NOT NULL,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(30) UNIQUE NOT NULL,
    coin INTEGER NOT NULL DEFAULT 0,
    role VARCHAR(10) NOT NULL DEFAULT 'USER',
    refresh_token VARCHAR(500),
    refresh_token_expiry TIMESTAMP,
    rate DOUBLE PRECISION NOT NULL DEFAULT 5.0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT true,
    
    CONSTRAINT chk_role CHECK (role IN ('USER', 'ADMIN'))
);

-- Indexes for performance
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_is_active ON users(is_active);
