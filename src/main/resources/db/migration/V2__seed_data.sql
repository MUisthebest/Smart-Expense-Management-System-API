-- ================================================================
-- V2__seed_data.sql  (revised)
-- Seed data for SmartMoney - public Schema
-- Password: test@123  |  Hash: $2b$10$yd1sHZ7crglVrsq4DpqmNOYvWADdarz0TuSmP5ujL8HFGb7joKBw.
-- Personal project targets: 1-10M VND  |  Descriptions: realistic English
-- ================================================================

-- ════════════════════════════════════════════════════════════
-- 1. USERS
-- ════════════════════════════════════════════════════════════
INSERT INTO public.users (
    id, username, full_name, avatar, password_hash,
    date_of_birth, phone, email, coin, role,
    rate, is_email_verified, notification_enabled, is_active,
    income_setup_completed, financial_setup_completed,
    created_at, updated_at
) VALUES
      ('a1b2c3d4-0001-0001-0001-000000000001','nnminh22','Nguyen Nhat Minh',NULL,
       '$2b$10$yd1sHZ7crglVrsq4DpqmNOYvWADdarz0TuSmP5ujL8HFGb7joKBw.',
       '2004-03-15','0901234561','nnminh22@clc.fitus.edu.vn',150,'USER',
       4.8,TRUE,TRUE,TRUE,TRUE,TRUE,'2026-04-01 08:00:00','2026-04-01 08:00:00'),
      ('a1b2c3d4-0002-0002-0002-000000000002','nhntran22','Nguyen Hoang Nam Tran',NULL,
       '$2b$10$yd1sHZ7crglVrsq4DpqmNOYvWADdarz0TuSmP5ujL8HFGb7joKBw.',
       '2004-07-20','0901234562','nhntran22@clc.fitus.edu.vn',200,'USER',
       4.9,TRUE,TRUE,TRUE,TRUE,TRUE,'2026-04-02 09:00:00','2026-04-02 09:00:00'),
      ('a1b2c3d4-0003-0003-0003-000000000003','vtvuong22','Vo Thinh Vuong',NULL,
       '$2b$10$yd1sHZ7crglVrsq4DpqmNOYvWADdarz0TuSmP5ujL8HFGb7joKBw.',
       '2004-11-05','0901234563','vtvuong22@clc.fitus.edu.vn',80,'USER',
       4.5,TRUE,TRUE,TRUE,TRUE,TRUE,'2026-04-03 10:00:00','2026-04-03 10:00:00'),
      ('a1b2c3d4-0004-0004-0004-000000000004','nhbang22','Ngo Hai Bang',NULL,
       '$2b$10$yd1sHZ7crglVrsq4DpqmNOYvWADdarz0TuSmP5ujL8HFGb7joKBw.',
       '2004-05-28','0901234564','nhbang22@clc.fitus.edu.vn',120,'USER',
       5.0,TRUE,TRUE,TRUE,TRUE,TRUE,'2026-04-04 11:00:00','2026-04-04 11:00:00');

-- ════════════════════════════════════════════════════════════
-- 2. USER FINANCIAL PROFILES
-- ════════════════════════════════════════════════════════════
INSERT INTO public.user_financial_profiles (
    id, user_id, role, living_status, income_level,
    transport_mode, spending_style, work_style,
    family_status, study_intensity, health_need,
    created_at, updated_at
) VALUES
      (gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001',
       'STUDENT_PART_TIME','RENTING','LOW','MOTORBIKE','MODERATE','HYBRID','SINGLE','HIGH','LOW',
       '2026-04-01 08:30:00+07','2026-04-01 08:30:00+07'),
      (gen_random_uuid(),'a1b2c3d4-0002-0002-0002-000000000002',
       'STUDENT','DORMITORY','LOW','BICYCLE','FRUGAL','REMOTE','SINGLE','VERY_HIGH','MODERATE',
       '2026-04-02 09:30:00+07','2026-04-02 09:30:00+07'),
      (gen_random_uuid(),'a1b2c3d4-0003-0003-0003-000000000003',
       'STUDENT_PART_TIME','WITH_FAMILY','MEDIUM','BUS','MODERATE','PART_TIME','SINGLE','HIGH','LOW',
       '2026-04-03 10:30:00+07','2026-04-03 10:30:00+07'),
      (gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004',
       'STUDENT','RENTING','MEDIUM','MOTORBIKE','FLEXIBLE','HYBRID','SINGLE','HIGH','LOW',
       '2026-04-04 11:30:00+07','2026-04-04 11:30:00+07');

-- ════════════════════════════════════════════════════════════
-- 3. USER INCOME
-- ════════════════════════════════════════════════════════════
INSERT INTO public.user_income (
    id, user_id, net_income, usable_income, safe_spending,
    currency, auto_invest_surplus, calculation_note,
    last_calculated_at, created_at, updated_at
) VALUES
      (gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001',
       7000000,5500000,4000000,'VND',FALSE,
       'IT freelance contracts average 5M/month plus 2M family allowance. Fixed deductions: 2.5M rent, 100K utilities.',
       '2026-04-05 10:00:00+07','2026-04-05 10:00:00+07','2026-05-05 10:00:00+07'),
      (gen_random_uuid(),'a1b2c3d4-0002-0002-0002-000000000002',
       4500000,3500000,2500000,'VND',TRUE,
       'Full scholarship 2.5M plus 2M family allowance. Fixed deductions: 800K dorm fee, 100K utilities. Savings-first approach.',
       '2026-04-06 10:00:00+07','2026-04-06 10:00:00+07','2026-05-06 10:00:00+07'),
      (gen_random_uuid(),'a1b2c3d4-0003-0003-0003-000000000003',
       6500000,5000000,3500000,'VND',FALSE,
       'Part-time cafe wage 3.5M plus 3M family allowance. No rent cost (living at home). Main expenses: food, transport, education.',
       '2026-04-07 10:00:00+07','2026-04-07 10:00:00+07','2026-05-07 10:00:00+07'),
      (gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004',
       6000000,4800000,3500000,'VND',FALSE,
       'Part-time programming contracts 3.5M plus 2.5M family allowance. Fixed deductions: 2.2M rent, 120K utilities.',
       '2026-04-08 10:00:00+07','2026-04-08 10:00:00+07','2026-05-08 10:00:00+07');

-- ════════════════════════════════════════════════════════════
-- 4. TRANSACTIONS - USER 1: Nguyen Nhat Minh (50 rows)
-- ════════════════════════════════════════════════════════════
INSERT INTO public.transactions (id, user_id, amount, type, category, description, date, created_at, updated_at) VALUES
                                                                                                                      ('e1000001-0000-0000-0000-000000000001','a1b2c3d4-0001-0001-0001-000000000001',7000000,'INCOME','OTHER','Monthly freelance payment from client - React dashboard project + family allowance','2026-04-01 09:00:00','2026-04-01 09:00:00','2026-04-01 09:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000002','a1b2c3d4-0001-0001-0001-000000000001',2500000,'EXPENSE','UTILITIES','Monthly room rent - April - Binh Thanh district apartment','2026-04-03 10:00:00','2026-04-03 10:00:00','2026-04-03 10:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000003','a1b2c3d4-0001-0001-0001-000000000001',85000,'EXPENSE','FOOD','Buy combo at KFC - 2 pieces of spicy chicken + large fries + Pepsi','2026-04-04 12:30:00','2026-04-04 12:30:00','2026-04-04 12:30:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000004','a1b2c3d4-0001-0001-0001-000000000001',650000,'EXPENSE','EDUCATION','Enroll in university elective course - Software Engineering semester 2 fee','2026-04-05 08:00:00','2026-04-05 08:00:00','2026-04-05 08:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000005','a1b2c3d4-0001-0001-0001-000000000001',120000,'EXPENSE','TRANSPORTATION','Fill up 5L petrol at Petrolimex station - 24,000 VND/liter','2026-04-06 07:30:00','2026-04-06 07:30:00','2026-04-06 07:30:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000006','a1b2c3d4-0001-0001-0001-000000000001',75000,'EXPENSE','FOOD','Dinner at Pho 24 with friends - 1 large bowl beef pho + herbal tea','2026-04-07 19:00:00','2026-04-07 19:00:00','2026-04-07 19:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000007','a1b2c3d4-0001-0001-0001-000000000001',350000,'EXPENSE','CLOTHING','Buy 2 graphic T-shirts at Uniqlo - end-of-season 30% discount','2026-04-08 16:00:00','2026-04-08 16:00:00','2026-04-08 16:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000008','a1b2c3d4-0001-0001-0001-000000000001',90000,'EXPENSE','FOOD','Weekly grocery at Co.opMart - eggs, instant noodles, vegetables','2026-04-09 08:00:00','2026-04-09 08:00:00','2026-04-09 08:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000009','a1b2c3d4-0001-0001-0001-000000000001',200000,'EXPENSE','ENTERTAINMENT','Buy 2 CGV tickets - Spider-Man: Beyond the Spider-Verse screening','2026-04-12 20:00:00','2026-04-12 20:00:00','2026-04-12 20:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000010','a1b2c3d4-0001-0001-0001-000000000001',450000,'INCOME','OTHER','Fix laptop motherboard for a classmate - 2-hour hardware repair job','2026-04-13 15:00:00','2026-04-13 15:00:00','2026-04-13 15:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000011','a1b2c3d4-0001-0001-0001-000000000001',65000,'EXPENSE','FOOD','Student set lunch at campus canteen - rice + pork ribs + vegetable soup','2026-04-14 12:00:00','2026-04-14 12:00:00','2026-04-14 12:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000012','a1b2c3d4-0001-0001-0001-000000000001',180000,'EXPENSE','HEALTH','Annual checkup at District 1 Medical Center - blood pressure and BMI screening','2026-04-15 09:00:00','2026-04-15 09:00:00','2026-04-15 09:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000013','a1b2c3d4-0001-0001-0001-000000000001',95000,'EXPENSE','UTILITIES','Monthly electricity bill - 95 kWh consumed at 1,000 VND/kWh average','2026-04-17 10:00:00','2026-04-17 10:00:00','2026-04-17 10:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000014','a1b2c3d4-0001-0001-0001-000000000001',130000,'EXPENSE','FOOD','Weekend grocery at BIG C - chicken thighs, cabbage, tofu, cooking oil','2026-04-19 13:00:00','2026-04-19 13:00:00','2026-04-19 13:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000015','a1b2c3d4-0001-0001-0001-000000000001',280000,'EXPENSE','SHOPPING','Buy "JavaScript: The Good Parts" book at Fahasa - for self-study','2026-04-21 14:00:00','2026-04-21 14:00:00','2026-04-21 14:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000016','a1b2c3d4-0001-0001-0001-000000000001',70000,'EXPENSE','FOOD','Buy 2 cups of Phuc Long milk tea + shared cheese crackers with roommate','2026-04-22 21:00:00','2026-04-22 21:00:00','2026-04-22 21:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000017','a1b2c3d4-0001-0001-0001-000000000001',500000,'INCOME','OTHER','Early delivery bonus - React app shipped 3 days ahead of client deadline','2026-04-25 17:00:00','2026-04-25 17:00:00','2026-04-25 17:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000018','a1b2c3d4-0001-0001-0001-000000000001',55000,'EXPENSE','TRANSPORTATION','Grab ride from home to exam hall - 6 km trip, surge pricing avoided','2026-04-26 07:00:00','2026-04-26 07:00:00','2026-04-26 07:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000019','a1b2c3d4-0001-0001-0001-000000000001',160000,'EXPENSE','FOOD','Family dinner at Quan Bun Bo Hue - 4 bowls of spicy beef noodle soup','2026-04-28 19:00:00','2026-04-28 19:00:00','2026-04-28 19:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000020','a1b2c3d4-0001-0001-0001-000000000001',7000000,'INCOME','OTHER','May freelance payment - Node.js REST API project + family allowance','2026-05-01 09:00:00','2026-05-01 09:00:00','2026-05-01 09:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000021','a1b2c3d4-0001-0001-0001-000000000001',2500000,'EXPENSE','UTILITIES','Monthly room rent - May','2026-05-03 10:00:00','2026-05-03 10:00:00','2026-05-03 10:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000022','a1b2c3d4-0001-0001-0001-000000000001',85000,'EXPENSE','FOOD','Lunch at Com Tam Nguyen Ky - broken rice, grilled pork chop, fried egg','2026-05-05 12:00:00','2026-05-05 12:00:00','2026-05-05 12:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000023','a1b2c3d4-0001-0001-0001-000000000001',1200000,'EXPENSE','EDUCATION','Purchase Udemy "Advanced React Patterns" course - 15h content, lifetime access','2026-05-06 10:00:00','2026-05-06 10:00:00','2026-05-06 10:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000024','a1b2c3d4-0001-0001-0001-000000000001',110000,'EXPENSE','TRANSPORTATION','Fill up 6L petrol + buy monthly parking sticker at RMIT building','2026-05-07 08:00:00','2026-05-07 08:00:00','2026-05-07 08:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000025','a1b2c3d4-0001-0001-0001-000000000001',320000,'EXPENSE','ENTERTAINMENT','Group BBQ birthday for Hai Bang - grill set + 2kg pork + drinks for 5 people','2026-05-10 18:30:00','2026-05-10 18:30:00','2026-05-10 18:30:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000026','a1b2c3d4-0001-0001-0001-000000000001',75000,'EXPENSE','FOOD','Breakfast combo at Highlands Coffee - croissant sandwich + iced latte','2026-05-12 08:00:00','2026-05-12 08:00:00','2026-05-12 08:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000027','a1b2c3d4-0001-0001-0001-000000000001',480000,'EXPENSE','CLOTHING','Buy slim-fit chino pants at Zara mid-season sale - 40% off','2026-05-13 15:00:00','2026-05-13 15:00:00','2026-05-13 15:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000028','a1b2c3d4-0001-0001-0001-000000000001',900000,'INCOME','OTHER','Build landing page for local cafe shop - 3-day freelance project, fixed price','2026-05-15 16:00:00','2026-05-15 16:00:00','2026-05-15 16:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000029','a1b2c3d4-0001-0001-0001-000000000001',95000,'EXPENSE','UTILITIES','FPT Fiber internet bill - 100 Mbps plan + room electricity combined','2026-05-18 10:00:00','2026-05-18 10:00:00','2026-05-18 10:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000030','a1b2c3d4-0001-0001-0001-000000000001',140000,'EXPENSE','FOOD','Home cooking - bought chicken, lemongrass, chili at wet market for 3 meals','2026-05-18 09:00:00','2026-05-18 09:00:00','2026-05-18 09:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000031','a1b2c3d4-0001-0001-0001-000000000001',250000,'EXPENSE','HEALTH','Buy Panadol cold tablets + 500mg Vitamin C + zinc lozenges at Pharmacity','2026-05-20 11:00:00','2026-05-20 11:00:00','2026-05-20 11:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000032','a1b2c3d4-0001-0001-0001-000000000001',180000,'EXPENSE','SHOPPING','Buy HP printer ink cartridge (black + color) + 500 sheets A4 paper','2026-05-22 14:00:00','2026-05-22 14:00:00','2026-05-22 14:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000033','a1b2c3d4-0001-0001-0001-000000000001',60000,'EXPENSE','FOOD','Order 3 cups of Gong Cha bubble tea - afternoon study break at library','2026-05-25 16:00:00','2026-05-25 16:00:00','2026-05-25 16:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000034','a1b2c3d4-0001-0001-0001-000000000001',420000,'EXPENSE','ENTERTAINMENT','Class picnic outing at Thu Le Park - contributed to shared food and transport fund','2026-05-28 07:00:00','2026-05-28 07:00:00','2026-05-28 07:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000035','a1b2c3d4-0001-0001-0001-000000000001',7500000,'INCOME','OTHER','June freelance salary + milestone bonus for on-time mobile app delivery','2026-06-01 09:00:00','2026-06-01 09:00:00','2026-06-01 09:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000036','a1b2c3d4-0001-0001-0001-000000000001',2500000,'EXPENSE','UTILITIES','Monthly room rent - June','2026-06-02 10:00:00','2026-06-02 10:00:00','2026-06-02 10:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000037','a1b2c3d4-0001-0001-0001-000000000001',90000,'EXPENSE','FOOD','Lunch at Bep Me In - rice set with braised pork belly, pickled mustard greens, soup','2026-06-03 12:30:00','2026-06-03 12:30:00','2026-06-03 12:30:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000038','a1b2c3d4-0001-0001-0001-000000000001',800000,'EXPENSE','EDUCATION','Register for AWS Cloud Practitioner exam - Pearson VUE public center voucher','2026-06-04 08:00:00','2026-06-04 08:00:00','2026-06-04 08:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000039','a1b2c3d4-0001-0001-0001-000000000001',130000,'EXPENSE','TRANSPORTATION','Fill up 6L petrol + expressway toll fee for trip to Bien Hoa client site','2026-06-05 07:30:00','2026-06-05 07:30:00','2026-06-05 07:30:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000040','a1b2c3d4-0001-0001-0001-000000000001',270000,'EXPENSE','FOOD','Dinner at Quan 94 BBQ restaurant - celebrate Hai Bang graduation, split 5 ways','2026-06-07 19:30:00','2026-06-07 19:30:00','2026-06-07 19:30:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000041','a1b2c3d4-0001-0001-0001-000000000001',380000,'EXPENSE','CLOTHING','Buy formal white shirt + dark dress pants at Canifa for graduation ceremony','2026-06-08 15:00:00','2026-06-08 15:00:00','2026-06-08 15:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000042','a1b2c3d4-0001-0001-0001-000000000001',100000,'EXPENSE','UTILITIES','Water supply bill + extra electricity charge for June - AC usage increased','2026-06-10 10:00:00','2026-06-10 10:00:00','2026-06-10 10:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000043','a1b2c3d4-0001-0001-0001-000000000001',1500000,'INCOME','OTHER','Freelance React Native project - implement 2 screens for a food delivery app','2026-06-11 17:00:00','2026-06-11 17:00:00','2026-06-11 17:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000044','a1b2c3d4-0001-0001-0001-000000000001',220000,'EXPENSE','ENTERTAINMENT','Buy 1 ticket to indie music night at Acoustic Bar - 3 performing bands','2026-06-13 20:00:00','2026-06-13 20:00:00','2026-06-13 20:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000045','a1b2c3d4-0001-0001-0001-000000000001',155000,'EXPENSE','FOOD','Weekend brunch at The Workshop Coffee - avocado toast + pour-over coffee','2026-06-14 13:00:00','2026-06-14 13:00:00','2026-06-14 13:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000046','a1b2c3d4-0001-0001-0001-000000000001',95000,'EXPENSE','HEALTH','Buy Vitamin D3 2000 IU + Zinc 50mg supplement pack at Guardian pharmacy','2026-06-11 09:30:00','2026-06-11 09:30:00','2026-06-11 09:30:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000047','a1b2c3d4-0001-0001-0001-000000000001',310000,'EXPENSE','SHOPPING','Buy Sony WF-C500 wireless earbuds on Tiki 6.6 flash sale - 20% off','2026-06-12 14:00:00','2026-06-12 14:00:00','2026-06-12 14:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000048','a1b2c3d4-0001-0001-0001-000000000001',185000,'EXPENSE','FOOD','Weekly grocery at VinMart - pork tenderloin, broccoli, fruit, yogurt','2026-06-13 09:00:00','2026-06-13 09:00:00','2026-06-13 09:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000049','a1b2c3d4-0001-0001-0001-000000000001',450000,'EXPENSE','EDUCATION','Buy "Clean Code" by Robert Martin at Fahasa - personal copy for annotations','2026-06-13 11:00:00','2026-06-13 11:00:00','2026-06-13 11:00:00'),
                                                                                                                      ('e1000001-0000-0000-0000-000000000050','a1b2c3d4-0001-0001-0001-000000000001',80000,'EXPENSE','TRANSPORTATION','Grab ride home from co-working cafe after 11 PM coding session','2026-06-14 23:00:00','2026-06-14 23:00:00','2026-06-14 23:00:00');

-- ════════════════════════════════════════════════════════════
-- 4. TRANSACTIONS - USER 2: Nguyen Hoang Nam Tran (50 rows)
-- ════════════════════════════════════════════════════════════
INSERT INTO public.transactions (id, user_id, amount, type, category, description, date, created_at, updated_at) VALUES
                                                                                                                      ('e2000002-0000-0000-0000-000000000001','a1b2c3d4-0002-0002-0002-000000000002',4500000,'INCOME','OTHER','Semester 2 scholarship disbursement 2.5M + April family allowance 2M','2026-04-01 10:00:00','2026-04-01 10:00:00','2026-04-01 10:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000002','a1b2c3d4-0002-0002-0002-000000000002',800000,'EXPENSE','UTILITIES','Dormitory room fee - April - 4-bed shared room at university dorm B','2026-04-02 09:00:00','2026-04-02 09:00:00','2026-04-02 09:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000003','a1b2c3d4-0002-0002-0002-000000000002',55000,'EXPENSE','FOOD','Buy lunch set at dormitory cafeteria - steamed rice + 2 vegetable dishes','2026-04-04 12:00:00','2026-04-04 12:00:00','2026-04-04 12:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000004','a1b2c3d4-0002-0002-0002-000000000002',350000,'EXPENSE','EDUCATION','Purchase "Introduction to Algorithms" 3rd edition at Fahasa bookstore','2026-04-05 09:00:00','2026-04-05 09:00:00','2026-04-05 09:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000005','a1b2c3d4-0002-0002-0002-000000000002',30000,'EXPENSE','TRANSPORTATION','Monthly shared bicycle station subscription pass - Go! Bikes app','2026-04-06 08:00:00','2026-04-06 08:00:00','2026-04-06 08:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000006','a1b2c3d4-0002-0002-0002-000000000002',65000,'EXPENSE','FOOD','Dinner at com binh dan near dorm with 2 roommates - pork + tofu + rice','2026-04-08 19:30:00','2026-04-08 19:30:00','2026-04-08 19:30:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000007','a1b2c3d4-0002-0002-0002-000000000002',250000,'EXPENSE','CLOTHING','Buy light cardigan at H&M - needed for heavily air-conditioned classrooms','2026-04-10 15:00:00','2026-04-10 15:00:00','2026-04-10 15:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000008','a1b2c3d4-0002-0002-0002-000000000002',70000,'EXPENSE','FOOD','Buy snacks and 3 packs of instant noodles at FamilyMart for the week','2026-04-11 10:00:00','2026-04-11 10:00:00','2026-04-11 10:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000009','a1b2c3d4-0002-0002-0002-000000000002',150000,'EXPENSE','ENTERTAINMENT','Mobile game monthly battle pass + top-up 100,000 in-game currency','2026-04-13 21:00:00','2026-04-13 21:00:00','2026-04-13 21:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000010','a1b2c3d4-0002-0002-0002-000000000002',45000,'EXPENSE','FOOD','Breakfast banh mi thit at street stall near dormitory gate','2026-04-15 07:30:00','2026-04-15 07:30:00','2026-04-15 07:30:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000011','a1b2c3d4-0002-0002-0002-000000000002',120000,'EXPENSE','HEALTH','Buy Paracetamol 500mg + daily multivitamin tablets at Pharmacity','2026-04-16 11:00:00','2026-04-16 11:00:00','2026-04-16 11:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000012','a1b2c3d4-0002-0002-0002-000000000002',400000,'INCOME','OTHER','Merit scholarship incentive bonus - ranked top 5% GPA last semester','2026-04-17 15:00:00','2026-04-17 15:00:00','2026-04-17 15:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000013','a1b2c3d4-0002-0002-0002-000000000002',80000,'EXPENSE','UTILITIES','Extra electricity charge in dorm room - personal fan running 24/7','2026-04-19 10:00:00','2026-04-19 10:00:00','2026-04-19 10:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000014','a1b2c3d4-0002-0002-0002-000000000002',90000,'EXPENSE','FOOD','Room anniversary dinner - ordered 1 large pizza delivered to dorm block B','2026-04-20 18:00:00','2026-04-20 18:00:00','2026-04-20 18:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000015','a1b2c3d4-0002-0002-0002-000000000002',200000,'EXPENSE','SHOPPING','Buy A4 notebook set (5 books) + ballpoint pens + correction tape at AEON','2026-04-22 14:00:00','2026-04-22 14:00:00','2026-04-22 14:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000016','a1b2c3d4-0002-0002-0002-000000000002',50000,'EXPENSE','FOOD','Buy 2 cups of Tiger Sugar brown sugar pearl milk tea near campus','2026-04-24 22:00:00','2026-04-24 22:00:00','2026-04-24 22:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000017','a1b2c3d4-0002-0002-0002-000000000002',60000,'EXPENSE','TRANSPORTATION','Motorbike taxi to Ben Xe Mien Dong bus station for April 30 holiday','2026-04-27 16:00:00','2026-04-27 16:00:00','2026-04-27 16:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000018','a1b2c3d4-0002-0002-0002-000000000002',100000,'EXPENSE','FOOD','Holiday meals at family home during April 30 - May 1 national holiday break','2026-04-30 12:00:00','2026-04-30 12:00:00','2026-04-30 12:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000019','a1b2c3d4-0002-0002-0002-000000000002',4500000,'INCOME','OTHER','May scholarship disbursement + family allowance','2026-05-01 10:00:00','2026-05-01 10:00:00','2026-05-01 10:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000020','a1b2c3d4-0002-0002-0002-000000000002',800000,'EXPENSE','UTILITIES','Dormitory room fee - May payment','2026-05-02 09:00:00','2026-05-02 09:00:00','2026-05-02 09:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000021','a1b2c3d4-0002-0002-0002-000000000002',55000,'EXPENSE','FOOD','Canteen lunch set - steamed rice + stir-fried tofu + pickled vegetables','2026-05-04 12:30:00','2026-05-04 12:30:00','2026-05-04 12:30:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000022','a1b2c3d4-0002-0002-0002-000000000002',500000,'EXPENSE','EDUCATION','TOEIC 2-skill exam registration at IIG Vietnam center - May 25 test date','2026-05-05 09:00:00','2026-05-05 09:00:00','2026-05-05 09:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000023','a1b2c3d4-0002-0002-0002-000000000002',35000,'EXPENSE','TRANSPORTATION','Bus ticket to Thu Duc City library - route 19 round trip','2026-05-07 08:30:00','2026-05-07 08:30:00','2026-05-07 08:30:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000024','a1b2c3d4-0002-0002-0002-000000000002',180000,'EXPENSE','ENTERTAINMENT','Buy "Sapiens: A Brief History of Humankind" at Phuong Nam bookstore','2026-05-09 16:00:00','2026-05-09 16:00:00','2026-05-09 16:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000025','a1b2c3d4-0002-0002-0002-000000000002',75000,'EXPENSE','FOOD','Dinner at Lotteria - buy-1-get-1 crispy chicken burger + fries combo','2026-05-12 19:00:00','2026-05-12 19:00:00','2026-05-12 19:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000026','a1b2c3d4-0002-0002-0002-000000000002',300000,'EXPENSE','CLOTHING','Buy Nike Air Max 270 running shoes at Sport365 - replaced worn-out pair','2026-05-14 15:00:00','2026-05-14 15:00:00','2026-05-14 15:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000027','a1b2c3d4-0002-0002-0002-000000000002',85000,'EXPENSE','FOOD','Weekly grocery at Co.opMart - rice 2kg, eggs tray, bread loaf, vegetables','2026-05-16 10:00:00','2026-05-16 10:00:00','2026-05-16 10:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000028','a1b2c3d4-0002-0002-0002-000000000002',90000,'EXPENSE','UTILITIES','Dorm electricity and water - combined monthly bill for May usage','2026-05-20 10:00:00','2026-05-20 10:00:00','2026-05-20 10:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000029','a1b2c3d4-0002-0002-0002-000000000002',150000,'EXPENSE','HEALTH','Routine blood test + general checkup at HCMUS Medical Center on campus','2026-05-21 09:00:00','2026-05-21 09:00:00','2026-05-21 09:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000030','a1b2c3d4-0002-0002-0002-000000000002',60000,'EXPENSE','FOOD','Buy 2 packs Hao Hao instant noodles + 2 cans sardine for late-night study','2026-05-23 20:00:00','2026-05-23 20:00:00','2026-05-23 20:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000031','a1b2c3d4-0002-0002-0002-000000000002',220000,'EXPENSE','SHOPPING','Buy Samsonite Sonora backpack at outlet - replaced 3-year-old torn bag','2026-05-26 14:30:00','2026-05-26 14:30:00','2026-05-26 14:30:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000032','a1b2c3d4-0002-0002-0002-000000000002',45000,'EXPENSE','FOOD','Buy 2 iced Americano at The Coffee House during group project meeting','2026-05-28 14:00:00','2026-05-28 14:00:00','2026-05-28 14:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000033','a1b2c3d4-0002-0002-0002-000000000002',5000000,'INCOME','OTHER','Excellence scholarship award 2.5M + June family allowance 2.5M','2026-06-01 10:00:00','2026-06-01 10:00:00','2026-06-01 10:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000034','a1b2c3d4-0002-0002-0002-000000000002',800000,'EXPENSE','UTILITIES','Dormitory room fee - June payment','2026-06-02 09:00:00','2026-06-02 09:00:00','2026-06-02 09:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000035','a1b2c3d4-0002-0002-0002-000000000002',60000,'EXPENSE','FOOD','Canteen lunch - grilled chicken set with mixed salad + lemon tea','2026-06-03 12:00:00','2026-06-03 12:00:00','2026-06-03 12:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000036','a1b2c3d4-0002-0002-0002-000000000002',700000,'EXPENSE','EDUCATION','Enroll in TOEIC online prep course at IZONE - 200 practice questions + mock tests','2026-06-04 09:00:00','2026-06-04 09:00:00','2026-06-04 09:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000037','a1b2c3d4-0002-0002-0002-000000000002',40000,'EXPENSE','TRANSPORTATION','Bicycle inner tube replacement at repair shop near campus gate','2026-06-05 11:00:00','2026-06-05 11:00:00','2026-06-05 11:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000038','a1b2c3d4-0002-0002-0002-000000000002',130000,'EXPENSE','ENTERTAINMENT','Buy entrance ticket to Ho Chi Minh City Book Fair 2026 - weekend visit','2026-06-07 10:00:00','2026-06-07 10:00:00','2026-06-07 10:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000039','a1b2c3d4-0002-0002-0002-000000000002',80000,'EXPENSE','FOOD','Group food order during final exam week - 3 fried rice dishes via GrabFood','2026-06-09 19:00:00','2026-06-09 19:00:00','2026-06-09 19:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000040','a1b2c3d4-0002-0002-0002-000000000002',180000,'EXPENSE','CLOTHING','Buy 3 plain round-neck T-shirts at Muji - summer collection restock','2026-06-10 15:00:00','2026-06-10 15:00:00','2026-06-10 15:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000041','a1b2c3d4-0002-0002-0002-000000000002',100000,'EXPENSE','UTILITIES','Extra electricity charge in June - AC usage increased due to heat wave','2026-06-11 10:00:00','2026-06-11 10:00:00','2026-06-11 10:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000042','a1b2c3d4-0002-0002-0002-000000000002',170000,'EXPENSE','HEALTH','Buy La Roche-Posay SPF 50 sunscreen + hair vitamin supplement at Guardian','2026-06-12 09:00:00','2026-06-12 09:00:00','2026-06-12 09:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000043','a1b2c3d4-0002-0002-0002-000000000002',90000,'EXPENSE','FOOD','Weekend grocery - eggs 10-pack, bread, butter, banana bunch at BIG C','2026-06-13 10:00:00','2026-06-13 10:00:00','2026-06-13 10:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000044','a1b2c3d4-0002-0002-0002-000000000002',250000,'EXPENSE','SHOPPING','Buy Stabilo fineliner 20-color set + sketchbook for UX design project','2026-06-13 15:00:00','2026-06-13 15:00:00','2026-06-13 15:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000045','a1b2c3d4-0002-0002-0002-000000000002',50000,'EXPENSE','FOOD','Buy hot Americano at Highlands Coffee before 8 AM morning exam','2026-06-14 07:30:00','2026-06-14 07:30:00','2026-06-14 07:30:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000046','a1b2c3d4-0002-0002-0002-000000000002',35000,'EXPENSE','TRANSPORTATION','Grab car ride back to dormitory after last final exam session','2026-06-14 11:30:00','2026-06-14 11:30:00','2026-06-14 11:30:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000047','a1b2c3d4-0002-0002-0002-000000000002',400000,'INCOME','OTHER','Semester academic performance bonus - GPA 3.8/4.0 achievement reward','2026-06-10 14:00:00','2026-06-10 14:00:00','2026-06-10 14:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000048','a1b2c3d4-0002-0002-0002-000000000002',65000,'EXPENSE','FOOD','Team lunch at com binh dan after final group project presentation','2026-06-11 13:00:00','2026-06-11 13:00:00','2026-06-11 13:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000049','a1b2c3d4-0002-0002-0002-000000000002',190000,'EXPENSE','EDUCATION','Buy official TOEIC Reading & Listening practice test book at bookstore','2026-06-12 14:00:00','2026-06-12 14:00:00','2026-06-12 14:00:00'),
                                                                                                                      ('e2000002-0000-0000-0000-000000000050','a1b2c3d4-0002-0002-0002-000000000002',110000,'EXPENSE','ENTERTAINMENT','Watch "Inside Out 2" at CGV Vincom - 4 dormitory friends, regular seats','2026-06-14 19:00:00','2026-06-14 19:00:00','2026-06-14 19:00:00');

-- ════════════════════════════════════════════════════════════
-- 4. TRANSACTIONS - USER 3: Vo Thinh Vuong (50 rows)
-- ════════════════════════════════════════════════════════════
INSERT INTO public.transactions (id, user_id, amount, type, category, description, date, created_at, updated_at) VALUES
                                                                                                                      ('e3000003-0000-0000-0000-000000000001','a1b2c3d4-0003-0003-0003-000000000003',6500000,'INCOME','OTHER','April part-time wage at Cong Ca Phe Tay Nguyen cafe + family support allowance','2026-04-01 08:00:00','2026-04-01 08:00:00','2026-04-01 08:00:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000002','a1b2c3d4-0003-0003-0003-000000000003',80000,'EXPENSE','TRANSPORTATION','Monthly bus pass recharge - routes 72 and 104 for school-work commute','2026-04-02 07:30:00','2026-04-02 07:30:00','2026-04-02 07:30:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000003','a1b2c3d4-0003-0003-0003-000000000003',60000,'EXPENSE','FOOD','Lunch at com binh dan near campus - rice + pork ribs + stir-fried morning glory','2026-04-03 12:30:00','2026-04-03 12:30:00','2026-04-03 12:30:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000004','a1b2c3d4-0003-0003-0003-000000000003',450000,'EXPENSE','EDUCATION','Pay for "Communication Skills for Business" workshop at Toastmasters Club','2026-04-05 09:00:00','2026-04-05 09:00:00','2026-04-05 09:00:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000005','a1b2c3d4-0003-0003-0003-000000000003',150000,'EXPENSE','FOOD','Monthly contribution to family grocery fund - hand over to mother','2026-04-06 19:00:00','2026-04-06 19:00:00','2026-04-06 19:00:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000006','a1b2c3d4-0003-0003-0003-000000000003',320000,'EXPENSE','CLOTHING','Buy 2 black polo shirts - official uniform required for cafe serving staff','2026-04-08 15:00:00','2026-04-08 15:00:00','2026-04-08 15:00:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000007','a1b2c3d4-0003-0003-0003-000000000003',95000,'EXPENSE','FOOD','Breakfast and lunch on 10-hour double-shift work day - banh mi + com hop','2026-04-10 07:00:00','2026-04-10 07:00:00','2026-04-10 07:00:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000008','a1b2c3d4-0003-0003-0003-000000000003',250000,'EXPENSE','ENTERTAINMENT','Billiards session + soft drinks for 4 friends at Q Billiards, 2 hours','2026-04-12 15:00:00','2026-04-12 15:00:00','2026-04-12 15:00:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000009','a1b2c3d4-0003-0003-0003-000000000003',70000,'EXPENSE','FOOD','Home cooking - bought pork mince, tofu, spring onions at wet market','2026-04-14 18:00:00','2026-04-14 18:00:00','2026-04-14 18:00:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000010','a1b2c3d4-0003-0003-0003-000000000003',200000,'EXPENSE','HEALTH','Throat exam at district clinic + buy Strepsils lozenges + antibiotic prescription','2026-04-15 10:00:00','2026-04-15 10:00:00','2026-04-15 10:00:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000011','a1b2c3d4-0003-0003-0003-000000000003',800000,'INCOME','OTHER','4 weekend overtime shifts at cafe - extra pay at 1.5x hourly rate','2026-04-17 20:00:00','2026-04-17 20:00:00','2026-04-17 20:00:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000012','a1b2c3d4-0003-0003-0003-000000000003',85000,'EXPENSE','TRANSPORTATION','Top up petrol for father Honda Future motorcycle - 5L at Shell station','2026-04-18 08:00:00','2026-04-18 08:00:00','2026-04-18 08:00:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000013','a1b2c3d4-0003-0003-0003-000000000003',120000,'EXPENSE','FOOD','Family seafood meal during April 30 holiday - bought shrimp and squid at market','2026-04-29 12:00:00','2026-04-29 12:00:00','2026-04-29 12:00:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000014','a1b2c3d4-0003-0003-0003-000000000003',160000,'EXPENSE','SHOPPING','Buy 2 ballpoint pens + highlighter set + sticky notes at stationery shop','2026-04-20 14:00:00','2026-04-20 14:00:00','2026-04-20 14:00:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000015','a1b2c3d4-0003-0003-0003-000000000003',55000,'EXPENSE','FOOD','Budget dinner at local eatery - rice + 2 dishes, no drink','2026-04-22 19:30:00','2026-04-22 19:30:00','2026-04-22 19:30:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000016','a1b2c3d4-0003-0003-0003-000000000003',400000,'EXPENSE','UTILITIES','Monthly contribution to family electricity and water bills','2026-04-01 11:00:00','2026-04-01 11:00:00','2026-04-01 11:00:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000017','a1b2c3d4-0003-0003-0003-000000000003',6500000,'INCOME','OTHER','May part-time wage at cafe + family support allowance','2026-05-01 08:00:00','2026-05-01 08:00:00','2026-05-01 08:00:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000018','a1b2c3d4-0003-0003-0003-000000000003',80000,'EXPENSE','TRANSPORTATION','Monthly bus pass recharge - May renewal','2026-05-01 07:30:00','2026-05-01 07:30:00','2026-05-01 07:30:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000019','a1b2c3d4-0003-0003-0003-000000000003',400000,'EXPENSE','UTILITIES','Monthly family bill contribution - May electricity + water','2026-05-02 11:00:00','2026-05-02 11:00:00','2026-05-02 11:00:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000020','a1b2c3d4-0003-0003-0003-000000000003',65000,'EXPENSE','FOOD','Beef pho at Pho Thin shop before morning work shift - large bowl + extra beef','2026-05-04 07:00:00','2026-05-04 07:00:00','2026-05-04 07:00:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000021','a1b2c3d4-0003-0003-0003-000000000003',600000,'EXPENSE','EDUCATION','Pay one-subject summer semester tuition - Database Systems practical course','2026-05-05 09:00:00','2026-05-05 09:00:00','2026-05-05 09:00:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000022','a1b2c3d4-0003-0003-0003-000000000003',200000,'EXPENSE','FOOD','Buy cooking ingredients at wet market for 3 home-cooked meals on day off','2026-05-07 09:00:00','2026-05-07 09:00:00','2026-05-07 09:00:00'),
                                                                                                                      ('e3000003-0000-0000-0000-000000000023','a1b2c3d4-0003-0003-0003-000000000003',350000,'EXPENSE','ENTERTAINMENT','Buy flower bouquet at 8Flowers + 1 birthday cake at Tous Les Jours for mother's birthday','2026-05-09 16:00:00','2026-05-09 16:00:00','2026-05-09 16:00:00'),
('e3000003-0000-0000-0000-000000000024','a1b2c3d4-0003-0003-0003-000000000003',90000,'EXPENSE','FOOD','Lunch at a nearby restaurant on day off - grilled chicken rice set','2026-05-11 12:30:00','2026-05-11 12:30:00','2026-05-11 12:30:00'),
('e3000003-0000-0000-0000-000000000025','a1b2c3d4-0003-0003-0003-000000000003',280000,'EXPENSE','CLOTHING','Buy black leather working shoes for cafe job - non-slip sole required','2026-05-13 15:30:00','2026-05-13 15:30:00','2026-05-13 15:30:00'),
('e3000003-0000-0000-0000-000000000026','a1b2c3d4-0003-0003-0003-000000000003',1000000,'INCOME','OTHER','May overtime pay + shared tip pool from 3 weekend shifts - customer tips','2026-05-15 22:00:00','2026-05-15 22:00:00','2026-05-15 22:00:00'),
('e3000003-0000-0000-0000-000000000027','a1b2c3d4-0003-0003-0003-000000000003',75000,'EXPENSE','FOOD','Breakfast at the cafe before clocking in - banh mi thit nguoi + coconut coffee','2026-05-17 07:30:00','2026-05-17 07:30:00','2026-05-17 07:30:00'),
('e3000003-0000-0000-0000-000000000028','a1b2c3d4-0003-0003-0003-000000000003',150000,'EXPENSE','HEALTH','Eye exam for glasses prescription update + buy Rohto V eye drops at optician','2026-05-19 10:00:00','2026-05-19 10:00:00','2026-05-19 10:00:00'),
('e3000003-0000-0000-0000-000000000029','a1b2c3d4-0003-0003-0003-000000000003',110000,'EXPENSE','SHOPPING','Buy 10,000 mAh power bank at An Khang pharmacy - discounted travel bundle','2026-05-21 14:00:00','2026-05-21 14:00:00','2026-05-21 14:00:00'),
('e3000003-0000-0000-0000-000000000030','a1b2c3d4-0003-0003-0003-000000000003',55000,'EXPENSE','FOOD','Buy 1 cup Phuc Long jasmine milk tea on the way home after evening class','2026-05-23 17:00:00','2026-05-23 17:00:00','2026-05-23 17:00:00'),
('e3000003-0000-0000-0000-000000000031','a1b2c3d4-0003-0003-0003-000000000003',85000,'EXPENSE','TRANSPORTATION','Grab ride after missed last bus + bus ticket for rest of commute week','2026-05-27 08:00:00','2026-05-27 08:00:00','2026-05-27 08:00:00'),
('e3000003-0000-0000-0000-000000000032','a1b2c3d4-0003-0003-0003-000000000003',200000,'EXPENSE','FOOD','Cook Vietnamese hot pot with family on Saturday - bought broth, beef, mushrooms','2026-05-30 18:00:00','2026-05-30 18:00:00','2026-05-30 18:00:00'),
('e3000003-0000-0000-0000-000000000033','a1b2c3d4-0003-0003-0003-000000000003',7000000,'INCOME','OTHER','June wage + outstanding employee of the month bonus from cafe manager','2026-06-01 08:00:00','2026-06-01 08:00:00','2026-06-01 08:00:00'),
('e3000003-0000-0000-0000-000000000034','a1b2c3d4-0003-0003-0003-000000000003',80000,'EXPENSE','TRANSPORTATION','Monthly bus pass recharge - June renewal','2026-06-01 07:30:00','2026-06-01 07:30:00','2026-06-01 07:30:00'),
('e3000003-0000-0000-0000-000000000035','a1b2c3d4-0003-0003-0003-000000000003',400000,'EXPENSE','UTILITIES','Monthly family bill contribution - June electricity and water','2026-06-02 11:00:00','2026-06-02 11:00:00','2026-06-02 11:00:00'),
('e3000003-0000-0000-0000-000000000036','a1b2c3d4-0003-0003-0003-000000000003',70000,'EXPENSE','FOOD','Beef pho at Pho Thin before first morning shift of June - large bowl','2026-06-03 07:00:00','2026-06-03 07:00:00','2026-06-03 07:00:00'),
('e3000003-0000-0000-0000-000000000037','a1b2c3d4-0003-0003-0003-000000000003',350000,'EXPENSE','CLOTHING','Buy 2 summer polo shirts at Canifa for school - lightweight fabric','2026-06-05 15:00:00','2026-06-05 15:00:00','2026-06-05 15:00:00'),
('e3000003-0000-0000-0000-000000000038','a1b2c3d4-0003-0003-0003-000000000003',140000,'EXPENSE','FOOD','Post-exam group food order via GoFood - 4 com hop grilled pork delivered','2026-06-07 18:00:00','2026-06-07 18:00:00','2026-06-07 18:00:00'),
('e3000003-0000-0000-0000-000000000039','a1b2c3d4-0003-0003-0003-000000000003',500000,'EXPENSE','EDUCATION','Buy "Introduction to Computer Networks" 7th ed. at Fahasa - required textbook','2026-06-08 09:00:00','2026-06-08 09:00:00','2026-06-08 09:00:00'),
('e3000003-0000-0000-0000-000000000040','a1b2c3d4-0003-0003-0003-000000000003',90000,'EXPENSE','HEALTH','Tonsil treatment at district medical center + amoxicillin 500mg prescription','2026-06-09 10:00:00','2026-06-09 10:00:00','2026-06-09 10:00:00'),
('e3000003-0000-0000-0000-000000000041','a1b2c3d4-0003-0003-0003-000000000003',300000,'EXPENSE','ENTERTAINMENT','Family day trip to Vung Tau beach - shared petrol + parking + beach entry fee','2026-06-10 06:00:00','2026-06-10 06:00:00','2026-06-10 06:00:00'),
('e3000003-0000-0000-0000-000000000042','a1b2c3d4-0003-0003-0003-000000000003',75000,'EXPENSE','FOOD','Home-cooked dinner + extra protein bought at local wet market - pork + eggs','2026-06-11 18:00:00','2026-06-11 18:00:00','2026-06-11 18:00:00'),
('e3000003-0000-0000-0000-000000000043','a1b2c3d4-0003-0003-0003-000000000003',180000,'EXPENSE','SHOPPING','Buy Victor AL-2200 badminton racket at sports equipment shop near home','2026-06-12 14:00:00','2026-06-12 14:00:00','2026-06-12 14:00:00'),
('e3000003-0000-0000-0000-000000000044','a1b2c3d4-0003-0003-0003-000000000003',60000,'EXPENSE','FOOD','Buy 2 cups KOI The milk tea + rice paper rolls from street vendor','2026-06-13 17:00:00','2026-06-13 17:00:00','2026-06-13 17:00:00'),
('e3000003-0000-0000-0000-000000000045','a1b2c3d4-0003-0003-0003-000000000003',95000,'EXPENSE','TRANSPORTATION','Grab to exam venue 7 AM + bus ride home after exam session','2026-06-14 08:00:00','2026-06-14 08:00:00','2026-06-14 08:00:00'),
('e3000003-0000-0000-0000-000000000046','a1b2c3d4-0003-0003-0003-000000000003',120000,'EXPENSE','FOOD','Buy 3 takeaway lunch boxes during final exam period - com thap cam set','2026-06-14 13:00:00','2026-06-14 13:00:00','2026-06-14 13:00:00'),
('e3000003-0000-0000-0000-000000000047','a1b2c3d4-0003-0003-0003-000000000003',500000,'INCOME','OTHER','June holiday bonus from cafe - distributed equally to all shift staff','2026-06-13 21:00:00','2026-06-13 21:00:00','2026-06-13 21:00:00'),
('e3000003-0000-0000-0000-000000000048','a1b2c3d4-0003-0003-0003-000000000003',85000,'EXPENSE','UTILITIES','Extra electricity charge at home in June - heat wave caused higher AC use','2026-06-10 10:00:00','2026-06-10 10:00:00','2026-06-10 10:00:00'),
('e3000003-0000-0000-0000-000000000049','a1b2c3d4-0003-0003-0003-000000000003',130000,'EXPENSE','HEALTH','Buy Sunplay SPF 50 sunscreen + Vaseline moisturizer lotion at Watsons','2026-06-12 11:00:00','2026-06-12 11:00:00','2026-06-12 11:00:00'),
('e3000003-0000-0000-0000-000000000050','a1b2c3d4-0003-0003-0003-000000000003',200000,'EXPENSE','FOOD','Weekly wet market shopping for home cooking - pork belly, squid, vegetables','2026-06-14 09:00:00','2026-06-14 09:00:00','2026-06-14 09:00:00');

-- ════════════════════════════════════════════════════════════
-- 4. TRANSACTIONS - USER 4: Ngo Hai Bang (50 rows)
-- ════════════════════════════════════════════════════════════
INSERT INTO public.transactions (id, user_id, amount, type, category, description, date, created_at, updated_at) VALUES
('e4000004-0000-0000-0000-000000000001','a1b2c3d4-0004-0004-0004-000000000004',6000000,'INCOME','OTHER','April part-time programming income from client + family support allowance','2026-04-01 09:30:00','2026-04-01 09:30:00','2026-04-01 09:30:00'),
('e4000004-0000-0000-0000-000000000002','a1b2c3d4-0004-0004-0004-000000000004',2200000,'EXPENSE','UTILITIES','Monthly room rent - April - Binh Thanh district studio apartment','2026-04-02 10:00:00','2026-04-02 10:00:00','2026-04-02 10:00:00'),
('e4000004-0000-0000-0000-000000000003','a1b2c3d4-0004-0004-0004-000000000004',90000,'EXPENSE','FOOD','Order beef pho + hot black coffee for breakfast via GrabFood app','2026-04-03 07:30:00','2026-04-03 07:30:00','2026-04-03 07:30:00'),
('e4000004-0000-0000-0000-000000000004','a1b2c3d4-0004-0004-0004-000000000004',500000,'EXPENSE','EDUCATION','Buy Udemy "Unity 3D Game Development" course - 22h video, lifetime access','2026-04-05 10:00:00','2026-04-05 10:00:00','2026-04-05 10:00:00'),
('e4000004-0000-0000-0000-000000000005','a1b2c3d4-0004-0004-0004-000000000004',130000,'EXPENSE','TRANSPORTATION','Fill up 5L petrol at Shell station + buy emergency rain poncho','2026-04-06 08:00:00','2026-04-06 08:00:00','2026-04-06 08:00:00'),
('e4000004-0000-0000-0000-000000000006','a1b2c3d4-0004-0004-0004-000000000004',220000,'EXPENSE','FOOD','Dinner for 2 at Pizza 4Ps - 1 medium margherita pizza + 2 soft drinks','2026-04-08 19:30:00','2026-04-08 19:30:00','2026-04-08 19:30:00'),
('e4000004-0000-0000-0000-000000000007','a1b2c3d4-0004-0004-0004-000000000004',450000,'EXPENSE','CLOTHING','Buy Uniqlo fleece hoodie + Adidas jogger pants at AEON Mall','2026-04-09 15:00:00','2026-04-09 15:00:00','2026-04-09 15:00:00'),
('e4000004-0000-0000-0000-000000000008','a1b2c3d4-0004-0004-0004-000000000004',100000,'EXPENSE','FOOD','Weekly grocery at VinMart - rice 2kg, chicken breast, eggs, mixed vegetables','2026-04-10 09:00:00','2026-04-10 09:00:00','2026-04-10 09:00:00'),
('e4000004-0000-0000-0000-000000000009','a1b2c3d4-0004-0004-0004-000000000004',350000,'EXPENSE','ENTERTAINMENT','Buy 2 tickets to Saigon Music Festival + artist merchandise tote bag','2026-04-12 20:00:00','2026-04-12 20:00:00','2026-04-12 20:00:00'),
('e4000004-0000-0000-0000-000000000010','a1b2c3d4-0004-0004-0004-000000000004',1200000,'INCOME','OTHER','Flutter mobile app milestone 1 payment received - login and home screen done','2026-04-14 16:00:00','2026-04-14 16:00:00','2026-04-14 16:00:00'),
('e4000004-0000-0000-0000-000000000011','a1b2c3d4-0004-0004-0004-000000000004',80000,'EXPENSE','FOOD','Order daily lunch via Baemin - com binh dan set with soup and side dish','2026-04-15 12:00:00','2026-04-15 12:00:00','2026-04-15 12:00:00'),
('e4000004-0000-0000-0000-000000000012','a1b2c3d4-0004-0004-0004-000000000004',120000,'EXPENSE','UTILITIES','Room electricity bill + water usage - April combined utility charge','2026-04-17 10:00:00','2026-04-17 10:00:00','2026-04-17 10:00:00'),
('e4000004-0000-0000-0000-000000000013','a1b2c3d4-0004-0004-0004-000000000004',280000,'EXPENSE','HEALTH','Stomach endoscopy at Gia An 115 Hospital + buy 7-day esomeprazole course','2026-04-18 10:00:00','2026-04-18 10:00:00','2026-04-18 10:00:00'),
('e4000004-0000-0000-0000-000000000014','a1b2c3d4-0004-0004-0004-000000000004',160000,'EXPENSE','FOOD','Weekend meal prep at home - grilled pork shoulder + rice + pickled cucumber','2026-04-19 18:00:00','2026-04-19 18:00:00','2026-04-19 18:00:00'),
('e4000004-0000-0000-0000-000000000015','a1b2c3d4-0004-0004-0004-000000000004',600000,'EXPENSE','SHOPPING','Buy Keychron K2 V2 mechanical keyboard - Gateron Brown switch from Shopee','2026-04-21 14:00:00','2026-04-21 14:00:00','2026-04-21 14:00:00'),
('e4000004-0000-0000-0000-000000000016','a1b2c3d4-0004-0004-0004-000000000004',75000,'EXPENSE','FOOD','Buy 2 Starbucks caramel macchiato tall size during late-night study session','2026-04-23 17:30:00','2026-04-23 17:30:00','2026-04-23 17:30:00'),
('e4000004-0000-0000-0000-000000000017','a1b2c3d4-0004-0004-0004-000000000004',6000000,'INCOME','OTHER','May programming income - completed UI components + family support allowance','2026-05-01 09:30:00','2026-05-01 09:30:00','2026-05-01 09:30:00'),
('e4000004-0000-0000-0000-000000000018','a1b2c3d4-0004-0004-0004-000000000004',2200000,'EXPENSE','UTILITIES','Monthly room rent - May','2026-05-02 10:00:00','2026-05-02 10:00:00','2026-05-02 10:00:00'),
('e4000004-0000-0000-0000-000000000019','a1b2c3d4-0004-0004-0004-000000000004',90000,'EXPENSE','FOOD','Breakfast banh mi with cha lua at street stall + iced milk coffee','2026-05-03 07:30:00','2026-05-03 07:30:00','2026-05-03 07:30:00'),
('e4000004-0000-0000-0000-000000000020','a1b2c3d4-0004-0004-0004-000000000004',800000,'EXPENSE','EDUCATION','Buy GitHub Copilot Individual annual subscription - productivity tool','2026-05-05 10:00:00','2026-05-05 10:00:00','2026-05-05 10:00:00'),
('e4000004-0000-0000-0000-000000000021','a1b2c3d4-0004-0004-0004-000000000004',110000,'EXPENSE','TRANSPORTATION','Fill up 6L petrol at Petrolimex + buy quarterly parking permit at RMIT','2026-05-06 08:00:00','2026-05-06 08:00:00','2026-05-06 08:00:00'),
('e4000004-0000-0000-0000-000000000022','a1b2c3d4-0004-0004-0004-000000000004',400000,'EXPENSE','ENTERTAINMENT','Team project wrap-up dinner for 5 at Nha Hang Ngon - sharing BBQ platter','2026-05-09 19:00:00','2026-05-09 19:00:00','2026-05-09 19:00:00'),
('e4000004-0000-0000-0000-000000000023','a1b2c3d4-0004-0004-0004-000000000004',85000,'EXPENSE','FOOD','Lunch at Lotteria - crispy chicken burger combo + medium fries + cola','2026-05-11 12:00:00','2026-05-11 12:00:00','2026-05-11 12:00:00'),
('e4000004-0000-0000-0000-000000000024','a1b2c3d4-0004-0004-0004-000000000004',520000,'EXPENSE','CLOTHING','Buy 3 summer T-shirts + 1 linen shorts at H&M end-of-season sale','2026-05-13 15:30:00','2026-05-13 15:30:00','2026-05-13 15:30:00'),
('e4000004-0000-0000-0000-000000000025','a1b2c3d4-0004-0004-0004-000000000004',1800000,'INCOME','OTHER','Final payment for completed company web app - invoice settled by client','2026-05-14 17:00:00','2026-05-14 17:00:00','2026-05-14 17:00:00'),
('e4000004-0000-0000-0000-000000000026','a1b2c3d4-0004-0004-0004-000000000004',120000,'EXPENSE','UTILITIES','FPT internet + room electricity - May combined bill','2026-05-17 10:00:00','2026-05-17 10:00:00','2026-05-17 10:00:00'),
('e4000004-0000-0000-0000-000000000027','a1b2c3d4-0004-0004-0004-000000000004',180000,'EXPENSE','FOOD','Weekend brunch at Cong Ca Phe - 2 coconut coffee + 1 banh beo plate','2026-05-18 11:00:00','2026-05-18 11:00:00','2026-05-18 11:00:00'),
('e4000004-0000-0000-0000-000000000028','a1b2c3d4-0004-0004-0004-000000000004',200000,'EXPENSE','HEALTH','Buy Zenni blue-light blocking glasses online - anti-fatigue lens for coding','2026-05-20 14:00:00','2026-05-20 14:00:00','2026-05-20 14:00:00'),
('e4000004-0000-0000-0000-000000000029','a1b2c3d4-0004-0004-0004-000000000004',350000,'EXPENSE','SHOPPING','Buy WD Green 256GB SSD from An Phat Computer - upgrade old laptop storage','2026-05-22 14:00:00','2026-05-22 14:00:00','2026-05-22 14:00:00'),
('e4000004-0000-0000-0000-000000000030','a1b2c3d4-0004-0004-0004-000000000004',65000,'EXPENSE','FOOD','Buy 2 cups KOI The Milk Tea + 1 classic waffle - afternoon snack','2026-05-25 15:00:00','2026-05-25 15:00:00','2026-05-25 15:00:00'),
('e4000004-0000-0000-0000-000000000031','a1b2c3d4-0004-0004-0004-000000000004',280000,'EXPENSE','ENTERTAINMENT','Book train ticket Saigon-Da Lat for weekend getaway - seat class B','2026-05-28 10:00:00','2026-05-28 10:00:00','2026-05-28 10:00:00'),
('e4000004-0000-0000-0000-000000000032','a1b2c3d4-0004-0004-0004-000000000004',500000,'EXPENSE','FOOD','Food and drinks across 2 days in Da Lat - breakfast cafes, street food, avocado shake','2026-05-30 12:00:00','2026-05-30 12:00:00','2026-05-30 12:00:00'),
('e4000004-0000-0000-0000-000000000033','a1b2c3d4-0004-0004-0004-000000000004',6500000,'INCOME','OTHER','June programming salary + semester project completion bonus','2026-06-01 09:30:00','2026-06-01 09:30:00','2026-06-01 09:30:00'),
('e4000004-0000-0000-0000-000000000034','a1b2c3d4-0004-0004-0004-000000000004',2200000,'EXPENSE','UTILITIES','Monthly room rent - June','2026-06-02 10:00:00','2026-06-02 10:00:00','2026-06-02 10:00:00'),
('e4000004-0000-0000-0000-000000000035','a1b2c3d4-0004-0004-0004-000000000004',95000,'EXPENSE','FOOD','Buy signature baguette at Banh Mi Pate Huynh Hoa + iced Vietnamese drip coffee','2026-06-03 07:30:00','2026-06-03 07:30:00','2026-06-03 07:30:00'),
('e4000004-0000-0000-0000-000000000036','a1b2c3d4-0004-0004-0004-000000000004',1000000,'EXPENSE','EDUCATION','Buy Udemy "Flutter & Dart - Complete Developer Guide 2026" course on sale','2026-06-04 10:00:00','2026-06-04 10:00:00','2026-06-04 10:00:00'),
('e4000004-0000-0000-0000-000000000037','a1b2c3d4-0004-0004-0004-000000000004',130000,'EXPENSE','TRANSPORTATION','Fill up 6L petrol at Petrolimex + pay monthly apartment parking fee','2026-06-05 08:00:00','2026-06-05 08:00:00','2026-06-05 08:00:00'),
('e4000004-0000-0000-0000-000000000038','a1b2c3d4-0004-0004-0004-000000000004',300000,'EXPENSE','FOOD','Dinner at Nuong Viet BBQ - celebrate final project submission, 5 people split','2026-06-07 19:30:00','2026-06-07 19:30:00','2026-06-07 19:30:00'),
('e4000004-0000-0000-0000-000000000039','a1b2c3d4-0004-0004-0004-000000000004',550000,'EXPENSE','CLOTHING','Buy Zara slim blazer + H&M white dress shirt for graduation ceremony','2026-06-08 15:00:00','2026-06-08 15:00:00','2026-06-08 15:00:00'),
('e4000004-0000-0000-0000-000000000040','a1b2c3d4-0004-0004-0004-000000000004',120000,'EXPENSE','UTILITIES','Electricity + water bill - June combined usage charge','2026-06-10 10:00:00','2026-06-10 10:00:00','2026-06-10 10:00:00'),
('e4000004-0000-0000-0000-000000000041','a1b2c3d4-0004-0004-0004-000000000004',2000000,'INCOME','OTHER','Final payment for 2-month startup mobile app project - 3 screens + backend integration','2026-06-11 17:00:00','2026-06-11 17:00:00','2026-06-11 17:00:00'),
('e4000004-0000-0000-0000-000000000042','a1b2c3d4-0004-0004-0004-000000000004',400000,'EXPENSE','ENTERTAINMENT','Graduation celebration at Sky Garden rooftop bar - reserved table for 5 friends','2026-06-12 20:00:00','2026-06-12 20:00:00','2026-06-12 20:00:00'),
('e4000004-0000-0000-0000-000000000043','a1b2c3d4-0004-0004-0004-000000000004',170000,'EXPENSE','FOOD','Weekend brunch at The Breakfast Club - eggs benedict + fresh orange juice','2026-06-13 11:00:00','2026-06-13 11:00:00','2026-06-13 11:00:00'),
('e4000004-0000-0000-0000-000000000044','a1b2c3d4-0004-0004-0004-000000000004',250000,'EXPENSE','HEALTH','General checkup at Medic Hospital + buy Centrum Silver multivitamin 60-tablet pack','2026-06-13 09:30:00','2026-06-13 09:30:00','2026-06-13 09:30:00'),
('e4000004-0000-0000-0000-000000000045','a1b2c3d4-0004-0004-0004-000000000004',480000,'EXPENSE','SHOPPING','Buy Logitech G502 Hero gaming mouse at FPT Shop - on-site warranty 2 years','2026-06-12 14:00:00','2026-06-12 14:00:00','2026-06-12 14:00:00'),
('e4000004-0000-0000-0000-000000000046','a1b2c3d4-0004-0004-0004-000000000004',200000,'EXPENSE','FOOD','Weekly grocery at Co.opMart - pork, salmon fillet, vegetables, cooking ingredients','2026-06-12 09:00:00','2026-06-12 09:00:00','2026-06-12 09:00:00'),
('e4000004-0000-0000-0000-000000000047','a1b2c3d4-0004-0004-0004-000000000004',350000,'EXPENSE','EDUCATION','Buy "Designing Data-Intensive Applications" book at Fahasa - for system design prep','2026-06-13 14:00:00','2026-06-13 14:00:00','2026-06-13 14:00:00'),
('e4000004-0000-0000-0000-000000000048','a1b2c3d4-0004-0004-0004-000000000004',85000,'EXPENSE','FOOD','Pho Thin beef pho breakfast - extra tendon + soft drink before exam','2026-06-14 07:00:00','2026-06-14 07:00:00','2026-06-14 07:00:00'),
('e4000004-0000-0000-0000-000000000049','a1b2c3d4-0004-0004-0004-000000000004',60000,'EXPENSE','TRANSPORTATION','Grab ride to university campus for final exam - 4.5 km trip','2026-06-14 07:30:00','2026-06-14 07:30:00','2026-06-14 07:30:00'),
('e4000004-0000-0000-0000-000000000050','a1b2c3d4-0004-0004-0004-000000000004',150000,'EXPENSE','FOOD','Post-exam dinner at Quan Bui Modern Vietnamese - celebrate with 3 classmates','2026-06-14 20:00:00','2026-06-14 20:00:00','2026-06-14 20:00:00');

-- ════════════════════════════════════════════════════════════
-- 5. BUDGETS (April, May, June 2026)
-- ════════════════════════════════════════════════════════════════
INSERT INTO public.budgets (id, user_id, category, amount_limit, spent, month, year, created_at, updated_at) VALUES
-- User 1 Minh
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001','FOOD',1500000,930000,4,2026,'2026-04-01 08:00:00','2026-04-28 19:00:00'),
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001','UTILITIES',3000000,2595000,4,2026,'2026-04-01 08:00:00','2026-04-17 10:00:00'),
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001','EDUCATION',800000,650000,4,2026,'2026-04-01 08:00:00','2026-04-05 08:00:00'),
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001','TRANSPORTATION',300000,175000,4,2026,'2026-04-01 08:00:00','2026-04-26 07:00:00'),
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001','ENTERTAINMENT',400000,200000,4,2026,'2026-04-01 08:00:00','2026-04-12 20:00:00'),
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001','CLOTHING',500000,350000,4,2026,'2026-04-01 08:00:00','2026-04-08 16:00:00'),
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001','HEALTH',300000,180000,4,2026,'2026-04-01 08:00:00','2026-04-15 09:00:00'),
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001','FOOD',1500000,915000,5,2026,'2026-05-01 08:00:00','2026-05-30 12:00:00'),
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001','UTILITIES',3000000,2597000,5,2026,'2026-05-01 08:00:00','2026-05-18 10:00:00'),
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001','EDUCATION',1500000,1200000,5,2026,'2026-05-01 08:00:00','2026-05-06 10:00:00'),
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001','ENTERTAINMENT',500000,420000,5,2026,'2026-05-01 08:00:00','2026-05-28 07:00:00'),
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001','FOOD',1500000,0,6,2026,'2026-06-01 08:00:00','2026-06-01 08:00:00'),
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001','UTILITIES',3000000,2600000,6,2026,'2026-06-01 08:00:00','2026-06-10 10:00:00'),
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001','EDUCATION',1200000,1250000,6,2026,'2026-06-01 08:00:00','2026-06-13 11:00:00'),
-- User 2 Nam Tran
(gen_random_uuid(),'a1b2c3d4-0002-0002-0002-000000000002','FOOD',700000,550000,4,2026,'2026-04-02 09:00:00','2026-04-30 12:00:00'),
(gen_random_uuid(),'a1b2c3d4-0002-0002-0002-000000000002','UTILITIES',1000000,880000,4,2026,'2026-04-02 09:00:00','2026-04-19 10:00:00'),
(gen_random_uuid(),'a1b2c3d4-0002-0002-0002-000000000002','EDUCATION',600000,350000,4,2026,'2026-04-02 09:00:00','2026-04-05 09:00:00'),
(gen_random_uuid(),'a1b2c3d4-0002-0002-0002-000000000002','ENTERTAINMENT',300000,150000,4,2026,'2026-04-02 09:00:00','2026-04-13 21:00:00'),
(gen_random_uuid(),'a1b2c3d4-0002-0002-0002-000000000002','FOOD',700000,555000,5,2026,'2026-05-01 09:00:00','2026-05-30 20:00:00'),
(gen_random_uuid(),'a1b2c3d4-0002-0002-0002-000000000002','UTILITIES',1000000,890000,5,2026,'2026-05-01 09:00:00','2026-05-20 10:00:00'),
(gen_random_uuid(),'a1b2c3d4-0002-0002-0002-000000000002','EDUCATION',800000,700000,5,2026,'2026-05-01 09:00:00','2026-05-05 09:00:00'),
(gen_random_uuid(),'a1b2c3d4-0002-0002-0002-000000000002','FOOD',700000,0,6,2026,'2026-06-01 09:00:00','2026-06-01 09:00:00'),
(gen_random_uuid(),'a1b2c3d4-0002-0002-0002-000000000002','EDUCATION',900000,890000,6,2026,'2026-06-01 09:00:00','2026-06-12 14:00:00'),
-- User 3 Thinh Vuong
(gen_random_uuid(),'a1b2c3d4-0003-0003-0003-000000000003','FOOD',1200000,945000,4,2026,'2026-04-03 10:00:00','2026-04-29 12:00:00'),
(gen_random_uuid(),'a1b2c3d4-0003-0003-0003-000000000003','TRANSPORTATION',300000,165000,4,2026,'2026-04-03 10:00:00','2026-04-18 08:00:00'),
(gen_random_uuid(),'a1b2c3d4-0003-0003-0003-000000000003','EDUCATION',600000,450000,4,2026,'2026-04-03 10:00:00','2026-04-05 09:00:00'),
(gen_random_uuid(),'a1b2c3d4-0003-0003-0003-000000000003','ENTERTAINMENT',400000,250000,4,2026,'2026-04-03 10:00:00','2026-04-12 15:00:00'),
(gen_random_uuid(),'a1b2c3d4-0003-0003-0003-000000000003','FOOD',1200000,980000,5,2026,'2026-05-01 10:00:00','2026-05-30 18:00:00'),
(gen_random_uuid(),'a1b2c3d4-0003-0003-0003-000000000003','EDUCATION',700000,600000,5,2026,'2026-05-01 10:00:00','2026-05-05 09:00:00'),
(gen_random_uuid(),'a1b2c3d4-0003-0003-0003-000000000003','ENTERTAINMENT',400000,350000,5,2026,'2026-05-01 10:00:00','2026-05-09 16:00:00'),
(gen_random_uuid(),'a1b2c3d4-0003-0003-0003-000000000003','FOOD',1200000,0,6,2026,'2026-06-01 10:00:00','2026-06-01 10:00:00'),
(gen_random_uuid(),'a1b2c3d4-0003-0003-0003-000000000003','EDUCATION',600000,500000,6,2026,'2026-06-01 10:00:00','2026-06-08 09:00:00'),
-- User 4 Hai Bang
(gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004','FOOD',1200000,820000,4,2026,'2026-04-04 11:00:00','2026-04-28 19:00:00'),
(gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004','UTILITIES',2500000,2320000,4,2026,'2026-04-04 11:00:00','2026-04-17 10:00:00'),
(gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004','EDUCATION',700000,500000,4,2026,'2026-04-04 11:00:00','2026-04-05 10:00:00'),
(gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004','ENTERTAINMENT',500000,350000,4,2026,'2026-04-04 11:00:00','2026-04-12 20:00:00'),
(gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004','SHOPPING',700000,600000,4,2026,'2026-04-04 11:00:00','2026-04-21 14:00:00'),
(gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004','FOOD',1200000,1105000,5,2026,'2026-05-01 11:00:00','2026-05-30 12:00:00'),
(gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004','UTILITIES',2500000,2320000,5,2026,'2026-05-01 11:00:00','2026-05-17 10:00:00'),
(gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004','EDUCATION',1000000,800000,5,2026,'2026-05-01 11:00:00','2026-05-05 10:00:00'),
(gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004','ENTERTAINMENT',600000,680000,5,2026,'2026-05-01 11:00:00','2026-05-30 12:00:00'),
(gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004','FOOD',1200000,0,6,2026,'2026-06-01 11:00:00','2026-06-01 11:00:00'),
(gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004','UTILITIES',2500000,2320000,6,2026,'2026-06-01 11:00:00','2026-06-10 10:00:00'),
(gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004','EDUCATION',1200000,1350000,6,2026,'2026-06-01 11:00:00','2026-06-13 14:00:00');

-- ════════════════════════════════════════════════════════════
-- 6. PROJECTS
-- Personal project targets: 1-10M VND
-- ════════════════════════════════════════════════════════════════
INSERT INTO public.projects (id, owner_id, type, name, status, priority, description, target_amount, currency, deadline, created_at, updated_at) VALUES
-- Project 1 (PERSONAL - Minh): Samsung Galaxy Watch 7
('f1000001-0001-0001-0001-000000000001',
 'a1b2c3d4-0001-0001-0001-000000000001',
 'PERSONAL','Buy Samsung Galaxy Watch 7','ACTIVE','MEDIUM',
 'Saving up for a Samsung Galaxy Watch 7 46mm. Useful for tracking workouts and coding session breaks. Target by August.',
 6000000.00,'VND','2026-08-31',
 '2026-04-10 09:00:00+07','2026-06-10 10:00:00+07'),

-- Project 2 (PERSONAL - Nam Tran): IELTS exam fund
('f2000002-0002-0002-0002-000000000002',
 'a1b2c3d4-0002-0002-0002-000000000002',
 'PERSONAL','IELTS Exam and Prep Materials Fund','ACTIVE','HIGH',
 'Saving for IELTS exam registration fee 3.4M + prep books + mock test subscription. Required for scholarship application.',
 4000000.00,'VND','2026-08-15',
 '2026-04-15 10:00:00+07','2026-06-12 09:00:00+07'),

-- Project 3 (GROUP - Thinh Vuong + Hai Bang + Minh): Da Nang summer trip
('f3000003-0003-0003-0003-000000000003',
 'a1b2c3d4-0003-0003-0003-000000000003',
 'GROUP','Da Nang Summer Trip 2026','ACTIVE','MEDIUM',
 '4-person group trip: Da Nang - Hoi An - Ba Na Hills. Covers round-trip flights from SGN, 3-night hotel, and shared food budget.',
 12000000.00,'VND','2026-08-15',
 '2026-05-01 08:00:00+07','2026-06-13 08:00:00+07'),

-- Project 4 (GROUP - Hai Bang + Minh + Thinh Vuong): startup fund
('f4000004-0004-0004-0004-000000000004',
 'a1b2c3d4-0004-0004-0004-000000000004',
 'GROUP','SmartBudget Startup Fund','ACTIVE','HIGH',
 'Pooled fund for the 3-person startup team: domain + SSL 1 year, VPS hosting, Figma Pro plan, and initial Google Ads budget.',
 20000000.00,'VND','2026-09-30',
 '2026-05-10 10:00:00+07','2026-06-14 09:00:00+07'),

-- Project 5 (PERSONAL - Hai Bang): Dell 27-inch monitor
('f5000005-0004-0004-0004-000000000005',
 'a1b2c3d4-0004-0004-0004-000000000004',
 'PERSONAL','Buy Dell 27-inch 2K Monitor','ACTIVE','MEDIUM',
 'Saving for a Dell S2722QC 27-inch 4K USB-C monitor to replace old 21-inch screen. Better for coding dual-screen setup.',
 8000000.00,'VND','2026-10-15',
 '2026-04-20 11:00:00+07','2026-06-05 11:00:00+07');

-- ════════════════════════════════════════════════════════════
-- 7. PROJECT MEMBERS (GROUP projects only)
-- ════════════════════════════════════════════════════════════════
INSERT INTO public.project_members (id, project_id, user_id, join_status, is_admin, created_at, updated_at) VALUES
-- proj3 (Da Nang Trip): Hai Bang JOINED, Minh JOINED, Nam Tran INVITED
('d3000001-0001-0001-0001-000000000001','f3000003-0003-0003-0003-000000000003',
 'a1b2c3d4-0004-0004-0004-000000000004','JOINED',FALSE,
 '2026-05-02 09:00:00+07','2026-05-03 10:00:00+07'),
('d3000002-0002-0002-0002-000000000002','f3000003-0003-0003-0003-000000000003',
 'a1b2c3d4-0001-0001-0001-000000000001','JOINED',FALSE,
 '2026-05-02 09:30:00+07','2026-05-04 11:00:00+07'),
('d3000003-0003-0003-0003-000000000003','f3000003-0003-0003-0003-000000000003',
 'a1b2c3d4-0002-0002-0002-000000000002','INVITED',FALSE,
 '2026-05-03 10:00:00+07','2026-05-03 10:00:00+07'),
-- proj4 (SmartBudget Startup): Minh JOINED admin, Thinh Vuong JOINED
('d4000001-0001-0001-0001-000000000001','f4000004-0004-0004-0004-000000000004',
 'a1b2c3d4-0001-0001-0001-000000000001','JOINED',TRUE,
 '2026-05-11 10:00:00+07','2026-05-12 09:00:00+07'),
('d4000002-0002-0002-0002-000000000002','f4000004-0004-0004-0004-000000000004',
 'a1b2c3d4-0003-0003-0003-000000000003','JOINED',FALSE,
 '2026-05-11 11:00:00+07','2026-05-13 10:00:00+07');

-- ════════════════════════════════════════════════════════════
-- 8. PROJECT CONTRIBUTIONS
-- ════════════════════════════════════════════════════════════════
INSERT INTO public.project_contributions (id, project_id, user_id, amount, currency, note, created_at) VALUES
-- proj1 Samsung Watch (Minh PERSONAL) - total 3,700,000
(gen_random_uuid(),'f1000001-0001-0001-0001-000000000001','a1b2c3d4-0001-0001-0001-000000000001',
 1200000.00,'VND','April savings from freelance project surplus','2026-04-30 20:00:00+07'),
(gen_random_uuid(),'f1000001-0001-0001-0001-000000000001','a1b2c3d4-0001-0001-0001-000000000001',
 1500000.00,'VND','May savings - extra income from cafe landing page project','2026-05-31 20:00:00+07'),
(gen_random_uuid(),'f1000001-0001-0001-0001-000000000001','a1b2c3d4-0001-0001-0001-000000000001',
 1000000.00,'VND','June top-up from React Native freelance payment','2026-06-10 20:00:00+07'),

-- proj2 IELTS Fund (Nam Tran PERSONAL) - total 2,800,000
(gen_random_uuid(),'f2000002-0002-0002-0002-000000000002','a1b2c3d4-0002-0002-0002-000000000002',
 800000.00,'VND','April savings from merit scholarship bonus','2026-04-30 21:00:00+07'),
(gen_random_uuid(),'f2000002-0002-0002-0002-000000000002','a1b2c3d4-0002-0002-0002-000000000002',
 1200000.00,'VND','May savings - cut entertainment spending to boost fund','2026-05-31 21:00:00+07'),
(gen_random_uuid(),'f2000002-0002-0002-0002-000000000002','a1b2c3d4-0002-0002-0002-000000000002',
 800000.00,'VND','June contribution from excellence scholarship bonus','2026-06-12 21:00:00+07'),

-- proj3 Da Nang Trip (GROUP - Thinh Vuong, Hai Bang, Minh JOINED) - total 6,500,000
(gen_random_uuid(),'f3000003-0003-0003-0003-000000000003','a1b2c3d4-0003-0003-0003-000000000003',
 1500000.00,'VND','Thinh Vuong - paid flight deposit for 4 seats on VietJet SGN-DAD','2026-05-15 10:00:00+07'),
(gen_random_uuid(),'f3000003-0003-0003-0003-000000000003','a1b2c3d4-0004-0004-0004-000000000004',
 1500000.00,'VND','Hai Bang - personal share for flights and hotel booking','2026-05-17 11:00:00+07'),
(gen_random_uuid(),'f3000003-0003-0003-0003-000000000003','a1b2c3d4-0001-0001-0001-000000000001',
 1500000.00,'VND','Minh - first installment for shared trip expenses','2026-05-20 09:00:00+07'),
(gen_random_uuid(),'f3000003-0003-0003-0003-000000000003','a1b2c3d4-0003-0003-0003-000000000003',
 1000000.00,'VND','Thinh Vuong - second round to cover 3-night hotel at Danang Wink','2026-06-05 10:00:00+07'),
(gen_random_uuid(),'f3000003-0003-0003-0003-000000000003','a1b2c3d4-0004-0004-0004-000000000004',
 500000.00,'VND','Hai Bang - top up for Ba Na Hills cable car + Golden Bridge tickets','2026-06-07 10:00:00+07'),

-- proj4 SmartBudget Startup (GROUP - Hai Bang, Minh, Thinh Vuong JOINED) - total 10,000,000
(gen_random_uuid(),'f4000004-0004-0004-0004-000000000004','a1b2c3d4-0004-0004-0004-000000000004',
 3000000.00,'VND','Hai Bang - initial capital: domain + VPS Vultr 1 year + SSL','2026-05-15 10:00:00+07'),
(gen_random_uuid(),'f4000004-0004-0004-0004-000000000004','a1b2c3d4-0001-0001-0001-000000000001',
 2000000.00,'VND','Minh - technical fund: dev tools, API subscriptions, Firebase plan','2026-05-18 11:00:00+07'),
(gen_random_uuid(),'f4000004-0004-0004-0004-000000000004','a1b2c3d4-0003-0003-0003-000000000003',
 1500000.00,'VND','Thinh Vuong - design budget: Figma Pro annual + stock photo credits','2026-05-22 09:00:00+07'),
(gen_random_uuid(),'f4000004-0004-0004-0004-000000000004','a1b2c3d4-0004-0004-0004-000000000004',
 2000000.00,'VND','Hai Bang - June marketing budget: Google Ads test campaign','2026-06-08 10:00:00+07'),
(gen_random_uuid(),'f4000004-0004-0004-0004-000000000004','a1b2c3d4-0001-0001-0001-000000000001',
 1500000.00,'VND','Minh - June contribution: cover additional backend hosting costs','2026-06-10 10:00:00+07'),

-- proj5 Dell Monitor (Hai Bang PERSONAL) - total 3,500,000
(gen_random_uuid(),'f5000005-0004-0004-0004-000000000005','a1b2c3d4-0004-0004-0004-000000000004',
 1000000.00,'VND','April savings from freelance income surplus','2026-04-28 20:00:00+07'),
(gen_random_uuid(),'f5000005-0004-0004-0004-000000000005','a1b2c3d4-0004-0004-0004-000000000004',
 1500000.00,'VND','May savings after rent and fixed expenses','2026-05-31 20:00:00+07'),
(gen_random_uuid(),'f5000005-0004-0004-0004-000000000005','a1b2c3d4-0004-0004-0004-000000000004',
 1000000.00,'VND','June contribution from startup app final payment','2026-06-11 20:00:00+07');

-- ════════════════════════════════════════════════════════════
-- 9. PROJECT TRACKING
-- ════════════════════════════════════════════════════════════════
INSERT INTO public.project_tracking (id, project_id, money_saved, monthly_saving, current_month, month_left, money_owed, created_at, updated_at) VALUES
-- proj1 Watch (target 6M, saved 3.7M, 2 months left to Aug deadline)
(gen_random_uuid(),'f1000001-0001-0001-0001-000000000001',
 3700000.00,1150000.00,3,2,0.00,
 '2026-04-10 09:00:00+07','2026-06-10 20:00:00+07'),
-- proj2 IELTS (target 4M, saved 2.8M, 2 months left to Aug deadline)
(gen_random_uuid(),'f2000002-0002-0002-0002-000000000002',
 2800000.00,600000.00,3,2,0.00,
 '2026-04-15 10:00:00+07','2026-06-12 21:00:00+07'),
-- proj3 Da Nang Trip (target 12M, saved 6.5M, 2 months left to Aug deadline)
(gen_random_uuid(),'f3000003-0003-0003-0003-000000000003',
 6500000.00,2750000.00,2,2,0.00,
 '2026-05-01 08:00:00+07','2026-06-07 10:00:00+07'),
-- proj4 Startup (target 20M, saved 10M, 4 months left to Sep deadline)
(gen_random_uuid(),'f4000004-0004-0004-0004-000000000004',
 10000000.00,2500000.00,2,4,0.00,
 '2026-05-10 10:00:00+07','2026-06-10 10:00:00+07'),
-- proj5 Monitor (target 8M, saved 3.5M, 5 months left to Oct deadline)
(gen_random_uuid(),'f5000005-0004-0004-0004-000000000005',
 3500000.00,900000.00,3,5,0.00,
 '2026-04-20 11:00:00+07','2026-06-11 20:00:00+07');

-- ════════════════════════════════════════════════════════════
-- 10. PROJECT HISTORY (monthly tracking snapshots)
-- ════════════════════════════════════════════════════════════════
INSERT INTO public.project_history (
    id, project_id, user_id, month, year,
    money_saved_before, money_saved_after,
    monthly_saving, penalty, surplus_invested,
    month_left_before, month_left_after, created_at
) VALUES
-- proj1 Samsung Watch (Minh)
(gen_random_uuid(),'f1000001-0001-0001-0001-000000000001','a1b2c3d4-0001-0001-0001-000000000001',
 4,2026, 0,1200000,1200000,0,0, 5,4,'2026-04-30 23:59:00+07'),
(gen_random_uuid(),'f1000001-0001-0001-0001-000000000001','a1b2c3d4-0001-0001-0001-000000000001',
 5,2026, 1200000,2700000,1500000,0,0, 4,3,'2026-05-31 23:59:00+07'),
-- proj2 IELTS Fund (Nam Tran)
(gen_random_uuid(),'f2000002-0002-0002-0002-000000000002','a1b2c3d4-0002-0002-0002-000000000002',
 4,2026, 0,800000,800000,0,0, 4,3,'2026-04-30 23:59:00+07'),
(gen_random_uuid(),'f2000002-0002-0002-0002-000000000002','a1b2c3d4-0002-0002-0002-000000000002',
 5,2026, 800000,2000000,1200000,0,0, 3,2,'2026-05-31 23:59:00+07'),
-- proj3 Da Nang Trip (GROUP - Thinh Vuong)
(gen_random_uuid(),'f3000003-0003-0003-0003-000000000003','a1b2c3d4-0003-0003-0003-000000000003',
 5,2026, 0,4500000,4500000,0,0, 3,2,'2026-05-31 23:59:00+07'),
-- proj4 SmartBudget Startup (Hai Bang)
(gen_random_uuid(),'f4000004-0004-0004-0004-000000000004','a1b2c3d4-0004-0004-0004-000000000004',
 5,2026, 0,6500000,6500000,0,0, 5,4,'2026-05-31 23:59:00+07'),
-- proj5 Dell Monitor (Hai Bang)
(gen_random_uuid(),'f5000005-0004-0004-0004-000000000005','a1b2c3d4-0004-0004-0004-000000000004',
 4,2026, 0,1000000,1000000,0,0, 7,6,'2026-04-30 23:59:00+07'),
(gen_random_uuid(),'f5000005-0004-0004-0004-000000000005','a1b2c3d4-0004-0004-0004-000000000004',
 5,2026, 1000000,2500000,1500000,0,0, 6,5,'2026-05-31 23:59:00+07');

-- ════════════════════════════════════════════════════════════
-- 11. NOTIFICATIONS
-- ════════════════════════════════════════════════════════════════
INSERT INTO public.notifications (id, user_id, content, created_at) VALUES
-- Minh
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001',
 'Welcome to SmartMoney! Set up your financial profile to unlock personalized spending insights.','2026-04-01 08:00:00'),
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001',
 'EDUCATION budget for April is at 81% with 10 days remaining. Consider pausing non-essential purchases.','2026-04-05 08:30:00'),
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001',
 'You contributed 1,200,000 VND to "Buy Samsung Galaxy Watch 7" - 20% of goal reached!','2026-04-30 20:05:00'),
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001',
 'Ngo Hai Bang invited you to join the group project "SmartBudget Startup Fund". Tap to review.','2026-05-11 10:05:00'),
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001',
 'Project "Buy Samsung Galaxy Watch 7" reached 61.7% - only 2,300,000 VND left to go!','2026-06-10 20:05:00'),
(gen_random_uuid(),'a1b2c3d4-0001-0001-0001-000000000001',
 'EDUCATION budget for June exceeded 104%. You spent 50,000 VND over the limit this month.','2026-06-13 11:05:00'),
-- Nam Tran
(gen_random_uuid(),'a1b2c3d4-0002-0002-0002-000000000002',
 'Welcome to SmartMoney! Configure your income source to get AI-powered saving recommendations.','2026-04-02 09:00:00'),
(gen_random_uuid(),'a1b2c3d4-0002-0002-0002-000000000002',
 'Scholarship merit bonus of 400,000 VND received. Redirect it to your IELTS fund to hit the goal faster!','2026-04-17 15:05:00'),
(gen_random_uuid(),'a1b2c3d4-0002-0002-0002-000000000002',
 'May contribution of 1,200,000 VND added to "IELTS Exam and Prep Materials Fund" - 50% complete!','2026-05-31 21:05:00'),
(gen_random_uuid(),'a1b2c3d4-0002-0002-0002-000000000002',
 'Vo Thinh Vuong invited you to join "Da Nang Summer Trip 2026". Accept to start contributing.','2026-05-03 10:05:00'),
(gen_random_uuid(),'a1b2c3d4-0002-0002-0002-000000000002',
 'IELTS fund is at 70% - 1,200,000 VND remaining. On track for August deadline!','2026-06-12 21:05:00'),
-- Thinh Vuong
(gen_random_uuid(),'a1b2c3d4-0003-0003-0003-000000000003',
 'Welcome to SmartMoney! Start tracking your cafe wages and set savings milestones.','2026-04-03 10:00:00'),
(gen_random_uuid(),'a1b2c3d4-0003-0003-0003-000000000003',
 'FOOD budget for April at 78%. Great discipline - you are spending within plan this month.','2026-04-22 12:00:00'),
(gen_random_uuid(),'a1b2c3d4-0003-0003-0003-000000000003',
 'Group project "Da Nang Summer Trip 2026" created. Hai Bang and Minh have been invited to join.','2026-05-02 09:05:00'),
(gen_random_uuid(),'a1b2c3d4-0003-0003-0003-000000000003',
 'Ngo Hai Bang invited you to join the startup project. 5 spots filled - join before deadline.','2026-05-11 11:05:00'),
(gen_random_uuid(),'a1b2c3d4-0003-0003-0003-000000000003',
 'Da Nang Trip project hit 54% of target! Deadline in 2 months - keep the monthly saves going.','2026-06-07 10:05:00'),
-- Hai Bang
(gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004',
 'Welcome to SmartMoney! Link your income streams and set budget limits for each category.','2026-04-04 11:00:00'),
(gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004',
 'ENTERTAINMENT budget for May exceeded 113%. 80,000 VND over limit - review discretionary spending.','2026-05-30 12:05:00'),
(gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004',
 '"SmartBudget Startup Fund" now has 3 active members. Total pooled so far: 6,500,000 VND.','2026-05-13 10:05:00'),
(gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004',
 'Startup project hit the 50% milestone - 10,000,000 VND saved! On pace for September deadline.','2026-06-10 10:05:00'),
(gen_random_uuid(),'a1b2c3d4-0004-0004-0004-000000000004',
 '"Buy Dell 27-inch 2K Monitor" is at 43.75% - deposit 900,000 VND/month to stay on track.','2026-06-11 20:05:00');

-- ════════════════════════════════════════════════════════════════
-- Verify (uncomment to run)
-- SELECT tablename, count FROM (
--   SELECT ''users'' t, count(*) n FROM public.users UNION ALL
--   SELECT ''transactions'', count(*) FROM public.transactions UNION ALL
--   SELECT ''budgets'', count(*) FROM public.budgets UNION ALL
--   SELECT ''projects'', count(*) FROM public.projects UNION ALL
--   SELECT ''project_members'', count(*) FROM public.project_members UNION ALL
--   SELECT ''project_contributions'', count(*) FROM public.project_contributions UNION ALL
--   SELECT ''project_tracking'', count(*) FROM public.project_tracking UNION ALL
--   SELECT ''project_history'', count(*) FROM public.project_history UNION ALL
--   SELECT ''notifications'', count(*) FROM public.notifications UNION ALL
--   SELECT ''user_financial_profiles'', count(*) FROM public.user_financial_profiles UNION ALL
--   SELECT ''user_income'', count(*) FROM public.user_income) x;
-- ════════════════════════════════════════════════════════════════