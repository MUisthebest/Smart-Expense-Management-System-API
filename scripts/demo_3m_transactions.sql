-- ============================================================================
-- SEED: 3 tháng giao dịch cho tài khoản demo (`demo-3m` trong docs/kich_ban_demo.md §2.2)
-- ============================================================================
--
-- KHÔNG PHẢI MIGRATION. File này nằm ngoài `classpath:db/migration` nên Flyway
-- không tự chạy. Chạy tay bằng psql / DBeaver khi cần dựng lại dữ liệu demo.
--
--   psql -h localhost -U postgres -d smartmoney -f src/main/resources/db/seed/demo_3m_transactions.sql
--
-- ---------------------------------------------------------------------------
-- TRƯỚC KHI CHẠY: thay `v_user_id` ở dòng DECLARE bên dưới bằng user id thật.
-- Giá trị hiện tại là placeholder; script sẽ RAISE EXCEPTION nếu id đó không
-- tồn tại trong bảng `users` (tránh lỗi khóa ngoại khó đọc).
-- ---------------------------------------------------------------------------
--
-- CỬA SỔ THỜI GIAN — luôn là 3 tháng liền TRƯỚC tháng hiện tại:
--
--     M-3 ────────── M-2 ────────── M-1 ────────── M-0 (tháng hiện tại, KHÔNG seed)
--     └──────── phạm vi seed ───────┘
--
-- Mọi mốc đều tính từ `CURRENT_DATE` lúc chạy, nên chạy tháng nào cũng ra đúng
-- 3 tháng đã đóng sổ gần nhất. Ngày trong tháng được kẹp bằng LEAST(day, số ngày
-- của tháng đó) → chạy vào tháng 3 (khi M-1 là tháng 2) không bị tràn sang tháng sau.
--
-- IDEMPOTENT: xóa sạch giao dịch của user trong đúng cửa sổ 3 tháng rồi mới chèn.
-- ⚠ Lệnh DELETE này cũng xóa cả giao dịch PROJECT_CONTRIBUTION nằm trong cửa sổ.
--   Với tài khoản demo thì đúng ý; đừng trỏ script vào user thật.
--
-- ---------------------------------------------------------------------------
-- HỒ SƠ NHÂN VẬT (khớp KB1/KB2 trong docs/kich_ban_demo_user_story_v1.md)
--   Minh — mới đi làm, lương 15.000.000₫/tháng, ở trọ ghép tại TP.HCM,
--   chạy xe máy, gửi tiền phụ giúp gia đình hằng tháng.
--
-- TỔNG QUAN 3 THÁNG
--   | Tháng |        Thu |        Chi |  Còn lại |
--   |-------|-----------:|-----------:|---------:|
--   | M-3   | 15.500.000 |  9.849.000 | 5.651.000|
--   | M-2   | 17.000.000 | 11.294.000 | 5.706.000|
--   | M-1   | 18.000.000 | 12.514.000 | 5.486.000|
--   138 giao dịch (43 + 46 + 49).
--
-- ---------------------------------------------------------------------------
-- DỮ LIỆU ĐƯỢC THIẾT KẾ ĐỂ ENGINE SINH INSIGHT THẬT
-- (đối chiếu SpendingInsightAnalyzer.java — asOf = hôm nay, period = tháng hiện tại)
--
--  ✅ CATEGORY_TREND (WARN) — FOOD leo thang 2.600.000 → 3.100.000 → 3.700.000.
--     slope ≈ 550.000, mean ≈ 3.133.333 → growthRate ≈ 0,18 (ngưỡng WARN 0,10;
--     CRITICAL 0,25) trên đúng 3 tháng = MIN_TREND_SAMPLE.
--
--  ✅ RECURRING (INFO) — 10 khoản cố định lặp đủ 3 tháng, mọi tháng nằm trong
--     ±15% quanh trung vị (RECURRING_TOLERANCE), tổng ≈ 5.639.000₫/tháng:
--       Tiền thuê phòng trọ 2.800.000 · Tiền điện nước phòng trọ ~420.000
--       Cước Internet FPT 220.000 · Cước điện thoại Viettel 90.000
--       Gói tập gym California 400.000 · Gói Netflix hộ gia đình 220.000
--       Spotify Premium 59.000 · Gửi tiền phụ giúp gia đình 1.000.000
--       Đổ xăng xe máy ~310.000 · Vé gửi xe máy tháng 120.000
--     Các khoản ăn uống/mua sắm cố ý đặt tên KHÁC nhau giữa các tháng để không
--     lọt vào nhóm recurring ngoài ý muốn (detector gộp theo category + mô tả).
--
--  ✅ SAVINGS_CAPACITY (INFO) — thu nhập trung bình ≈ 16.833.333₫, chi phí cố
--     định ≈ 5.639.000₫ → disposable dương kể cả sau khi trừ hạn mức ngân sách.
--
--  ✅ OVERSPEND_ANOMALY — đủ MIN_SAMPLE = 3 tháng nền cho mọi danh mục.
--
--  ⚠ KHÔNG tự bật (cần dữ liệu THÁNG HIỆN TẠI, mà đề bài chỉ seed 3 tháng trước):
--       BUDGET_PACE            — so hạn mức với chi tiêu tháng đang chạy
--       BUDGET_UNDERSPEND      — cũng cần pace của tháng hiện tại
--       SUBSCRIPTION_PRICE_HIKE— cần 3 tháng nền ổn định + tháng hiện tại tăng giá
--     Ba loại này sẽ nổ ngay khi user ghi giao dịch tháng này — tức là đúng lúc
--     diễn KB1 (giọng nói / OCR / thông báo ngân hàng). Nếu muốn chúng có sẵn
--     trước khi lên sân khấu thì cần seed thêm tháng hiện tại (xem cuối file).
-- ============================================================================

BEGIN;

DO $$
DECLARE
    -- >>> THAY BẰNG USER ID THẬT TRƯỚC KHI CHẠY <<<
    v_user_id  UUID := '00000000-0000-0000-0000-000000000000';

    v_m0       DATE;   -- ngày đầu tháng hiện tại (mốc chặn trên, không seed)
    v_m1       DATE;   -- ngày đầu tháng liền trước
    v_m2       DATE;
    v_m3       DATE;   -- ngày đầu tháng cách đây 3 tháng
    v_len1     INT;    -- số ngày của từng tháng, dùng để kẹp ngày (tháng 2!)
    v_len2     INT;
    v_len3     INT;
    v_deleted  INT;
    v_inserted INT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM users WHERE id = v_user_id) THEN
        RAISE EXCEPTION
            'User % không tồn tại. Hãy sửa v_user_id ở đầu file thành id của tài khoản demo.',
            v_user_id;
    END IF;

    v_m0 := date_trunc('month', CURRENT_DATE)::date;
    v_m1 := (v_m0 - INTERVAL '1 month')::date;
    v_m2 := (v_m0 - INTERVAL '2 months')::date;
    v_m3 := (v_m0 - INTERVAL '3 months')::date;

    v_len1 := EXTRACT(DAY FROM (v_m1 + INTERVAL '1 month - 1 day'))::int;
    v_len2 := EXTRACT(DAY FROM (v_m2 + INTERVAL '1 month - 1 day'))::int;
    v_len3 := EXTRACT(DAY FROM (v_m3 + INTERVAL '1 month - 1 day'))::int;

    RAISE NOTICE 'Seed cửa sổ [% .. %) cho user %', v_m3, v_m0, v_user_id;

    -- Dọn trước để chạy lại nhiều lần vẫn ra đúng 138 dòng.
    DELETE FROM transactions
     WHERE user_id = v_user_id
       AND date >= v_m3::timestamp
       AND date <  v_m0::timestamp;
    GET DIAGNOSTICS v_deleted = ROW_COUNT;
    RAISE NOTICE 'Đã xóa % giao dịch cũ trong cửa sổ.', v_deleted;

    -- ========================================================================
    -- THÁNG M-3 — 43 giao dịch. Thu 15.500.000 / Chi 9.849.000
    -- FOOD 2.600.000 · TRANSPORTATION 580.000 · UTILITIES 3.490.000
    -- ENTERTAINMENT 429.000 · HEALTH 500.000 · SHOPPING 500.000
    -- CLOTHING 300.000 · EDUCATION 150.000 · OTHER 1.300.000
    -- ========================================================================
    INSERT INTO transactions
        (id, user_id, amount, type, category, description, date, project_id, created_at, updated_at)
    SELECT gen_random_uuid(),
           v_user_id,
           t.amount,
           t.type,
           t.category,
           t.description,
           (v_m3 + make_interval(days => LEAST(t.d, v_len3) - 1) + t.tod)::timestamp,
           NULL,
           (v_m3 + make_interval(days => LEAST(t.d, v_len3) - 1) + t.tod)::timestamp,
           (v_m3 + make_interval(days => LEAST(t.d, v_len3) - 1) + t.tod)::timestamp
      FROM (VALUES
        ( 2::int,      25000::numeric, 'EXPENSE'::varchar, 'FOOD'::varchar,           'Bánh mì thịt nướng vỉa hè'::varchar,   TIME '07:15'),
        ( 3,          120000, 'EXPENSE', 'TRANSPORTATION', 'Vé gửi xe máy tháng',                 TIME '08:10'),
        ( 3,           45000, 'EXPENSE', 'FOOD',           'Cơm trưa quán gần công ty',           TIME '12:05'),
        ( 3,           80000, 'EXPENSE', 'TRANSPORTATION', 'Đổ xăng xe máy',                      TIME '18:40'),
        ( 4,           55000, 'EXPENSE', 'FOOD',           'Cà phê sữa đá Highlands',             TIME '09:30'),
        ( 5,        15000000, 'INCOME',  'SALARY',         'Lương tháng',                         TIME '09:00'),
        ( 5,          180000, 'EXPENSE', 'FOOD',           'Đi chợ đầu tháng nấu ăn',             TIME '17:45'),
        ( 5,         2800000, 'EXPENSE', 'UTILITIES',      'Tiền thuê phòng trọ',                 TIME '19:00'),
        ( 6,           60000, 'EXPENSE', 'FOOD',           'Trà sữa Phúc Long size L',            TIME '15:10'),
        ( 6,          400000, 'EXPENSE', 'HEALTH',         'Gói tập gym California',              TIME '18:20'),
        ( 6,         1000000, 'EXPENSE', 'OTHER',          'Gửi tiền phụ giúp gia đình',          TIME '20:15'),
        ( 8,           50000, 'EXPENSE', 'FOOD',           'Cơm tấm sườn bì chả',                 TIME '12:15'),
        ( 8,          380000, 'EXPENSE', 'UTILITIES',      'Tiền điện nước phòng trọ',            TIME '19:30'),
        ( 9,          220000, 'EXPENSE', 'FOOD',           'Ăn tối cùng đồng nghiệp',             TIME '19:40'),
        (10,           70000, 'EXPENSE', 'TRANSPORTATION', 'Đổ xăng xe máy',                      TIME '08:05'),
        (10,          220000, 'EXPENSE', 'UTILITIES',      'Cước Internet FPT',                   TIME '10:00'),
        (11,          210000, 'EXPENSE', 'FOOD',           'Đi chợ cuối tuần',                    TIME '08:50'),
        (12,           55000, 'EXPENSE', 'FOOD',           'Bún bò Huế O Xuân',                   TIME '12:20'),
        (12,           90000, 'EXPENSE', 'UTILITIES',      'Cước điện thoại Viettel',             TIME '10:30'),
        (13,           65000, 'EXPENSE', 'TRANSPORTATION', 'Grab về khuya',                       TIME '23:10'),
        (13,          250000, 'EXPENSE', 'SHOPPING',       'Nước giặt và giấy vệ sinh',           TIME '20:00'),
        (14,           25000, 'EXPENSE', 'FOOD',           'Cà phê Ông Bầu',                      TIME '08:20'),
        (15,           45000, 'EXPENSE', 'FOOD',           'Cơm văn phòng suất 45k',              TIME '12:10'),
        (15,          220000, 'EXPENSE', 'ENTERTAINMENT',  'Gói Netflix hộ gia đình',             TIME '21:00'),
        (16,          150000, 'EXPENSE', 'ENTERTAINMENT',  'Vé xem phim CGV',                     TIME '20:30'),
        (17,          320000, 'EXPENSE', 'FOOD',           'Lẩu Thái với bạn cấp 3',              TIME '19:20'),
        (18,           59000, 'EXPENSE', 'ENTERTAINMENT',  'Spotify Premium',                     TIME '07:50'),
        (18,           75000, 'EXPENSE', 'TRANSPORTATION', 'Đổ xăng xe máy',                      TIME '08:15'),
        (18,          150000, 'EXPENSE', 'EDUCATION',      'Mua sách kỹ năng ở Fahasa',           TIME '16:40'),
        (19,           65000, 'EXPENSE', 'FOOD',           'Phở Hòa Pasteur',                     TIME '07:40'),
        (20,          500000, 'INCOME',  'GIFT',           'Tiền mừng sinh nhật',                 TIME '12:00'),
        (20,          385000, 'EXPENSE', 'FOOD',           'Siêu thị Bách Hóa Xanh',              TIME '18:30'),
        (21,          100000, 'EXPENSE', 'HEALTH',         'Mua thuốc cảm ở Long Châu',           TIME '19:15'),
        (22,           40000, 'EXPENSE', 'FOOD',           'Bánh mì chảo buổi sáng',              TIME '07:25'),
        (23,           90000, 'EXPENSE', 'TRANSPORTATION', 'Grab đi họp khách hàng',              TIME '14:05'),
        (24,           55000, 'EXPENSE', 'FOOD',           'Cơm gà xối mỡ Hồng Phát',             TIME '12:30'),
        (24,          300000, 'EXPENSE', 'CLOTHING',       'Mua 2 áo thun Uniqlo',                TIME '16:20'),
        (25,          250000, 'EXPENSE', 'SHOPPING',       'Sạc dự phòng Anker',                  TIME '20:45'),
        (26,           65000, 'EXPENSE', 'FOOD',           'Trà sữa Gong Cha',                    TIME '15:30'),
        (27,          450000, 'EXPENSE', 'FOOD',           'Ăn tối với gia đình',                 TIME '19:00'),
        (28,           80000, 'EXPENSE', 'TRANSPORTATION', 'Đổ xăng xe máy',                      TIME '08:00'),
        (29,          250000, 'EXPENSE', 'FOOD',           'Đi chợ cuối tháng',                   TIME '17:50'),
        (30,          300000, 'EXPENSE', 'OTHER',          'Tiền mừng cưới bạn',                  TIME '11:00')
      ) AS t(d, amount, type, category, description, tod);

    -- ========================================================================
    -- THÁNG M-2 — 46 giao dịch. Thu 17.000.000 / Chi 11.294.000
    -- FOOD 3.100.000 · TRANSPORTATION 685.000 · UTILITIES 3.530.000
    -- ENTERTAINMENT 699.000 · HEALTH 700.000 · SHOPPING 630.000
    -- CLOTHING 350.000 · EDUCATION 400.000 · OTHER 1.200.000
    -- ========================================================================
    INSERT INTO transactions
        (id, user_id, amount, type, category, description, date, project_id, created_at, updated_at)
    SELECT gen_random_uuid(),
           v_user_id,
           t.amount,
           t.type,
           t.category,
           t.description,
           (v_m2 + make_interval(days => LEAST(t.d, v_len2) - 1) + t.tod)::timestamp,
           NULL,
           (v_m2 + make_interval(days => LEAST(t.d, v_len2) - 1) + t.tod)::timestamp,
           (v_m2 + make_interval(days => LEAST(t.d, v_len2) - 1) + t.tod)::timestamp
      FROM (VALUES
        ( 2::int,      30000::numeric, 'EXPENSE'::varchar, 'FOOD'::varchar,           'Xôi mặn buổi sáng'::varchar,           TIME '07:10'),
        ( 3,          120000, 'EXPENSE', 'TRANSPORTATION', 'Vé gửi xe máy tháng',                 TIME '08:15'),
        ( 3,           50000, 'EXPENSE', 'FOOD',           'Cơm trưa căng tin công ty',           TIME '12:00'),
        ( 4,           65000, 'EXPENSE', 'FOOD',           'Cà phê The Coffee House',             TIME '09:20'),
        ( 4,           85000, 'EXPENSE', 'TRANSPORTATION', 'Đổ xăng xe máy',                      TIME '18:30'),
        ( 5,        15000000, 'INCOME',  'SALARY',         'Lương tháng',                         TIME '09:00'),
        ( 5,         2800000, 'EXPENSE', 'UTILITIES',      'Tiền thuê phòng trọ',                 TIME '19:10'),
        ( 5,          400000, 'EXPENSE', 'EDUCATION',      'Khóa học online Udemy',               TIME '21:30'),
        ( 6,          220000, 'EXPENSE', 'FOOD',           'Đi chợ mua thịt cá',                  TIME '08:40'),
        ( 6,          350000, 'EXPENSE', 'CLOTHING',       'Mua quần jean',                       TIME '16:40'),
        ( 6,          400000, 'EXPENSE', 'HEALTH',         'Gói tập gym California',              TIME '18:25'),
        ( 6,         1000000, 'EXPENSE', 'OTHER',          'Gửi tiền phụ giúp gia đình',          TIME '20:05'),
        ( 7,           65000, 'EXPENSE', 'FOOD',           'Trà sữa Koi Thé',                     TIME '15:20'),
        ( 8,           55000, 'EXPENSE', 'FOOD',           'Hủ tiếu Nam Vang',                    TIME '12:10'),
        ( 8,          420000, 'EXPENSE', 'UTILITIES',      'Tiền điện nước phòng trọ',            TIME '19:35'),
        ( 9,          180000, 'EXPENSE', 'TRANSPORTATION', 'Grab đi sân bay Tân Sơn Nhất',        TIME '05:30'),
        (10,          220000, 'EXPENSE', 'UTILITIES',      'Cước Internet FPT',                   TIME '10:05'),
        (10,          380000, 'EXPENSE', 'FOOD',           'Nhậu với hội bạn cũ',                 TIME '20:00'),
        (11,           75000, 'EXPENSE', 'TRANSPORTATION', 'Đổ xăng xe máy',                      TIME '08:10'),
        (11,          345000, 'EXPENSE', 'FOOD',           'Siêu thị Co.opmart cuối tuần',        TIME '17:30'),
        (12,         2000000, 'INCOME',  'BONUS',          'Thưởng dự án',                        TIME '11:00'),
        (12,           90000, 'EXPENSE', 'UTILITIES',      'Cước điện thoại Viettel',             TIME '10:20'),
        (12,          300000, 'EXPENSE', 'HEALTH',         'Khám răng lấy cao răng',              TIME '15:00'),
        (13,           50000, 'EXPENSE', 'FOOD',           'Bún riêu cua',                        TIME '12:25'),
        (14,           55000, 'EXPENSE', 'FOOD',           'Cà phê Trung Nguyên E-Coffee',        TIME '09:15'),
        (15,           50000, 'EXPENSE', 'FOOD',           'Cơm trưa suất 50k',                   TIME '12:05'),
        (15,          220000, 'EXPENSE', 'ENTERTAINMENT',  'Gói Netflix hộ gia đình',             TIME '21:05'),
        (16,           60000, 'EXPENSE', 'FOOD',           'Phở gà Hà Nội',                       TIME '07:35'),
        (17,          180000, 'EXPENSE', 'SHOPPING',       'Đồ dùng nhà tắm mua trên Shopee',     TIME '20:40'),
        (18,           59000, 'EXPENSE', 'ENTERTAINMENT',  'Spotify Premium',                     TIME '07:55'),
        (18,          340000, 'EXPENSE', 'FOOD',           'Buffet lẩu nướng Kichi Kichi',        TIME '19:30'),
        (19,           80000, 'EXPENSE', 'TRANSPORTATION', 'Đổ xăng xe máy',                      TIME '08:05'),
        (19,          220000, 'EXPENSE', 'ENTERTAINMENT',  'Vé xem phim CGV cùng bạn',            TIME '20:20'),
        (20,          260000, 'EXPENSE', 'FOOD',           'Đi chợ Bà Chiểu',                     TIME '08:30'),
        (21,           45000, 'EXPENSE', 'FOOD',           'Bánh cuốn nóng',                      TIME '07:20'),
        (21,           75000, 'EXPENSE', 'TRANSPORTATION', 'Grab về khuya sau tiệc',              TIME '23:30'),
        (22,           70000, 'EXPENSE', 'FOOD',           'Trà sữa Tocotoco',                    TIME '15:40'),
        (23,          450000, 'EXPENSE', 'SHOPPING',       'Tai nghe Bluetooth',                  TIME '19:50'),
        (24,           60000, 'EXPENSE', 'FOOD',           'Cơm niêu buổi tối',                   TIME '19:10'),
        (25,          250000, 'EXPENSE', 'FOOD',           'Góp tiền sinh nhật đồng nghiệp',      TIME '18:00'),
        (26,           90000, 'EXPENSE', 'FOOD',           'Ăn vặt bánh tráng trộn',              TIME '21:15'),
        (27,           70000, 'EXPENSE', 'TRANSPORTATION', 'Đổ xăng xe máy',                      TIME '08:20'),
        (27,          200000, 'EXPENSE', 'ENTERTAINMENT',  'Karaoke sinh nhật bạn',               TIME '21:40'),
        (28,          280000, 'EXPENSE', 'FOOD',           'Ăn tối quán nướng',                   TIME '19:25'),
        (29,          200000, 'EXPENSE', 'OTHER',          'Quyên góp từ thiện',                  TIME '10:10'),
        (30,          280000, 'EXPENSE', 'FOOD',           'Đi chợ cuối tháng nấu ăn',            TIME '17:40')
      ) AS t(d, amount, type, category, description, tod);

    -- ========================================================================
    -- THÁNG M-1 — 49 giao dịch. Thu 18.000.000 / Chi 12.514.000
    -- FOOD 3.700.000 · TRANSPORTATION 775.000 · UTILITIES 3.560.000
    -- ENTERTAINMENT 729.000 · HEALTH 930.000 · SHOPPING 770.000
    -- CLOTHING 350.000 · EDUCATION 250.000 · OTHER 1.450.000
    -- ========================================================================
    INSERT INTO transactions
        (id, user_id, amount, type, category, description, date, project_id, created_at, updated_at)
    SELECT gen_random_uuid(),
           v_user_id,
           t.amount,
           t.type,
           t.category,
           t.description,
           (v_m1 + make_interval(days => LEAST(t.d, v_len1) - 1) + t.tod)::timestamp,
           NULL,
           (v_m1 + make_interval(days => LEAST(t.d, v_len1) - 1) + t.tod)::timestamp,
           (v_m1 + make_interval(days => LEAST(t.d, v_len1) - 1) + t.tod)::timestamp
      FROM (VALUES
        ( 2::int,      30000::numeric, 'EXPENSE'::varchar, 'FOOD'::varchar,           'Bánh mì ốp la buổi sáng'::varchar,     TIME '07:05'),
        ( 3,          120000, 'EXPENSE', 'TRANSPORTATION', 'Vé gửi xe máy tháng',                 TIME '08:20'),
        ( 3,           55000, 'EXPENSE', 'FOOD',           'Cơm trưa văn phòng',                  TIME '12:00'),
        ( 4,           70000, 'EXPENSE', 'FOOD',           'Cà phê Katinat',                      TIME '09:25'),
        ( 4,           90000, 'EXPENSE', 'TRANSPORTATION', 'Đổ xăng xe máy',                      TIME '18:35'),
        ( 5,        15000000, 'INCOME',  'SALARY',         'Lương tháng',                         TIME '09:00'),
        ( 5,          250000, 'EXPENSE', 'FOOD',           'Đi chợ đầu tháng',                    TIME '17:35'),
        ( 5,         2800000, 'EXPENSE', 'UTILITIES',      'Tiền thuê phòng trọ',                 TIME '19:05'),
        ( 6,           70000, 'EXPENSE', 'FOOD',           'Trà sữa Mixue',                       TIME '15:05'),
        ( 6,          350000, 'EXPENSE', 'CLOTHING',       'Mua áo khoác gió',                    TIME '16:15'),
        ( 6,          400000, 'EXPENSE', 'HEALTH',         'Gói tập gym California',              TIME '18:30'),
        ( 6,         1000000, 'EXPENSE', 'OTHER',          'Gửi tiền phụ giúp gia đình',          TIME '20:10'),
        ( 7,           65000, 'EXPENSE', 'FOOD',           'Bún chả Hà Nội',                      TIME '12:15'),
        ( 8,           60000, 'EXPENSE', 'TRANSPORTATION', 'Grab đi khám bệnh',                   TIME '08:00'),
        ( 8,          350000, 'EXPENSE', 'HEALTH',         'Khám bệnh phòng khám đa khoa',        TIME '08:45'),
        ( 8,          450000, 'EXPENSE', 'UTILITIES',      'Tiền điện nước phòng trọ',            TIME '19:40'),
        ( 9,          180000, 'EXPENSE', 'HEALTH',         'Mua thuốc theo toa',                  TIME '09:30'),
        ( 9,          450000, 'EXPENSE', 'FOOD',           'Nhậu tất niên nhóm bạn',              TIME '20:10'),
        (10,          220000, 'EXPENSE', 'UTILITIES',      'Cước Internet FPT',                   TIME '10:10'),
        (10,          250000, 'EXPENSE', 'EDUCATION',      'Mua sách chuyên ngành',               TIME '16:50'),
        (10,          380000, 'EXPENSE', 'FOOD',           'Siêu thị Emart mua đồ khô',           TIME '17:20'),
        (11,           60000, 'EXPENSE', 'FOOD',           'Cơm sườn nướng buổi trưa',            TIME '12:05'),
        (12,           60000, 'EXPENSE', 'FOOD',           'Cà phê muối vỉa hè',                  TIME '09:10'),
        (12,           80000, 'EXPENSE', 'TRANSPORTATION', 'Đổ xăng xe máy',                      TIME '08:10'),
        (12,           90000, 'EXPENSE', 'UTILITIES',      'Cước điện thoại Viettel',             TIME '10:25'),
        (13,           60000, 'EXPENSE', 'FOOD',           'Bún bò giò heo',                      TIME '12:20'),
        (13,          420000, 'EXPENSE', 'SHOPPING',       'Đồ gia dụng Điện Máy Xanh',           TIME '19:45'),
        (14,          145000, 'EXPENSE', 'FOOD',           'GrabFood buổi tối',                   TIME '19:15'),
        (14,          450000, 'EXPENSE', 'ENTERTAINMENT',  'Vé concert nhạc Việt',                TIME '20:00'),
        (15,          220000, 'EXPENSE', 'ENTERTAINMENT',  'Gói Netflix hộ gia đình',             TIME '21:10'),
        (16,           55000, 'EXPENSE', 'FOOD',           'Cơm trưa gần chỗ làm',                TIME '12:10'),
        (17,          390000, 'EXPENSE', 'FOOD',           'Buffet nướng Hàn Quốc',               TIME '19:35'),
        (18,           59000, 'EXPENSE', 'ENTERTAINMENT',  'Spotify Premium',                     TIME '07:45'),
        (18,          120000, 'EXPENSE', 'FOOD',           'ShopeeFood buổi trưa',                TIME '12:30'),
        (18,          450000, 'EXPENSE', 'OTHER',          'Thay pin laptop',                     TIME '14:20'),
        (19,          290000, 'EXPENSE', 'FOOD',           'Đi chợ cuối tuần mua đồ ăn',          TIME '08:35'),
        (20,           85000, 'EXPENSE', 'TRANSPORTATION', 'Grab đi đám cưới',                    TIME '10:40'),
        (21,           35000, 'EXPENSE', 'FOOD',           'Bánh giò buổi sáng',                  TIME '07:15'),
        (21,           75000, 'EXPENSE', 'TRANSPORTATION', 'Đổ xăng xe máy',                      TIME '08:25'),
        (22,           75000, 'EXPENSE', 'FOOD',           'Trà sữa Phê La',                      TIME '15:35'),
        (23,          160000, 'EXPENSE', 'FOOD',           'GrabFood đặt tối muộn',               TIME '22:10'),
        (24,          180000, 'EXPENSE', 'TRANSPORTATION', 'Bảo dưỡng xe máy thay nhớt',          TIME '09:50'),
        (25,         3000000, 'INCOME',  'BONUS',          'Thưởng KPI quý',                      TIME '11:00'),
        (25,           65000, 'EXPENSE', 'FOOD',           'Cơm gà Hải Nam',                      TIME '12:25'),
        (26,          420000, 'EXPENSE', 'FOOD',           'Ăn tối nhà hàng Nhật',                TIME '19:50'),
        (27,           95000, 'EXPENSE', 'FOOD',           'Ăn vặt xiên que',                     TIME '21:20'),
        (28,           85000, 'EXPENSE', 'TRANSPORTATION', 'Đổ xăng xe máy',                      TIME '08:15'),
        (28,          350000, 'EXPENSE', 'SHOPPING',       'Balo đựng laptop',                    TIME '20:30'),
        (29,          300000, 'EXPENSE', 'FOOD',           'Đi chợ cuối tháng',                   TIME '17:45')
      ) AS t(d, amount, type, category, description, tod);

    SELECT COUNT(*) INTO v_inserted
      FROM transactions
     WHERE user_id = v_user_id
       AND date >= v_m3::timestamp
       AND date <  v_m0::timestamp;
    RAISE NOTICE 'Đã seed % giao dịch (kỳ vọng 138).', v_inserted;
END $$;

COMMIT;

-- ============================================================================
-- KIỂM TRA SAU KHI CHẠY — thay user id giống ở trên rồi chạy để đối chiếu số.
-- ============================================================================
-- \set demo_user '00000000-0000-0000-0000-000000000000'
--
-- -- 1) Thu / chi từng tháng
-- SELECT to_char(date, 'YYYY-MM') AS thang,
--        SUM(amount) FILTER (WHERE type = 'INCOME')  AS thu,
--        SUM(amount) FILTER (WHERE type = 'EXPENSE') AS chi,
--        COUNT(*)                                    AS so_giao_dich
--   FROM transactions
--  WHERE user_id = '00000000-0000-0000-0000-000000000000'::uuid
--    AND date >= (date_trunc('month', CURRENT_DATE) - INTERVAL '3 months')
--    AND date <   date_trunc('month', CURRENT_DATE)
--  GROUP BY 1 ORDER BY 1;
--
-- -- 2) Chi theo danh mục từng tháng (xác nhận FOOD leo thang → CATEGORY_TREND)
-- SELECT category,
--        SUM(amount) FILTER (WHERE date < date_trunc('month', CURRENT_DATE) - INTERVAL '2 months') AS m_3,
--        SUM(amount) FILTER (WHERE date >= date_trunc('month', CURRENT_DATE) - INTERVAL '2 months'
--                              AND date <  date_trunc('month', CURRENT_DATE) - INTERVAL '1 month') AS m_2,
--        SUM(amount) FILTER (WHERE date >= date_trunc('month', CURRENT_DATE) - INTERVAL '1 month') AS m_1
--   FROM transactions
--  WHERE user_id = '00000000-0000-0000-0000-000000000000'::uuid
--    AND type = 'EXPENSE'
--    AND date >= (date_trunc('month', CURRENT_DATE) - INTERVAL '3 months')
--    AND date <   date_trunc('month', CURRENT_DATE)
--  GROUP BY 1 ORDER BY 1;
--
-- -- 3) Các khoản đủ 3 tháng → ứng viên RECURRING (kỳ vọng đúng 10 dòng)
-- SELECT category, lower(trim(description)) AS mo_ta,
--        COUNT(DISTINCT date_trunc('month', date)) AS so_thang,
--        round(avg(thang_tong)) AS trung_binh_thang
--   FROM (SELECT category, description, date_trunc('month', date) AS thang,
--                SUM(amount) AS thang_tong, MIN(date) AS date
--           FROM transactions
--          WHERE user_id = '00000000-0000-0000-0000-000000000000'::uuid
--            AND type = 'EXPENSE'
--            AND date >= (date_trunc('month', CURRENT_DATE) - INTERVAL '3 months')
--            AND date <   date_trunc('month', CURRENT_DATE)
--          GROUP BY 1, 2, 3) g
--  GROUP BY 1, 2 HAVING COUNT(DISTINCT thang) = 3
--  ORDER BY 4 DESC;
--
-- ============================================================================
-- MUỐN BẬT THÊM BUDGET_PACE / SUBSCRIPTION_PRICE_HIKE?
-- Cần thêm một khối seed cho THÁNG HIỆN TẠI (đến ngày hôm nay), trong đó:
--   • FOOD chạy nhanh hơn hạn mức → BUDGET_PACE (nhớ tạo budget cho tháng này)
--   • 'Gói Netflix hộ gia đình' ghi 260.000 thay vì 220.000 → +18% so với trung
--     vị 3 tháng nền (ngưỡng vỡ băng dung sai là +15%) → SUBSCRIPTION_PRICE_HIKE
-- Cố ý KHÔNG đưa vào file này vì yêu cầu là seed đúng 3 tháng trước tháng hiện tại.
-- ============================================================================
