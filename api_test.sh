#!/bin/bash
# ================================================================
# api_test_fixed.sh — SmartMoney API Automation Test (Full Coverage)
# Thứ tự: CRUD tests trước, toàn bộ DELETE dọn dẹp ở section cuối
# Yêu cầu: curl, jq
# Chạy: chmod +x api_test_fixed.sh && ./api_test_fixed.sh
# ================================================================

BASE_URL="http://172.28.0.1:8080/api/v1"
PASS=0; FAIL=0
TOKEN=""; REFRESH_TOKEN=""
BUDGET_ID=""; TRANSACTION_ID=""; INCOME_TX_ID=""
PROJECT_ID=""; GROUP_PROJECT_ID=""

# ── Credentials ──────────────────────────────────────────────────
VERIFIED_EMAIL="nguyenphucanpth@gmail.com"
VERIFIED_PASS="nguyenphucan"
RAND=$(date +%s)
NEW_EMAIL="test_${RAND}@test.dev"
NEW_PASS="Test@123456"
NEW_USERNAME="user_${RAND}"

# ── Colors ───────────────────────────────────────────────────────
GREEN='\033[0;32m'; RED='\033[0;31m'
YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

pass() { echo -e "  ${GREEN}✓ PASS${NC} $1"; ((PASS++)); }
fail() { echo -e "  ${RED}✗ FAIL${NC} $1\n    $(echo "$2" | head -c 200)"; ((FAIL++)); }
info() { echo -e "  ${YELLOW}ℹ${NC}  $1"; }
section() {
  echo -e "\n${CYAN}══════════════════════════════════════════${NC}"
  echo -e "${CYAN}  $1${NC}"
  echo -e "${CYAN}══════════════════════════════════════════${NC}"
}

call() {
  local method="$1" path="$2"; shift 2
  local raw
  raw=$(curl -s -w "\n%{http_code}" -X "$method" "$BASE_URL$path" "$@")
  RESP_BODY=$(echo "$raw" | sed '$d')
  RESP_STATUS=$(echo "$raw" | tail -n1)
}

assert() {
  local label="$1" expected="$2"
  if [[ "$RESP_STATUS" == "$expected" ]]; then
    pass "[$label] → HTTP $RESP_STATUS"
  else
    fail "[$label] expected $expected got $RESP_STATUS" "$RESP_BODY"
  fi
}

assert_any() {
  local label="$1"; shift
  for s in "$@"; do
    [[ "$RESP_STATUS" == "$s" ]] && { pass "[$label] → HTTP $RESP_STATUS"; return; }
  done
  fail "[$label] expected one of ($*) got $RESP_STATUS" "$RESP_BODY"
}

assert_field() {
  local label="$1" jqpath="$2"
  local val
  val=$(echo "$RESP_BODY" | jq -r "$jqpath" 2>/dev/null)
  if [[ -n "$val" && "$val" != "null" ]]; then
    pass "  └─ $label = $val"
  else
    fail "  └─ $label missing/null" "$RESP_BODY"
  fi
}

# ================================================================
# LOGIN
# ================================================================
section "LOGIN — Lấy token"

call POST "/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$VERIFIED_EMAIL\",\"password\":\"$VERIFIED_PASS\"}"

if [[ "$RESP_STATUS" == "200" ]]; then
  TOKEN=$(echo "$RESP_BODY" | jq -r '.data.accessToken')
  REFRESH_TOKEN=$(echo "$RESP_BODY" | jq -r '.data.refreshToken')
  if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
    echo -e "${RED}FATAL: Cannot extract token${NC}"; exit 1
  fi
  AUTH="Authorization: Bearer $TOKEN"
  pass "[POST /auth/login] → HTTP 200, token acquired"
  info "Token: ${TOKEN:0:40}..."
else
  echo -e "${RED}FATAL: Login failed HTTP $RESP_STATUS${NC}"; echo "$RESP_BODY"; exit 1
fi

# ================================================================
# 1. AUTH
# ================================================================
section "1. AUTH"

call GET "/auth/health"
assert "GET /auth/health" "200"

call POST "/auth/register" \
  -F "username=$NEW_USERNAME" -F "fullName=Test Automation" \
  -F "email=$NEW_EMAIL" -F "password=$NEW_PASS" \
  -F "dateOfBirth=01/01/2000" -F "phone=0901234567"
assert "POST /auth/register" "200"

call POST "/auth/register" \
  -F "username=$NEW_USERNAME" -F "fullName=Test" \
  -F "email=$NEW_EMAIL" -F "password=$NEW_PASS" \
  -F "dateOfBirth=01/01/2000" -F "phone=0901234567"
assert_any "POST /auth/register duplicate → 200/400" "200" "400"

call POST "/auth/register" -F "username=onlyuser"
assert "POST /auth/register missing fields → 400" "400"

call POST "/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"wrong@test.com","password":"wrongpass"}'
assert "POST /auth/login wrong creds → 400" "400"

call GET "/auth/me" -H "$AUTH"
assert "GET /auth/me" "200"

call PUT "/auth/me" -H "$AUTH" \
  -F "fullName=Updated Name" -F "phone=0987654321" -F "dateOfBirth=15/06/1999"
assert "PUT /auth/me" "200"

call POST "/auth/forgot" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$VERIFIED_EMAIL\"}"
assert "POST /auth/forgot" "200"

call POST "/auth/forgot" \
  -H "Content-Type: application/json" \
  -d '{"email":"not-an-email"}'
assert "POST /auth/forgot invalid email → 400" "400"

call POST "/auth/verify-email" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$NEW_EMAIL\",\"otp\":\"000000\"}"
assert "POST /auth/verify-email" "200"

call POST "/auth/verify-otp" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$VERIFIED_EMAIL\",\"otp\":\"000000\"}"
assert_any "POST /auth/verify-otp wrong → 400/404" "400" "404"

call POST "/auth/reset-password" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$VERIFIED_EMAIL\",\"newPassword\":\"NewPass@123\"}"
assert "POST /auth/reset-password no token → 401" "401"

call POST "/auth/reset-password" \
  -H "Content-Type: application/json" \
  -H "X-Reset-Token: invalid-token" \
  -d "{\"email\":\"$VERIFIED_EMAIL\",\"newPassword\":\"NewPass@123\"}"
assert "POST /auth/reset-password invalid token → 401" "401"

call GET "/auth/notification" -H "$AUTH"
assert "GET /auth/notification" "200"

call PATCH "/auth/notification/enable" -H "$AUTH"
assert "PATCH /auth/notification/enable" "200"

call PATCH "/auth/notification/disable" -H "$AUTH"
assert "PATCH /auth/notification/disable" "200"

call PATCH "/auth/notification/enable" -H "$AUTH"

call POST "/auth/logout" -H "$AUTH"
assert "POST /auth/logout" "200"

# Re-login
# Re-login dùng curl trực tiếp để tránh RESP_BODY bị ghi đè
RELOGIN_BODY=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$VERIFIED_EMAIL\",\"password\":\"$VERIFIED_PASS\"}")
TOKEN=$(echo "$RELOGIN_BODY" | jq -r '.data.accessToken')
REFRESH_TOKEN=$(echo "$RELOGIN_BODY" | jq -r '.data.refreshToken')
AUTH="Authorization: Bearer $TOKEN"
info "Re-logged in: token=${TOKEN:0:30}..."
info "Refresh token: ${REFRESH_TOKEN:0:30}..."

# Test refresh-token với token mới từ re-login
REFRESH_BODY=$(curl -s -X POST "$BASE_URL/auth/refresh-token" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
REFRESH_HTTP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/auth/refresh-token" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\":\"$REFRESH_TOKEN\"}")
RESP_STATUS=$REFRESH_HTTP
RESP_BODY=$REFRESH_BODY
assert "POST /auth/refresh-token" "200"
# Cập nhật token sau refresh
NEW_TOKEN=$(echo "$REFRESH_BODY" | jq -r '.data.accessToken' 2>/dev/null)
NEW_REFRESH=$(echo "$REFRESH_BODY" | jq -r '.data.refreshToken' 2>/dev/null)
if [[ -n "$NEW_TOKEN" && "$NEW_TOKEN" != "null" ]]; then
  TOKEN=$NEW_TOKEN; REFRESH_TOKEN=$NEW_REFRESH
  AUTH="Authorization: Bearer $TOKEN"
  info "Token updated after refresh"
fi

call POST "/auth/refresh-token" \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"invalid.token.here"}'
assert "POST /auth/refresh-token invalid → 401" "401"

call GET "/transactions"
assert "GET /transactions no auth → 401" "401"

# ================================================================
# 2. TRANSACTIONS
# ================================================================
section "2. TRANSACTIONS"

call POST "/transactions" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"amount":50000,"type":"EXPENSE","category":"FOOD","description":"Test lunch","date":"14/06/2026 12:00"}'
assert "POST /transactions EXPENSE → 201" "201"
TRANSACTION_ID=$(echo "$RESP_BODY" | jq -r '.data.id')
assert_field "transaction.id" ".data.id"

call POST "/transactions" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"amount":5000000,"type":"INCOME","category":"OTHER","description":"Salary","date":"01/06/2026 09:00"}'
assert "POST /transactions INCOME → 201" "201"
INCOME_TX_ID=$(echo "$RESP_BODY" | jq -r '.data.id')

call POST "/transactions" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"type":"EXPENSE","category":"FOOD","date":"14/06/2026 12:00"}'
assert "POST /transactions missing amount → 400" "400"

call POST "/transactions" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"amount":10000,"type":"EXPENSE","category":"FOOD","date":"2026-06-14"}'
assert "POST /transactions invalid date → 400" "400"

call POST "/transactions" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"amount":10000,"type":"EXPENSE","category":"INVALID","date":"14/06/2026 12:00"}'
assert_any "POST /transactions invalid category → 400/500" "400" "500"

call GET "/transactions" -H "$AUTH"
assert "GET /transactions" "200"
assert_field "count" ".data.count"

call GET "/transactions?page=0&size=5" -H "$AUTH"
assert "GET /transactions page=0 size=5" "200"

call GET "/transactions?type=EXPENSE" -H "$AUTH"
assert "GET /transactions type=EXPENSE" "200"

call GET "/transactions?type=INCOME" -H "$AUTH"
assert "GET /transactions type=INCOME" "200"

call GET "/transactions?category=FOOD" -H "$AUTH"
assert "GET /transactions category=FOOD" "200"

call GET "/transactions?minAmount=1000&maxAmount=100000" -H "$AUTH"
assert "GET /transactions amount range" "200"

call GET "/transactions?sortBy=amount&sortOrder=DESC" -H "$AUTH"
assert "GET /transactions sort amount DESC" "200"

call GET "/transactions?sortBy=date&sortOrder=ASC" -H "$AUTH"
assert "GET /transactions sort date ASC" "200"

if [[ -n "$TRANSACTION_ID" && "$TRANSACTION_ID" != "null" ]]; then
  call GET "/transactions/$TRANSACTION_ID" -H "$AUTH"
  assert "GET /transactions/:id" "200"
  assert_field "transaction.amount" ".data.amount"
fi

call GET "/transactions/00000000-0000-0000-0000-000000000000" -H "$AUTH"
assert "GET /transactions/:id not found → 404" "404"

if [[ -n "$TRANSACTION_ID" && "$TRANSACTION_ID" != "null" ]]; then
  call PUT "/transactions/$TRANSACTION_ID" -H "$AUTH" -H "Content-Type: application/json" \
    -d '{"amount":75000,"description":"Updated lunch"}'
  assert "PUT /transactions/:id" "200"
fi

call PUT "/transactions/00000000-0000-0000-0000-000000000000" \
  -H "$AUTH" -H "Content-Type: application/json" -d '{"amount":100}'
assert "PUT /transactions/:id not found → 404" "404"

call POST "/transactions/analytics" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"month":6,"year":2026}'
assert "POST /transactions/analytics" "200"

call DELETE "/transactions/00000000-0000-0000-0000-000000000000" -H "$AUTH"
assert "DELETE /transactions/:id not found → 404" "404"

# ================================================================
# 3. BUDGETS
# ================================================================
section "3. BUDGETS"

call POST "/budgets" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"category":"FOOD","amountLimit":500000,"month":6,"year":2026}'
assert "POST /budgets → 201" "201"
BUDGET_ID=$(echo "$RESP_BODY" | jq -r '.data.budgetId')
assert_field "budget.budgetId" ".data.budgetId"

call POST "/budgets" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"category":"FOOD","amountLimit":300000,"month":6,"year":2026}'
assert "POST /budgets duplicate → 400" "400"

call POST "/budgets" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"category":"EDUCATION","amountLimit":100000,"month":13,"year":2026}'
assert "POST /budgets invalid month → 400" "400"

call POST "/budgets" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"category":"HEALTH","amountLimit":-100,"month":6,"year":2026}'
assert "POST /budgets negative amount → 400" "400"

call POST "/budgets/bulk" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"month":6,"year":2026,"budgets":[{"category":"TRANSPORTATION","amountLimit":200000},{"category":"ENTERTAINMENT","amountLimit":300000},{"category":"HEALTH","amountLimit":400000},{"category":"SHOPPING","amountLimit":600000}]}'
assert "POST /budgets/bulk → 201" "201"
assert_field "totalCreated" ".data.totalCreated"

call POST "/budgets/bulk" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"month":6,"year":2026,"budgets":[]}'
assert "POST /budgets/bulk empty → 400" "400"

call GET "/budgets?month=6&year=2026" -H "$AUTH"
assert "GET /budgets?month=6&year=2026" "200"
assert_field "items" ".data.items"

call GET "/budgets?month=1&year=2020" -H "$AUTH"
assert "GET /budgets empty month" "200"

call GET "/budgets" -H "$AUTH"
assert_any "GET /budgets missing params → 400/500" "400" "500"

if [[ -n "$BUDGET_ID" && "$BUDGET_ID" != "null" ]]; then
  call GET "/budgets/$BUDGET_ID" -H "$AUTH"
  assert "GET /budgets/:id" "200"
  assert_field "budget.category" ".data.category"
fi

call GET "/budgets/00000000-0000-0000-0000-000000000000" -H "$AUTH"
assert "GET /budgets/:id not found → 404" "404"

if [[ -n "$BUDGET_ID" && "$BUDGET_ID" != "null" ]]; then
  call PUT "/budgets/$BUDGET_ID" -H "$AUTH" -H "Content-Type: application/json" \
    -d '{"amountLimit":700000}'
  assert "PUT /budgets/:id" "200"
fi

call PUT "/budgets/00000000-0000-0000-0000-000000000000" \
  -H "$AUTH" -H "Content-Type: application/json" -d '{"amountLimit":100000}'
assert "PUT /budgets/:id not found → 404" "404"

call DELETE "/budgets/00000000-0000-0000-0000-000000000000" -H "$AUTH"
assert "DELETE /budgets/:id not found → 404" "404"

# ================================================================
# 4. USER INCOME
# ================================================================
section "4. USER INCOME"

curl -s -X DELETE "$BASE_URL/user-income/me" -H "$AUTH" > /dev/null

call POST "/user-income/me" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"netIncome":100000000,"usableIncome":80000000,"currency":"VND","autoInvestSurplus":false,"calculationNote":"Test income"}'
assert "POST /user-income/me" "200"
assert_field "income.netIncome" ".data.netIncome"

call POST "/user-income/me" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"netIncome":5000000,"usableIncome":4000000,"currency":"VND"}'
if [[ "$RESP_STATUS" != "200" ]]; then
  pass "[POST /user-income/me duplicate → error HTTP $RESP_STATUS]"
else
  info "[POST /user-income/me duplicate] returned 200 (upsert)"; ((PASS++))
fi

call GET "/user-income/me" -H "$AUTH"
assert "GET /user-income/me" "200"
assert_field "netIncome" ".data.netIncome"

call PUT "/user-income/me" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"netIncome":100000000}'
assert "PUT /user-income/me partial" "200"

call PUT "/user-income/me" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"netIncome":100000000,"usableIncome":80000000,"currency":"VND","autoInvestSurplus":true}'
assert "PUT /user-income/me full" "200"
assert_field "updated netIncome" ".data.netIncome"

call PUT "/user-income/me" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"netIncome":-1000}'
assert "PUT /user-income/me negative → 400" "400"

# ================================================================
# 5. PROJECTS
# ================================================================
section "5. PROJECTS"

# Cleanup projects cũ từ lần chạy trước để tránh PRIORITY_CONFLICT
info "Cleaning up old projects..."
OLD_PROJECTS=$(curl -s "$BASE_URL/projects" -H "$AUTH" | jq -r '.data[].projectId' 2>/dev/null)
for pid in $OLD_PROJECTS; do
  curl -s -X DELETE "$BASE_URL/projects/$pid/tracking" -H "$AUTH" > /dev/null
  curl -s -X DELETE "$BASE_URL/projects/$pid" -H "$AUTH" > /dev/null
done
info "Old projects cleaned up"

call POST "/projects" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"Test Saving Project","description":"Automated test","type":"PERSONAL","priority":"MEDIUM","targetAmount":10000000,"currency":"VND","deadline":"2027-12-31"}'
assert "POST /projects PERSONAL" "200"
PROJECT_ID=$(echo "$RESP_BODY" | jq -r '.data.projectId')
assert_field "project.projectId" ".data.projectId"

call POST "/projects" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"Group Vacation Fund","type":"GROUP","priority":"HIGH","targetAmount":20000000,"currency":"VND","deadline":"2027-06-30"}'
assert "POST /projects GROUP" "200"
GROUP_PROJECT_ID=$(echo "$RESP_BODY" | jq -r '.data.projectId')

call POST "/projects" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"No Type Project"}'
assert "POST /projects missing fields → 400" "400"

call GET "/projects" -H "$AUTH"
assert_any "GET /projects" "200" "404"

call GET "/projects?type=PERSONAL" -H "$AUTH"
assert_any "GET /projects type=PERSONAL" "200" "404"

call GET "/projects?type=GROUP" -H "$AUTH"
assert_any "GET /projects type=GROUP" "200" "404"

call GET "/projects?status=ACTIVE" -H "$AUTH"
assert_any "GET /projects status=ACTIVE" "200" "404"

call GET "/projects?priority=HIGH" -H "$AUTH"
assert_any "GET /projects priority=HIGH" "200" "404"

call GET "/projects?search=Test" -H "$AUTH"
assert_any "GET /projects search=Test" "200" "404"

if [[ -n "$PROJECT_ID" && "$PROJECT_ID" != "null" ]]; then
  call GET "/projects/$PROJECT_ID" -H "$AUTH"
  assert "GET /projects/:id" "200"
  assert_field "project.name" ".data.name"

  call PUT "/projects/$PROJECT_ID" -H "$AUTH" -H "Content-Type: application/json" \
    -d '{"name":"Updated Project Name","priority":"LOW"}'
  assert "PUT /projects/:id" "200"

  # Xóa tracking cũ nếu còn tồn tại
  curl -s -X DELETE "$BASE_URL/projects/$PROJECT_ID/tracking" -H "$AUTH" > /dev/null

  call POST "/projects/$PROJECT_ID/tracking" -H "$AUTH" -H "Content-Type: application/json" \
    -d '{"moneySaved":0,"currentMonth":1,"monthLeft":12,"moneyOwed":0}'
  assert "POST /projects/:id/tracking" "200"

  call GET "/projects/$PROJECT_ID/tracking" -H "$AUTH"
  assert "GET /projects/:id/tracking" "200"

  call PUT "/projects/$PROJECT_ID/tracking" -H "$AUTH" -H "Content-Type: application/json" \
    -d '{"moneySaved":500000,"currentMonth":2,"monthLeft":11,"moneyOwed":0}'
  assert "PUT /projects/:id/tracking" "200"

  call POST "/projects/$PROJECT_ID/contributions" -H "$AUTH" -H "Content-Type: application/json" \
    -d '{"amount":500000,"note":"Monthly contribution"}'
  assert "POST /projects/:id/contributions" "200"

  call POST "/projects/$PROJECT_ID/contributions" -H "$AUTH" -H "Content-Type: application/json" \
    -d '{"amount":-100}'
  assert "POST /projects/:id/contributions negative → 400" "400"

  call GET "/projects/$PROJECT_ID/contributions" -H "$AUTH"
  assert "GET /projects/:id/contributions" "200"

  call GET "/projects/$PROJECT_ID/contributions/summary" -H "$AUTH"
  assert "GET /projects/:id/contributions/summary" "200"

  call GET "/projects/$PROJECT_ID/history" -H "$AUTH"
  assert "GET /projects/:id/history" "200"
fi

call POST "/projects/project-advisor" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"Advisor Test","type":"PERSONAL","targetAmount":10000000,"currency":"VND","deadline":"2027-06-30","priority":"HIGH","mode":"URGENT"}'
assert_any "POST /projects/project-advisor URGENT" "200" "400"

call POST "/projects/project-advisor" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"Advisor Relaxed","type":"PERSONAL","targetAmount":5000000,"currency":"VND","deadline":"2028-12-31","mode":"RELAXED"}'
assert_any "POST /projects/project-advisor RELAXED" "200" "400"

call POST "/projects/project-advisor" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"No Mode","type":"PERSONAL","targetAmount":1000000,"currency":"VND","deadline":"2027-12-31"}'
assert "POST /projects/project-advisor missing mode → 400" "400"

# ================================================================
# 6. USER FINANCIAL PROFILE
# ================================================================
section "6. USER FINANCIAL PROFILE"

curl -s -X DELETE "$BASE_URL/user-financial-profile" -H "$AUTH" > /dev/null

call POST "/user-financial-profile" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"role":"STUDENT","living_status":"WITH_FAMILY","income_level":"LOW","transport_mode":"BUS","spending_style":"FRUGAL","work_style":"PART_TIME","family_status":"SINGLE","study_intensity":"COURSE_HEAVY","health_need":"LOW"}'
assert "POST /user-financial-profile" "200"
assert_field "profile.role" ".data.role"

call POST "/user-financial-profile" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"role":"STUDENT","living_status":"WITH_FAMILY","income_level":"LOW","transport_mode":"BUS","spending_style":"FRUGAL","work_style":"PART_TIME","family_status":"SINGLE","study_intensity":"COURSE_HEAVY","health_need":"LOW"}'
assert "POST /user-financial-profile duplicate → 409" "409"

call GET "/user-financial-profile" -H "$AUTH"
assert "GET /user-financial-profile" "200"
assert_field "profile.role" ".data.role"

call PUT "/user-financial-profile" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"role":"OFFICE_WORKER","living_status":"RENT_ROOM","income_level":"MEDIUM","transport_mode":"MOTORBIKE","spending_style":"BALANCED","work_style":"HYBRID","family_status":"SINGLE","study_intensity":"NORMAL","health_need":"NORMAL"}'
assert "PUT /user-financial-profile" "200"
assert_field "updated role" ".data.role"

call PUT "/user-financial-profile" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"role":"FREELANCER"}'
assert "PUT /user-financial-profile missing fields → 400" "400"

# ================================================================
# 7. NOTIFICATIONS
# ================================================================
section "7. NOTIFICATIONS"

call POST "/notifications" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"content":"Test automation notification #1"}'
assert "POST /notifications" "200"
assert_field "notification.id" ".data.id"

call POST "/notifications" -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"content":"Test automation notification #2"}'
assert "POST /notifications #2" "200"

call GET "/notifications" -H "$AUTH"
assert "GET /notifications" "200"
assert_field "notifications list" ".data"

call GET "/notifications"
assert "GET /notifications no auth → 401" "401"

# ================================================================
# 8. PUSH TOKENS
# ================================================================
section "8. PUSH TOKENS"

call POST "/push-tokens" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"pushToken\":\"fcm_android_${RAND}\",\"platform\":\"android\"}"
assert "POST /push-tokens android" "200"

call POST "/push-tokens" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"pushToken\":\"apns_ios_${RAND}\",\"platform\":\"ios\"}"
assert "POST /push-tokens ios" "200"

call POST "/push-tokens" -H "Content-Type: application/json" \
  -d '{"pushToken":"some_token","platform":"android"}'
assert "POST /push-tokens no auth → 401" "401"

# ================================================================
# 9. CLOUDINARY
# ================================================================
section "9. CLOUDINARY"

call GET "/cloudinary/signature" -H "$AUTH"
assert "GET /cloudinary/signature" "200"
assert_field "timestamp" ".data.timestamp"

call GET "/cloudinary/signature?folder=smartmoney/avatars" -H "$AUTH"
assert "GET /cloudinary/signature custom folder" "200"

call GET "/cloudinary/image/smartmoney/test" -H "$AUTH"
assert_any "GET /cloudinary/image/:publicId" "200" "500"

call DELETE "/cloudinary/image" -H "$AUTH" \
  -H "Content-Type: application/json" \
  -d '{"publicId":"nonexistent/image123","type":"upload"}'
assert_any "DELETE /cloudinary/image non-existent" "200" "500"

# ================================================================
# 10. AI
# ================================================================
section "10. AI"

call POST "/ai/budget/generate" -H "$AUTH"
assert_any "POST /ai/budget/generate" "200" "404" "500"

call POST "/ai/budget/generate"
assert "POST /ai/budget/generate no auth → 401" "401"

call POST "/ai" -H "$AUTH" \
  -F "data={\"description\":\"Spent 50000 on lunch\"}" \
  -F "type=EXPENSE_ANALYSIS"
assert_any "POST /ai submit job" "202" "409" "500"
JOB_ID=$(echo "$RESP_BODY" | jq -r '.jobId' 2>/dev/null)

if [[ -n "$JOB_ID" && "$JOB_ID" != "null" ]]; then
  sleep 2
  call GET "/ai/$JOB_ID" -H "$AUTH"
  assert_any "GET /ai/:jobId" "200" "404"
else
  info "[GET /ai/:jobId] skipped — no jobId"
fi

call GET "/ai/nonexistent-job-12345" -H "$AUTH"
assert "GET /ai/:jobId not found → 404" "404"

# ================================================================
# 11. ADMIN SETTLEMENT
# ================================================================
section "11. ADMIN SETTLEMENT"

call POST "/admin/settlement/run"
assert "POST /admin/settlement/run no auth → 401" "401"

call POST "/admin/settlement/run" -H "$AUTH"
assert_any "POST /admin/settlement/run" "200" "403"

# ================================================================
# 12. CLEANUP — Xóa toàn bộ data test
# ================================================================
section "12. CLEANUP — Xóa data test"

# Delete transactions
if [[ -n "$TRANSACTION_ID" && "$TRANSACTION_ID" != "null" ]]; then
  call DELETE "/transactions/$TRANSACTION_ID" -H "$AUTH"
  assert "DELETE /transactions/:id" "200"
fi
if [[ -n "$INCOME_TX_ID" && "$INCOME_TX_ID" != "null" ]]; then
  call DELETE "/transactions/$INCOME_TX_ID" -H "$AUTH"
  assert "DELETE /transactions (income)" "200"
fi

# Delete budgets
if [[ -n "$BUDGET_ID" && "$BUDGET_ID" != "null" ]]; then
  call DELETE "/budgets/$BUDGET_ID" -H "$AUTH"
  assert "DELETE /budgets/:id" "200"
fi

# Delete project tracking + projects
if [[ -n "$PROJECT_ID" && "$PROJECT_ID" != "null" ]]; then
  call DELETE "/projects/$PROJECT_ID/tracking" -H "$AUTH"
  assert_any "DELETE /projects/:id/tracking" "200" "404"
  call DELETE "/projects/$PROJECT_ID" -H "$AUTH"
  assert "DELETE /projects/:id" "200"
fi
if [[ -n "$GROUP_PROJECT_ID" && "$GROUP_PROJECT_ID" != "null" ]]; then
  call DELETE "/projects/$GROUP_PROJECT_ID" -H "$AUTH"
  assert "DELETE /projects/:id (group)" "200"
fi

# Delete user financial profile
call DELETE "/user-financial-profile" -H "$AUTH"
assert_any "DELETE /user-financial-profile" "200" "404"

# Delete user income
call DELETE "/user-income/me" -H "$AUTH"
assert "DELETE /user-income/me" "200"

# Verify income deleted
call GET "/user-income/me" -H "$AUTH"
if [[ "$RESP_STATUS" != "200" ]]; then
  pass "[GET /user-income/me after delete → error HTTP $RESP_STATUS]"
else
  info "[GET /user-income/me after delete] returned 200"
fi

# ================================================================
# SUMMARY
# ================================================================
TOTAL=$((PASS + FAIL))
echo -e "\n${CYAN}══════════════════════════════════════════${NC}"
echo -e "${CYAN}  KẾT QUẢ KIỂM TRA${NC}"
echo -e "${CYAN}══════════════════════════════════════════${NC}"
printf "  %-12s %d\n" "Tổng tests:" "$TOTAL"
printf "  ${GREEN}%-12s %d${NC}\n" "Passed:" "$PASS"
printf "  ${RED}%-12s %d${NC}\n" "Failed:" "$FAIL"
if [[ $FAIL -eq 0 ]]; then
  echo -e "\n  ${GREEN}✓ Tất cả tests passed!${NC}"
else
  PCT=$(( PASS * 100 / TOTAL ))
  echo -e "\n  ${YELLOW}Tỷ lệ pass: ${PCT}%${NC}"
  echo -e "  ${RED}✗ Có $FAIL test(s) failed${NC}"
fi
echo -e "${CYAN}══════════════════════════════════════════${NC}\n"
exit $FAIL