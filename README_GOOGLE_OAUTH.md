# 🎉 Google OAuth Integration Complete!

Hệ thống đã được tích hợp xác thực Google OAuth 2.0 hoàn toàn. Đây là hướng dẫn để bắt đầu sử dụng ngay.

---

## 📌 Tl;dr (Tóm tắt nhanh)

### Endpoint Mới
```
POST /api/v1/auth/login/google
```

### Request
```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIs..."
}
```

### Response
```json
{
  "success": true,
  "data": {
    "accessToken": "...",
    "refreshToken": "...",
    "user": { ... }
  }
}
```

---

## 🚀 Bắt Đầu Nhanh (5 Phút)

### 1️⃣ Cập Nhật Dependencies
```bash
mvn clean install
```

### 2️⃣ Lấy Google Client ID
- Truy cập: https://console.cloud.google.com
- Tạo OAuth 2.0 credentials
- Copy Client ID

### 3️⃣ Đặt Environment Variable
```bash
# Linux/Mac
export GOOGLE_CLIENT_ID="your-client-id"

# Windows PowerShell
[Environment]::SetEnvironmentVariable("GOOGLE_CLIENT_ID", "your-client-id")
```

### 4️⃣ Chạy Server
```bash
mvn spring-boot:run
```

### 5️⃣ Test API
Truy cập: `http://localhost:8080/swagger-ui.html`

---

## 📂 Files Được Tạo/Sửa

### ✅ Files Mới Tạo
```
src/main/java/.../dtos/requests/auth/
  └─ GoogleLoginRequest.java

src/main/java/.../usecases/auth/
  └─ GoogleLoginUseCase.java

src/test/java/.../controllers/
  └─ AuthControllerGoogleLoginTest.java

Documentation Files:
  ├─ GOOGLE_OAUTH_SETUP.md (Hướng dẫn chi tiết)
  ├─ GOOGLE_LOGIN_QUICKSTART.md (Bắt đầu nhanh)
  ├─ GOOGLE_OAUTH_API_SPEC.md (Chi tiết API)
  └─ IMPLEMENTATION_SUMMARY.md (Tóm tắt thực hiện)
```

### ✏️ Files Được Sửa
```
AuthController.java
  ✅ Added googleLoginUseCase field
  ✅ Added POST /api/v1/auth/login/google endpoint

User.java (Domain Model)
  ✅ Added createGoogleUser() factory method
  ✅ Added activate() method
  ✅ Added deactivate() method

application.yml
  ✅ Added google.oauth2.client-id configuration

pom.xml
  ✅ Added Google OAuth dependencies
```

---

## 💻 Frontend Integration Example

### React with @react-oauth/google

```javascript
import { GoogleLogin } from '@react-oauth/google';

function LoginComponent() {
  const handleGoogleSuccess = async (credentialResponse) => {
    const response = await fetch('http://localhost:8080/api/v1/auth/login/google', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        idToken: credentialResponse.credential
      })
    });

    const data = await response.json();
    if (data.success) {
      localStorage.setItem('accessToken', data.data.accessToken);
      localStorage.setItem('refreshToken', data.data.refreshToken);
      window.location.href = '/dashboard';
    }
  };

  return (
    <GoogleLogin
      onSuccess={handleGoogleSuccess}
      onError={() => console.log('Login Failed')}
    />
  );
}
```

---

## 🧪 Test API (Pilih một)

### Option 1: Swagger UI (Dễ nhất)
```
http://localhost:8080/swagger-ui.html
→ Tìm POST /api/v1/auth/login/google
→ Test
```

### Option 2: cURL
```bash
curl -X POST http://localhost:8080/api/v1/auth/login/google \
  -H "Content-Type: application/json" \
  -d '{"idToken": "your-google-id-token"}'
```

### Option 3: Postman
1. Import: `GOOGLE_OAUTH_API_SPEC.md`
2. Set idToken
3. Send POST request

---

## 🔑 Lấy Google Client ID (Chi tiết)

### Step 1: Google Cloud Console
```
https://console.cloud.google.com
```

### Step 2: Tạo Project
- Click "Select a Project"
- Click "NEW PROJECT"
- Nhập tên: "Smart Money"

### Step 3: Bật APIs
- Search "Google+ API"
- Click "ENABLE"

### Step 4: Tạo OAuth Credentials
- Left menu → "Credentials"
- Click "CREATE CREDENTIALS"
- Select "OAuth client ID"
- Application type: "Web application"
- Authorized redirect URIs:
  - `http://localhost:3000`
  - `http://localhost:8080`
  - `https://yourdomain.com`
- Create
- Copy "Client ID"

### Step 5: Set Environment Variable
```bash
export GOOGLE_CLIENT_ID="798767812456-27n46..."
```

---

## 📊 API Response Format

### Success (200 OK)
```json
{
  "success": true,
  "message": "Google login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": 1,
      "username": "moik5985_a1b2c3d4",
      "fullName": "12T2-04 Ngô Hải Bằng",
      "email": "moik5985@gmail.com",
      "avatar": "https://lh3.googleusercontent.com/...",
      "coin": 0,
      "role": "USER",
      "rate": 5.0,
      "active": true
    }
  }
}
```

### Error (401 Unauthorized)
```json
{
  "success": false,
  "message": "Google login failed: Invalid Google ID token"
}
```

---

## 🔄 Login Flow

```
User
  ↓ Clicks "Login with Google"
Google OAuth Dialog
  ↓ User authorizes
Frontend receives ID Token
  ↓ POST /api/v1/auth/login/google
Backend
  ├─ Verifies token with Google
  ├─ Checks if user exists
  │ ├─ YES → Get existing user
  │ └─ NO → Create new user
  ├─ Generates access & refresh tokens
  └─ Returns AuthResponse
Frontend
  ├─ Stores accessToken
  ├─ Stores refreshToken
  └─ Redirects to dashboard
Dashboard
  └─ Uses accessToken for API calls
```

---

## 🛡️ Security

✅ Google tokens verified server-side
✅ Empty password for OAuth users (can't login with email/password)
✅ Access token expires in 1 hour
✅ Refresh token expires in 7 days
✅ Email verified by Google
✅ Database stores refresh tokens securely

---

## 🐛 Troubleshooting

| Issue | Solution |
|-------|----------|
| "Invalid Google ID token" | Check Client ID matches, token not expired |
| "Email not found in Google token" | Email must be verified with Google |
| 401 Unauthorized | Check CORS, Google project enabled |
| 500 Internal Error | Check logs: `logs/smart-money.log` |

Enable debug logging:
```yaml
logging:
  level:
    com.example.smartmoney: DEBUG
```

---

## 📚 Full Documentation

Read these files for detailed info:

1. **GOOGLE_OAUTH_SETUP.md** - Complete setup guide
2. **GOOGLE_LOGIN_QUICKSTART.md** - Quick start with examples
3. **GOOGLE_OAUTH_API_SPEC.md** - API details and code examples
4. **IMPLEMENTATION_SUMMARY.md** - What was implemented

---

## ✅ Checklist

- [ ] Run: `mvn clean install`
- [ ] Get Google Client ID
- [ ] Set `GOOGLE_CLIENT_ID` environment variable
- [ ] Run: `mvn spring-boot:run`
- [ ] Test in Swagger: `http://localhost:8080/swagger-ui.html`
- [ ] Test with real Google token
- [ ] Integrate frontend
- [ ] Deploy to production

---

## 🎯 What Works Now

✅ Users can login with Google
✅ Automatic account creation
✅ JWT tokens generated
✅ User info saved from Google
✅ Refresh token support
✅ Account activation/deactivation
✅ Full API documentation
✅ Error handling
✅ Logging

---

## 🚀 Production Checklist

- [ ] Use real Google Client ID (not example)
- [ ] Set GOOGLE_CLIENT_ID as environment variable
- [ ] Enable HTTPS
- [ ] Update CORS allowed origins
- [ ] Configure database backup
- [ ] Set up monitoring
- [ ] Enable debug logging OFF
- [ ] Test error scenarios
- [ ] Setup rate limiting
- [ ] Configure SSL/TLS

---

## 📞 Next Steps

1. ✅ Read the quick start guide above
2. ✅ Get Google Client ID
3. ✅ Run: `mvn clean install && mvn spring-boot:run`
4. ✅ Test in Swagger
5. ✅ Integrate with frontend
6. ✅ Deploy

---

## 📝 Notes

- Google users can't set password (OAuth only)
- Username auto-generated from email + random string
- Avatar auto-saved from Google profile
- Accounts can be reactivated after deactivation
- Refresh tokens have 7-day expiration

---

**Status: ✅ Ready to Use**

Happy Coding! 🎉

For detailed info, see the documentation files in the project root.
