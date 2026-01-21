# Supabase + Facebook OAuth Integration Guide

## 🎯 Overview

This backend now validates **Supabase JWT tokens** instead of Facebook tokens directly. This is the correct approach because:

1. **Frontend handles OAuth** - Frontend authenticates with Facebook via Supabase SDK
2. **Supabase issues JWT** - Supabase returns a JWT token after successful authentication
3. **Backend validates JWT** - Backend validates the Supabase JWT (no need to call Facebook API)
4. **Backend generates its own token** - Backend creates its own JWT for subsequent API calls

## 🏗️ Architecture

```
Frontend (React/Expo)
  ↓
[Facebook OAuth via Supabase]
  ↓
Supabase Auth Service
  ↓ (returns JWT access_token)
Frontend sends JWT to Backend
  ↓
Backend: POST /api/v1/auth/login/facebook
  ↓
SupabaseJwtValidator validates JWT
  ↓
Backend creates/finds user
  ↓
Backend generates its own JWT token
  ↓
Frontend receives backend JWT
```

## 📋 Setup Steps

### 1. Get Supabase Credentials

1. Go to [Supabase Dashboard](https://app.supabase.com)
2. Select your project
3. Go to **Settings → API**
4. Copy these values:
   - `Project URL` → `SUPABASE_PROJECT_URL`
   - `anon public` → `SUPABASE_ANON_KEY`
   - `service_role secret` → `SUPABASE_SERVICE_ROLE_KEY`

### 2. Update .env File

```bash
# .env
SUPABASE_PROJECT_URL=https://xxxxxxxxxxxxx.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
SUPABASE_SERVICE_ROLE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...

APP_FACEBOOK_APP_ID=your-facebook-app-id
APP_FACEBOOK_APP_SECRET=your-facebook-app-secret
```

### 3. Test the Integration

#### Frontend Flow

```typescript
// Frontend: useFacebookLogin.ts

// Step 1: User authenticates with Facebook via Supabase
const { data, error } = await supabase.auth.signInWithOAuth({
  provider: 'facebook',
  options: {
    redirectTo: 'frontend://',
    skipBrowserRedirect: true,
  },
});

// Step 2: User completes Facebook login flow
const result = await WebBrowser.openAuthSessionAsync(data.url, 'frontend://');

// Step 3: Extract tokens from callback
const { access_token, refresh_token } = /* from callback URL */;

// Step 4: Set Supabase session
await supabase.auth.setSession({ access_token, refresh_token });

// Step 5: Send Supabase JWT to backend
const response = await fetch('http://localhost:8080/api/v1/auth/login/facebook', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    accessToken: access_token  // 👈 This is Supabase JWT, not Facebook token!
  })
});

const { data: { data: { accessToken } } } = await response.json();
// Store backend JWT token for subsequent API calls
```

#### Backend Flow

```java
// Backend: FacebookLoginUseCase.java

1. Receive Supabase JWT from frontend
   POST /api/v1/auth/login/facebook
   Body: { "accessToken": "eyJhbGc..." }

2. Validate JWT using SupabaseJwtValidator
   - Validates JWT signature using Supabase's public key
   - Extracts user claims from JWT
   - No need to call Facebook API!

3. Extract user information
   - Email
   - User ID (sub claim)
   - Name (if available)

4. Create or update user in database

5. Generate backend JWT token
   - accessToken (1 hour validity)
   - refreshToken (7 days validity)

6. Return tokens to frontend
```

## 🔧 API Endpoint

### POST `/api/v1/auth/login/facebook`

**Request Body:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response (Success):**
```json
{
  "success": true,
  "message": "Facebook login successful",
  "data": {
    "accessToken": "backend-jwt-token",
    "refreshToken": "refresh-token",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": "user-id",
      "username": "john_doe_12345",
      "fullName": "John Doe",
      "email": "john@example.com",
      "avatar": null,
      "role": "USER",
      "active": true,
      "coin": 0
    }
  }
}
```

**Response (Error):**
```json
{
  "success": false,
  "message": "Facebook authentication failed: Invalid Supabase JWT token"
}
```

## ✅ Advantages of This Approach

| Aspect | Old Approach | New Approach |
|--------|-------------|--------------|
| **Token Validation** | Call Facebook API | Validate JWT locally |
| **Performance** | Slow (external API call) | Fast (local validation) |
| **Reliability** | Depends on Facebook API | Depends on JWT secret |
| **Cost** | Facebook API rate limits | No external calls |
| **Security** | Token from external source | Token from trusted source (Supabase) |
| **User Info** | Request from Facebook API | Already in JWT claims |

## 🐛 Debugging

### Check JWT Token

Enable debug logging in `application.yml`:

```yaml
logging:
  level:
    com.example.smartmoney.infrastructure.external: DEBUG
```

Then check logs:
```
✅ Supabase JWT token validated successfully
📦 User info extracted from Supabase JWT: [sub, email, ...]
👤 User email: john@example.com, Supabase ID: user-123
```

### Manually Verify JWT

Use JWT.io to decode and verify your Supabase JWT:
1. Go to [jwt.io](https://jwt.io)
2. Paste your token
3. Verify payload contains: `sub`, `email`, `aud`, `iss`

## 📚 Related Files

- Config: [SupabaseJwtProperties.java](src/main/java/com/example/smartmoney/infrastructure/config/SupabaseJwtProperties.java)
- Validator: [SupabaseJwtValidator.java](src/main/java/com/example/smartmoney/infrastructure/external/SupabaseJwtValidator.java)
- UseCase: [FacebookLoginUseCase.java](src/main/java/com/example/smartmoney/application/usecases/auth/FacebookLoginUseCase.java)
- Config File: [application.yml](src/main/resources/application.yml)

## 🆘 Troubleshooting

### Error: "Invalid Supabase JWT token"

**Cause:** Token is invalid or expired
**Solution:** 
- Check if Supabase credentials are correct
- Verify token is not expired
- Ensure `SUPABASE_ANON_KEY` is set in `.env`

### Error: "Email not found in Supabase JWT token"

**Cause:** User didn't authorize email permission in Facebook
**Solution:**
- Update Supabase OAuth scopes to include email
- Ask user to re-authenticate

### Error: "Bad signature" (from old implementation)

**Solution:** This should no longer happen! The new implementation validates the Supabase JWT locally, not through Facebook API.

## 📞 Support

For issues or questions, check:
1. Application logs (logs/smart-money.log)
2. Supabase dashboard for token issues
3. .env file configuration
