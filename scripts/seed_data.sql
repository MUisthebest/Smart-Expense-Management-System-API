-- SQL Seed Data Script for Smart Expense Management System
-- This script inserts mock records for testing all features including group projects, budgets, and user financial profiles.
-- Recommended usage: Execute this script on your local/test PostgreSQL instance.

-- =========================================================================
-- 1. CLEAN EXISTING DATA (Optional, execute if you want a clean slate)
-- =========================================================================
-- TRUNCATE TABLE project_history, project_tracking, project_contributions, project_members, projects, budgets, transactions, user_income, user_financial_profiles, push_tokens, notifications, otps, users CASCADE;

-- =========================================================================
-- 2. SEED USERS
-- =========================================================================
-- Password hashes correspond to bcrypt encoded text: 'password123'
INSERT INTO users (id, username, full_name, avatar, password_hash, date_of_birth, phone, email, coin, role, is_active, rate, created_at, updated_at, is_email_verified, notification_enabled)
VALUES 
  ('11111111-1111-1111-1111-111111111111', 'alice_admin', 'Alice Admin', 'https://avatar.iran.liara.run/public/girl?username=alice', '$2a$10$dXJ3SWoKu998e3.6E3K55eG9e5UoD27FqR9W14e.1wR/53h5o857K', '1995-05-15', '0912345678', 'alice@example.com', 100, 'ADMIN', true, 5.0, NOW(), NOW(), true, true),
  ('22222222-2222-2222-2222-222222222222', 'bob_member', 'Bob Member', 'https://avatar.iran.liara.run/public/boy?username=bob', '$2a$10$dXJ3SWoKu998e3.6E3K55eG9e5UoD27FqR9W14e.1wR/53h5o857K', '1998-08-20', '0987654321', 'bob@example.com', 50, 'USER', true, 5.0, NOW(), NOW(), true, true),
  ('33333333-3333-3333-3333-333333333333', 'charlie_member', 'Charlie Member', 'https://avatar.iran.liara.run/public/boy?username=charlie', '$2a$10$dXJ3SWoKu998e3.6E3K55eG9e5UoD27FqR9W14e.1wR/53h5o857K', '2000-01-10', '0909090909', 'charlie@example.com', 0, 'USER', true, 5.0, NOW(), NOW(), false, true);

-- =========================================================================
-- 3. SEED USER FINANCIAL PROFILES
-- =========================================================================
INSERT INTO user_financial_profiles (id, user_id, role, living_status, income_level, transport_mode, spending_style, work_style, family_status, study_intensity, health_need, created_at, updated_at)
VALUES
  ('a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1', '11111111-1111-1111-1111-111111111111', 'OFFICE_WORKER', 'RENT_ROOM', 'HIGH', 'MOTORBIKE', 'FRUGAL', 'ONSITE', 'SINGLE', 'NORMAL', 'LOW', NOW(), NOW()),
  ('b2b2b2b2-b2b2-b2b2-b2b2-b2b2b2b2b2b2', '22222222-2222-2222-2222-222222222222', 'STUDENT', 'WITH_FAMILY', 'LOW', 'BUS', 'BALANCED', 'HYBRID', 'SINGLE', 'COURSE_HEAVY', 'LOW', NOW(), NOW());

-- =========================================================================
-- 4. SEED USER INCOMES
-- =========================================================================
INSERT INTO user_income (id, net_income, usable_income, safe_spending, currency, auto_invest_surplus, calculation_note, last_calculated_at, created_at, updated_at)
VALUES
  ('11111111-1111-1111-1111-111111111111', 20000000.00, 15000000.00, 500000.00, 'VND', true, 'Salary calculation', NOW(), NOW(), NOW()),
  ('22222222-2222-2222-2222-222222222222', 8000000.00, 6000000.00, 200000.00, 'VND', false, 'Part-time job income', NOW(), NOW(), NOW());

-- =========================================================================
-- 5. SEED PROJECTS
-- =========================================================================
INSERT INTO projects (id, owner_id, type, name, status, priority, description, target_amount, currency, deadline, created_at, updated_at)
VALUES
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'GROUP', 'Vacation Fund 2026', 'ACTIVE', 'HIGH', 'Shared savings for team trip to Da Nang', 15000000.00, 'VND', '2026-12-31', NOW(), NOW()),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', 'PERSONAL', 'New M4 MacBook Pro', 'ACTIVE', 'MEDIUM', 'Saving for personal development tool upgrading', 35000000.00, 'VND', '2026-09-30', NOW(), NOW());

-- =========================================================================
-- 6. SEED PROJECT MEMBERS
-- =========================================================================
INSERT INTO project_members (id, project_id, user_id, join_status, is_admin, created_at, updated_at)
VALUES
  ('e1e1e1e1-e1e1-e1e1-e1e1-e1e1e1e1e1e1', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 'JOINED', true, NOW(), NOW()),
  ('e2e2e2e2-e2e2-e2e2-e2e2-e2e2e2e2e2e2', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '22222222-2222-2222-2222-222222222222', 'JOINED', false, NOW(), NOW());

-- =========================================================================
-- 7. SEED PROJECT TRACKING
-- =========================================================================
INSERT INTO project_tracking (id, project_id, money_saved, monthly_saving, current_month, month_left, money_owed, created_at, updated_at, version)
VALUES
  ('c1c1c1c1-c1c1-c1c1-c1c1-c1c1c1c1c1c1', 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 3000000.00, 1500000.00, 2, 8, 0.00, NOW(), NOW(), 0),
  ('c2c2c2c2-c2c2-c2c2-c2c2-c2c2c2c2c2c2', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 10000000.00, 3000000.00, 3, 5, 0.00, NOW(), NOW(), 0);

-- =========================================================================
-- 8. SEED PROJECT CONTRIBUTIONS
-- =========================================================================
INSERT INTO project_contributions (id, project_id, user_id, amount, currency, note, created_at)
VALUES
  (gen_random_uuid(), 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 2000000.00, 'VND', 'Initial deposit by Alice', NOW() - INTERVAL '15 days'),
  (gen_random_uuid(), 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '22222222-2222-2222-2222-222222222222', 1000000.00, 'VND', 'Bob first month commitment', NOW() - INTERVAL '10 days'),
  (gen_random_uuid(), 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', 10000000.00, 'VND', 'Self contribution for MacBook Pro', NOW() - INTERVAL '30 days');

-- =========================================================================
-- 9. SEED PROJECT SETTLEMENT HISTORY
-- =========================================================================
INSERT INTO project_history (id, project_id, user_id, month, year, money_saved_before, money_saved_after, monthly_saving, penalty, surplus_invested, month_left_before, month_left_after, created_at)
VALUES
  (gen_random_uuid(), 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', 5, 2026, 1000000.00, 3000000.00, 2000000.00, 0.00, 0.00, 9, 8, NOW() - INTERVAL '30 days');

-- =========================================================================
-- 10. SEED BUDGETS
-- =========================================================================
INSERT INTO budgets (id, user_id, category, amount_limit, spent, month, year, created_at, updated_at)
VALUES
  (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'FOOD', 5000000.00, 1200000.00, 6, 2026, NOW(), NOW()),
  (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'TRANSPORTATION', 1500000.00, 350000.00, 6, 2026, NOW(), NOW()),
  (gen_random_uuid(), '22222222-2222-2222-2222-222222222222', 'FOOD', 3000000.00, 2500000.00, 6, 2026, NOW(), NOW());

-- =========================================================================
-- 11. SEED TRANSACTIONS
-- =========================================================================
INSERT INTO transactions (id, user_id, amount, type, category, description, date, created_at, updated_at)
VALUES
  (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 120000.00, 'EXPENSE', 'FOOD', 'Lunch with team', NOW() - INTERVAL '1 day', NOW(), NOW()),
  (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 25000000.00, 'INCOME', 'OTHER', 'Monthly net salary payout', NOW() - INTERVAL '10 days', NOW(), NOW()),
  (gen_random_uuid(), '22222222-2222-2222-2222-222222222222', 80000.00, 'EXPENSE', 'FOOD', 'Pho breakfast', NOW() - INTERVAL '2 days', NOW(), NOW());

-- =========================================================================
-- 12. SEED NOTIFICATIONS & OTPS
-- =========================================================================
INSERT INTO notifications (id, user_id, content, created_at)
VALUES
  (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'Welcome to Smart Expense Management! Set up your financial profile to begin.', NOW() - INTERVAL '5 days'),
  (gen_random_uuid(), '22222222-2222-2222-2222-222222222222', 'You have been added as a member of Vacation Fund 2026.', NOW() - INTERVAL '10 days');

INSERT INTO otps (id, email, otp, expired_at, verified, type)
VALUES
  (gen_random_uuid(), 'charlie@example.com', '123456', NOW() + INTERVAL '5 minutes', false, 'VERIFY');

INSERT INTO push_tokens (id, user_id, push_token, platform, created_at, updated_at)
VALUES
  (gen_random_uuid(), '11111111-1111-1111-1111-111111111111', 'ExponentPushToken[AlicePushTokenFake123]', 'ios', NOW(), NOW()),
  (gen_random_uuid(), '22222222-2222-2222-2222-222222222222', 'ExponentPushToken[BobPushTokenFake789]', 'android', NOW(), NOW());
