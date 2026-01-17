# Google OAuth Integration Guide

## Tổng Quan
Hệ thống đã được tích hợp xác thực Google OAuth 2.0, cho phép người dùng đăng nhập bằng tài khoản Google.

## Các Thành Phần Được Tạo

### 1. DTO - GoogleLoginRequest
**File:** `src/main/java/com/example/smartmoney/application/dtos/requests/auth/GoogleLoginRequest.java`

```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjdiZjU5NTQ4OWEwYmIxNThiMDg1ZTIzZTdiNTJiZjk4OTFlMDQ1MzgiLCJ0eXAiOiJKV1QifQ...",
  "email": "user@gmail.com",           // Optional
  "name": "User Name",                 // Optional
  "picture": "https://...",            // Optional
  "givenName": "User",                 // Optional
  "familyName": "Name"                 // Optional
}
```

### 2. Use Case - GoogleLoginUseCase
**File:** `src/main/java/com/example/smartmoney/application/usecases/auth/GoogleLoginUseCase.java`

Chức năng:
- Xác minh Google ID Token từ frontend
- Kiểm tra xem người dùng đã tồn tại hay chưa
- Tạo tài khoản mới (nếu người dùng không tồn tại)
- Sinh access token và refresh token
- Trả về AuthResponse với thông tin người dùng

### 3. Controller Endpoint
**File:** `src/main/java/com/example/smartmoney/presentation/controllers/AuthController.java`

**Endpoint:** `POST /api/v1/auth/login/google`

```bash
curl -X POST http://localhost:8080/api/v1/auth/login/google \
  -H "Content-Type: application/json" \
  -d '{
    "idToken": "eyJhbGciOiJSUzI1NiIs..."
  }'
```

**Response Success (200 OK):**
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
      "role": "USER",
      "coin": 0,
      "rate": 5.0,
      "active": true
    }
  }
}
```

**Response Error (401 Unauthorized):**
```json
{
  "success": false,
  "message": "Google login failed: Invalid Google ID token"
}
```

### 4. Domain Model Enhancement
**File:** `src/main/java/com/example/smartmoney/domain/models/user/User.java`

Thêm factory method:
- `User.createGoogleUser()` - Tạo người dùng từ dữ liệu Google OAuth
- `activate()` - Kích hoạt tài khoản
- `deactivate()` - Vô hiệu hóa tài khoản

### 5. Configuration
**File:** `src/main/resources/application.yml`

```yaml
app:
  google:
    oauth2:
      client-id: ${GOOGLE_CLIENT_ID:798767812456-27n46msrhfq1jkh357i6ckbrgvq65kp3.apps.googleusercontent.com}
```

### 6. Dependencies
**File:** `pom.xml`

Thêm dependencies cho Google OAuth:
```xml
<dependency>
    <groupId>com.google.api-client</groupId>
    <artifactId>google-api-client</artifactId>
    <version>2.2.0</version>
</dependency>
<dependency>
    <groupId>com.google.auth</groupId>
    <artifactId>google-auth-library-oauth2-http</artifactId>
    <version>1.11.0</version>
</dependency>
```

## Flow Xác Thực Google OAuth

```
Frontend                          Backend                          Google
   |                                |                                |
   |--- Request /auth/google ------>|                                |
   |                                |                                |
   |<---- Redirect to Google --------|                                |
   |                                                                   |
   |<------------ User Authorization --------------------------------->|
   |                                                                   |
   |<---- Receive ID Token ---------->|                                |
   |                                                                   |
   |--- POST /api/v1/auth/login/google with idToken -->|              |
   |                                                     |              |
   |                                    |---- Verify idToken -------->|
   |                                    |<---- Token Valid -----------|
   |                                    |
   |                                    | Check/Create User
   |                                    | Generate JWT Tokens
   |                                    |
   |<-- AccessToken & RefreshToken ----|
   |
```

## Frontend Implementation (React/JavaScript)

```javascript
import { GoogleLogin } from '@react-oauth/google';

function LoginComponent() {
  const handleGoogleSuccess = async (credentialResponse) => {
    try {
      const response = await fetch('http://localhost:8080/api/v1/auth/login/google', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          idToken: credentialResponse.credential
        })
      });

      const data = await response.json();
      
      if (data.success) {
        // Lưu tokens
        localStorage.setItem('accessToken', data.data.accessToken);
        localStorage.setItem('refreshToken', data.data.refreshToken);
        localStorage.setItem('user', JSON.stringify(data.data.user));
        
        // Redirect to dashboard
        window.location.href = '/dashboard';
      } else {
        console.error('Login failed:', data.message);
      }
    } catch (error) {
      console.error('Error during login:', error);
    }
  };

  return (
    <GoogleLogin
      onSuccess={handleGoogleSuccess}
      onError={() => console.log('Login Failed')}
    />
  );
}

export default LoginComponent;
```

## Cấu Hình Google OAuth Client ID

### Bước 1: Tạo Google OAuth Project
1. Truy cập [Google Cloud Console](https://console.cloud.google.com)
2. Tạo project mới
3. Bật API: Google+ API

### Bước 2: Tạo OAuth 2.0 Credentials
1. Chọn "Create Credentials" → "OAuth client ID"
2. Chọn Application type: "Web application"
3. Thêm Authorized redirect URIs:
   - `http://localhost:3000`
   - `http://localhost:8080`
   - `https://yourdomain.com`
4. Sao chép Client ID

### Bước 3: Cấu Hình Environment Variables
Tạo file `.env` hoặc `.env.local`:
```
GOOGLE_CLIENT_ID=your_client_id_here.apps.googleusercontent.com
```

## Testing API

### 1. Sử dụng Swagger UI
Truy cập: `http://localhost:8080/swagger-ui.html`

Tìm endpoint: `POST /api/v1/auth/login/google`

### 2. Sử dụng cURL
```bash
curl -X POST http://localhost:8080/api/v1/auth/login/google \
  -H "Content-Type: application/json" \
  -d '{
    "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjdiZjU5NTQ4OWEwYmIxNThiMDg1ZTIzZTdiNTJiZjk4OTFlMDQ1MzgiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiI3OTg3Njc4MTI0NTYtMjduNDZtc3JoZnExamtoMzU3aTZja2JyZ3ZxNjVrcDMuYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJhdWQiOiI3OTg3Njc4MTI0NTYtZGNiYjVtN2JldmxybGcyY29pazZkNGZnaTlwdTg2dG8uYXBwcy5nb29nbGV1c2VyY29udGVudC5jb20iLCJzdWIiOiIxMTA4NDQ2ODIxMDIyMDMyMzQzNDkiLCJlbWFpbCI6Im1vaWs1OTg1QGdtYWlsLmNvbSIsImVtYWlsX3ZlcmlmaWVkIjp0cnVlLCJuYW1lIjoiMTJUMi0wNCBOZ8O0IEjhuqNpIELhurFuZyIsInBpY3R1cmUiOiJodHRwczovL2xoMy5nb29nbGV1c2VyY29udGVudC5jb20vYS9BQ2c4b2NKdFRqR2xHSE5QTUktcHBzN3V4SzBuYmFfRlUycmZPblNfekJiV2FSUGNIX0liUWJ5Mj1zOTYtYyIsImdpdmVuX25hbWUiOiIxMlQyLTA0IiwiZmFtaWx5X25hbWUiOiJOZ8O0IEjhuqNpIELhurFuZyIsImlhdCI6MTc2ODQyMTEyMywiZXhwIjoxNzY4NDI0NzIzfQ.i-C27TF7AEcbOx-iGgn1oYdX_0wPxKohHMkm6Z2JnGdH458KoV88NvDGiNIPQLr4Baqh4MqvWcoiJFnvoR62xkDizHfmjuFXrtKkAG6mP9XM46p3KE9TvnG9SyEXxZznsTi1gNhe7vmML3WwCgmaGMBymCbCgYOo7LKcTTBaE8jjT1smQSzmMDj3Ozg9Zg1p6HGC6Zv9frjvgb2NA1MnLve5v4xuBfQEjxpiMz0Njjk7lpJXXj3AWr7Jcid2gOeFh6nethsZlIMoeXvoWhXDRJhGtrwzOeiI0-el82KPpVcFVZR1VMmF1kMHRr7OCoDguRnZElabpGSLHbC0hWodLw"
  }'
```

### 3. Sử dụng Postman
1. Tạo request POST tới: `http://localhost:8080/api/v1/auth/login/google`
2. Headers: `Content-Type: application/json`
3. Body (raw JSON):
```json
{
  "idToken": "your_google_id_token"
}
```

## Xử Lý Lỗi

| Code | Message | Nguyên Nhân |
|------|---------|-----------|
| 400 | Invalid Google ID token | Token không hợp lệ hoặc đã hết hạn |
| 400 | Email not found in Google token | Email không được xác minh từ Google |
| 401 | Google authentication failed | Lỗi xác minh hoặc xử lý |
| 500 | Internal server error | Lỗi server |

## Bảo Mật

1. **Luôn xác minh ID Token** trên backend
2. **Không lưu trữ mật khẩu** cho tài khoản Google OAuth
3. **Sử dụng HTTPS** trong production
4. **Kiểm tra email_verified** từ Google token
5. **Tạo username độc đáo** cho các tài khoản Google

## Quy Trình Đăng Nhập Lần Đầu

1. Người dùng nhấp vào "Login with Google"
2. Frontend nhận ID Token từ Google
3. Frontend gửi ID Token tới `/api/v1/auth/login/google`
4. Backend:
   - Xác minh ID Token
   - Kiểm tra xem email đã tồn tại chưa
   - Nếu không → Tạo tài khoản mới tự động
   - Nếu có → Kiểm tra trạng thái tài khoản
   - Sinh JWT tokens
5. Trả về access token, refresh token, và user info
6. Frontend lưu tokens và redirect tới dashboard

## Lưu Ý Quan Trọng

- Thay đổi `GOOGLE_CLIENT_ID` với Client ID thực tế từ Google Cloud Console
- Cấu hình `allowed-origins` phù hợp với domain của frontend
- Đặt biến môi trường `GOOGLE_CLIENT_ID` cho production
- Không commit Google Client ID vào repository công khai

## Khắc Phục Sự Cố

**Lỗi: "Invalid Google ID token"**
- Kiểm tra Client ID có khớp không
- Kiểm tra token không quá hạn
- Xác nhận frontend gửi đúng ID token

**Lỗi: "Email not found in Google token"**
- Email chưa được xác minh từ phía Google
- Yêu cầu người dùng xác minh email

**Lỗi: "Unauthorized"**
- Kiểm tra cấu hình CORS
- Xác nhận Google OAuth Project được bật

## Liên Hệ & Hỗ Trợ

Nếu gặp vấn đề, vui lòng kiểm tra:
1. Application logs: `logs/smart-money.log`
2. Google Cloud Console audit logs
3. Browser console (frontend errors)
