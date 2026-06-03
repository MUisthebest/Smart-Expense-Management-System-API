CREATE TABLE user_financial_profiles (
     id UUID PRIMARY KEY,
     user_id UUID NOT NULL UNIQUE,

     role VARCHAR(50) NOT NULL,
     living_status VARCHAR(50) NOT NULL,
     income_level VARCHAR(50) NOT NULL,
     transport_mode VARCHAR(50) NOT NULL,
     spending_style VARCHAR(50) NOT NULL,
     work_style VARCHAR(50) NOT NULL,
     family_status VARCHAR(50) NOT NULL,
     study_intensity VARCHAR(50) NOT NULL,
     health_need VARCHAR(50) NOT NULL,

     created_at TIMESTAMP NOT NULL,
     updated_at TIMESTAMP NOT NULL
);