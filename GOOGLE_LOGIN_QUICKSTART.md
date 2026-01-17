# 🚀 Google OAuth API - Quick Start Guide

## Tóm Tắt Những Gì Đã Được Tạo

Hệ thống backend hiện đã có đầy đủ API đăng nhập Google OAuth 2.0. Dưới đây là một hướng dẫn nhanh để bắt đầu sử dụng.

## 📋 Danh Sách Các File Được Tạo/Sửa

### 1. DTOs (Data Transfer Objects)
- **GoogleLoginRequest.java** - Nhận ID Token từ frontend

### 2. Use Cases
- **GoogleLoginUseCase.java** - Xử lý logic xác thực Google

### 3. Controllers
- **AuthController.java** - Thêm endpoint `/api/v1/auth/login/google`

### 4. Domain Models
- **User.java** - Thêm factory method `createGoogleUser()` và các methods `activate()`, `deactivate()`

### 5. Configuration
- **application.yml** - Thêm cấu hình Google OAuth Client ID
- **pom.xml** - Thêm dependencies Google OAuth

### 6. Documentation & Tests
- **GOOGLE_OAUTH_SETUP.md** - Hướng dẫn chi tiết
- **AuthControllerGoogleLoginTest.java** - Unit tests

## 🔧 Cấu Hình Nhanh

### Bước 1: Cập Nhật Dependency
File `pom.xml` đã được cập nhật với:
```xml
<dependency>
    <groupId>com.google.api-client</groupId>
    <artifactId>google-api-client</artifactId>
    <version>2.2.0</version>
</dependency>
```

Chạy lệnh:
```bash
mvn clean install
```

### Bước 2: Cấu Hình Google Client ID
Tạo file `.env` hoặc cập nhật biến môi trường:
```
GOOGLE_CLIENT_ID=798767812456-27n46msrhfq1jkh357i6ckbrgvq65kp3.apps.googleusercontent.com
```

> **⚠️ QUAN TRỌNG:** Thay thế Client ID này bằng Client ID thực tế từ Google Cloud Console

### Bước 3: Khởi Động Server
```bash
mvn spring-boot:run
```

Server sẽ chạy trên: `http://localhost:8080`

## 📱 Cách Sử Dụng từ Frontend

### JavaScript/React Example:
```javascript
import { GoogleLogin } from '@react-oauth/google';

function LoginComponent() {
  const handleGoogleSuccess = async (credentialResponse) => {
    const response = await fetch(
      'http://localhost:8080/api/v1/auth/login/google',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          idToken: credentialResponse.credential
        })
      }
    );

    const data = await response.json();
    if (data.success) {
      // Lưu tokens
      localStorage.setItem('accessToken', data.data.accessToken);
      localStorage.setItem('refreshToken', data.data.refreshToken);
      // Redirect hoặc cập nhật state
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

## 🧪 Test API

### Phương Pháp 1: Swagger UI
Truy cập: `http://localhost:8080/swagger-ui.html`
- Tìm `POST /api/v1/auth/login/google`
- Nhập ID Token (lấy từ frontend)
- Click "Execute"

### Phương Pháp 2: cURL
```bash
curl -X POST http://localhost:8080/api/v1/auth/login/google \
  -H "Content-Type: application/json" \
  -d '{
    "idToken": "eyJhbGciOiJSUzI1NiIs..."
  }'
```

### Phương Pháp 3: Postman
1. Tạo request POST
2. URL: `http://localhost:8080/api/v1/auth/login/google`
3. Headers: `Content-Type: application/json`
4. Body (raw):
```json
{
  "idToken": "your_google_id_token_here"
}
```

## ✅ Response Format

**Thành Công (200 OK):**
```json
{
  "success": true,
  "message": "Google login successful",
  "data": {
    "accessToken": "eyJhbGc...",
    "refreshToken": "eyJhbGc...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": 1,
      "username": "moik5985_a1b2c3d4",
      "fullName": "12T2-04 Ngô Hải Bằng",
      "email": "moik5985@gmail.com",
      "avatar": "https://...",
      "role": "USER",
      "coin": 0,
      "active": true
    }
  }
}
```

**Lỗi (401 Unauthorized):**
```json
{
  "success": false,
  "message": "Google login failed: Invalid Google ID token"
}
```

## 🔑 Lấy Google Client ID

1. Truy cập [Google Cloud Console](https://console.cloud.google.com)
2. Tạo project mới
3. Bật "Google+ API"
4. Tạo OAuth 2.0 Credentials:
   - Application type: "Web application"
   - Authorized redirect URIs:
     - `http://localhost:3000`
     - `http://localhost:8080`
     - `https://yourdomain.com`
5. Copy Client ID

## 🔄 Flow Đăng Nhập

```
User clicks "Login with Google"
         ↓
Frontend shows Google consent screen
         ↓
User approves & gets ID Token
         ↓
Frontend sends ID Token to: POST /api/v1/auth/login/google
         ↓
Backend verifies token with Google
         ↓
Check if user exists in database
  ├─ YES: Return access token
  └─ NO: Create new user & return access token
         ↓
Frontend stores tokens in localStorage
         ↓
User is logged in ✅
```

## 📊 Database Schema

Người dùng Google được lưu với:
- `username`: Auto-generated từ email + UUID
- `email`: Từ Google token
- `fullName`: Từ Google profile
- `avatar`: URL từ Google
- `password`: Empty (OAuth user)
- `role`: USER (default)
- `coin`: 0 (default)
- `active`: true

## 🛡️ Bảo Mật

✅ ID Token được xác minh từ Google
✅ Email được kiểm tra từ Google token
✅ Mật khẩu không bị lưu cho OAuth users
✅ Tokens có expiration time
✅ Refresh token được lưu safely trong database

## 🐛 Khắc Phục Sự Cố

| Problem | Solution |
|---------|----------|
| "Invalid Google ID token" | Kiểm tra Client ID khớp không, token không quá hạn |
| "Email not found" | Email phải được verify bởi Google |
| "Unauthorized" | Kiểm tra CORS settings, Google project enabled |
| 500 Internal Error | Xem logs: `logs/smart-money.log` |

## 📚 Tài Liệu Chi Tiết

Xem file [GOOGLE_OAUTH_SETUP.md](./GOOGLE_OAUTH_SETUP.md) để có hướng dẫn đầy đủ.

## ✨ Tính Năng

✅ Google OAuth 2.0 integration
✅ Automatic user account creation
✅ JWT token generation
✅ Refresh token support
✅ Account reactivation
✅ Full Swagger/OpenAPI documentation
✅ Comprehensive error handling
✅ Unit tests included

## 🎯 Next Steps

1. ✅ Cập nhật Google Client ID
2. ✅ Install dependencies: `mvn clean install`
3. ✅ Start server: `mvn spring-boot:run`
4. ✅ Test endpoint trong Swagger
5. ✅ Integrate vào frontend
6. ✅ Deploy to production

---

**Happy Coding! 🚀**

Có câu hỏi? Xem file GOOGLE_OAUTH_SETUP.md hoặc kiểm tra logs.
