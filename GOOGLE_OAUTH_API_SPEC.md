# Google OAuth API - Chi Tiết Kỹ Thuật

## Endpoint Details

### POST /api/v1/auth/login/google

**Description:** Xác thực người dùng bằng Google ID Token

**Method:** POST

**Base URL:** `http://localhost:8080`

**Full URL:** `http://localhost:8080/api/v1/auth/login/google`

---

## Request

### Headers
```
Content-Type: application/json
```

### Body (JSON)
```json
{
  "idToken": "string (required)",
  "email": "string (optional)",
  "name": "string (optional)",
  "picture": "string (optional)",
  "givenName": "string (optional)",
  "familyName": "string (optional)"
}
```

### Parameters Explanation

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| idToken | string | ✅ Yes | Google ID Token từ frontend |
| email | string | ❌ No | Email address (redundant if in token) |
| name | string | ❌ No | Full name (redundant if in token) |
| picture | string | ❌ No | Avatar URL (redundant if in token) |
| givenName | string | ❌ No | First name |
| familyName | string | ❌ No | Last name |

---

## Response

### 200 OK - Success
```json
{
  "success": true,
  "message": "Google login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": 1,
      "username": "moik5985_a1b2c3d4",
      "fullName": "12T2-04 Ngô Hải Bằng",
      "email": "moik5985@gmail.com",
      "avatar": "https://lh3.googleusercontent.com/a/...",
      "dateOfBirth": null,
      "phone": null,
      "coin": 0,
      "role": "USER",
      "rate": 5.0,
      "active": true
    }
  }
}
```

### Response Fields

| Field | Type | Description |
|-------|------|-------------|
| success | boolean | Status của request |
| message | string | Mô tả kết quả |
| data | object | Dữ liệu response |
| data.accessToken | string | JWT access token (sử dụng cho requests sau) |
| data.refreshToken | string | JWT refresh token (sử dụng để lấy token mới) |
| data.tokenType | string | "Bearer" (loại token) |
| data.expiresIn | long | Thời gian hết hạn của token (giây) |
| data.user | object | Thông tin người dùng |
| data.user.id | long | User ID trong database |
| data.user.username | string | Username (auto-generated) |
| data.user.fullName | string | Họ và tên |
| data.user.email | string | Email address |
| data.user.avatar | string | Avatar URL |
| data.user.coin | integer | Số coins (mặc định 0) |
| data.user.role | string | "USER", "ADMIN", etc. |
| data.user.rate | double | Rating (mặc định 5.0) |
| data.user.active | boolean | Trạng thái tài khoản |

---

## Error Responses

### 400 Bad Request
```json
{
  "success": false,
  "message": "Invalid input or user already exists"
}
```

**Causes:**
- ID Token không hợp lệ hoặc bị thiếu
- Request body không đúng định dạng JSON
- Email không được verify bởi Google

### 401 Unauthorized
```json
{
  "success": false,
  "message": "Google authentication failed: Invalid Google ID token"
}
```

**Causes:**
- Token đã hết hạn
- Token không được ký chính xác
- Client ID không khớp
- Google verification thất bại

### 500 Internal Server Error
```json
{
  "success": false,
  "message": "Google authentication failed: [Error Details]"
}
```

**Causes:**
- Database connection error
- Server configuration error
- Unexpected exception

---

## Code Examples

### cURL
```bash
curl -X POST http://localhost:8080/api/v1/auth/login/google \
  -H "Content-Type: application/json" \
  -d '{
    "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjdiZjU5NTQ4OWEwYmIxNThiMDg1ZTIzZTdiNTJiZjk4OTFlMDQ1MzgiLCJ0eXAiOiJKV1QifQ..."
  }'
```

### JavaScript/Fetch
```javascript
const googleToken = credentialResponse.credential;

fetch('http://localhost:8080/api/v1/auth/login/google', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json'
  },
  body: JSON.stringify({
    idToken: googleToken
  })
})
.then(response => response.json())
.then(data => {
  if (data.success) {
    console.log('Login successful!');
    console.log('Access Token:', data.data.accessToken);
    console.log('User:', data.data.user);
    
    // Lưu tokens
    localStorage.setItem('accessToken', data.data.accessToken);
    localStorage.setItem('refreshToken', data.data.refreshToken);
  } else {
    console.error('Login failed:', data.message);
  }
})
.catch(error => console.error('Error:', error));
```

### React with @react-oauth/google
```javascript
import { GoogleLogin } from '@react-oauth/google';

function GoogleLoginComponent() {
  const handleSuccess = async (credentialResponse) => {
    try {
      const response = await fetch('/api/v1/auth/login/google', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          idToken: credentialResponse.credential
        })
      });

      const result = await response.json();

      if (result.success) {
        // Lưu user info
        localStorage.setItem('user', JSON.stringify(result.data.user));
        localStorage.setItem('accessToken', result.data.accessToken);
        
        // Redirect
        window.location.href = '/dashboard';
      }
    } catch (error) {
      console.error('Login error:', error);
    }
  };

  return (
    <GoogleLogin
      onSuccess={handleSuccess}
      onError={() => console.log('Login Failed')}
    />
  );
}
```

### Python Requests
```python
import requests
import json

google_token = "eyJhbGciOiJSUzI1NiIs..."

response = requests.post(
    'http://localhost:8080/api/v1/auth/login/google',
    headers={'Content-Type': 'application/json'},
    json={'idToken': google_token}
)

data = response.json()
if data['success']:
    print(f"Login successful!")
    print(f"Access Token: {data['data']['accessToken']}")
    print(f"User: {data['data']['user']}")
else:
    print(f"Login failed: {data['message']}")
```

### TypeScript/Axios
```typescript
import axios from 'axios';

interface GoogleLoginRequest {
  idToken: string;
}

interface AuthResponse {
  success: boolean;
  message: string;
  data?: {
    accessToken: string;
    refreshToken: string;
    tokenType: string;
    expiresIn: number;
    user: {
      id: number;
      username: string;
      email: string;
      fullName: string;
    };
  };
}

async function googleLogin(idToken: string): Promise<AuthResponse> {
  const response = await axios.post<AuthResponse>(
    'http://localhost:8080/api/v1/auth/login/google',
    { idToken } as GoogleLoginRequest,
    {
      headers: { 'Content-Type': 'application/json' }
    }
  );
  return response.data;
}
```

---

## Token Usage

### Access Token
Sử dụng trong header cho authenticated requests:
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

Ví dụ:
```bash
curl -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..." \
  http://localhost:8080/api/v1/users/me
```

### Refresh Token
Sử dụng endpoint `/api/v1/auth/refresh-token`:
```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh-token \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
  }'
```

---

## Flow Sequence Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend                                 │
├─────────────────────────────────────────────────────────────────┤
│ 1. User clicks "Login with Google"                              │
│ 2. Google OAuth Dialog appears                                  │
│ 3. User authorizes application                                  │
│ 4. Receive: credentialResponse.credential (ID Token)            │
│ 5. POST /api/v1/auth/login/google {idToken: "..."}             │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                         Backend                                  │
├─────────────────────────────────────────────────────────────────┤
│ 1. Receive GoogleLoginRequest                                   │
│ 2. Verify ID Token with Google OAuth                            │
│ 3. Extract email, name, picture from token                      │
│ 4. Look up user in database by email                            │
│ 5. If not found:                                                │
│    - Generate unique username                                   │
│    - Create new User account                                    │
│ 6. Check if user is active                                      │
│ 7. Generate JWT access token                                    │
│ 8. Generate JWT refresh token                                   │
│ 9. Save refresh token in database                               │
│ 10. Return AuthResponse with tokens                             │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                         Frontend                                 │
├─────────────────────────────────────────────────────────────────┤
│ 1. Receive AuthResponse                                         │
│ 2. Store accessToken in localStorage                            │
│ 3. Store refreshToken securely                                  │
│ 4. Store user info in localStorage                              │
│ 5. Redirect to dashboard                                        │
│ 6. Use accessToken for subsequent API calls                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## Security Considerations

1. **Token Verification**
   - Tất cả ID Tokens được xác minh với Google's public keys
   - Invalid tokens bị reject ngay lập tức

2. **Data Validation**
   - Email từ token được kiểm tra tính hợp lệ
   - Tất cả input được validate trước khi sử dụng

3. **Password Security**
   - Google OAuth users không có mật khẩu
   - Nếu muốn set password sau, phải qua endpoint riêng

4. **Token Expiration**
   - Access Token: 1 hour (3600 seconds)
   - Refresh Token: 7 days
   - Expired tokens bị reject

5. **CORS & Origins**
   - Chỉ allowed origins mới có thể gọi API
   - Cấu hình trong application.yml

---

## Performance Notes

- Google token verification: ~100-200ms
- Database lookup by email: ~5-10ms
- JWT token generation: ~1-2ms
- Total response time: ~150-250ms

---

## Integration Checklist

- [ ] Google Cloud Console project created
- [ ] OAuth 2.0 Client ID obtained
- [ ] Client ID set in application.yml
- [ ] Dependencies installed (mvn clean install)
- [ ] Server started (mvn spring-boot:run)
- [ ] API tested in Swagger
- [ ] Frontend integration completed
- [ ] CORS configured correctly
- [ ] Error handling implemented
- [ ] Tokens stored securely in frontend
- [ ] Access token used in subsequent requests
- [ ] Refresh token implementation tested

---

## Support & Debugging

Enable debug logging:
```yaml
logging:
  level:
    com.example.smartmoney: DEBUG
    com.google.auth: DEBUG
```

Check logs:
```bash
tail -f logs/smart-money.log
```

Common issues and solutions are documented in GOOGLE_OAUTH_SETUP.md
