# Google OAuth Integration - Summary of Changes

## 📋 Overview

Complete Google OAuth 2.0 integration has been implemented for the Smart Money backend. Users can now login using their Google accounts with automatic user account creation.

---

## ✅ New Files Created

### 1. **DTOs** (Data Transfer Objects)
**File:** `src/main/java/com/example/smartmoney/application/dtos/requests/auth/GoogleLoginRequest.java`
- Request class for Google login endpoint
- Contains: idToken, email, name, picture, givenName, familyName

### 2. **Use Cases**
**File:** `src/main/java/com/example/smartmoney/application/usecases/auth/GoogleLoginUseCase.java`
- Business logic for Google authentication
- Token verification with Google
- User lookup or creation
- JWT token generation
- Error handling and logging

### 3. **Tests**
**File:** `src/test/java/com/example/smartmoney/presentation/controllers/AuthControllerGoogleLoginTest.java`
- Unit tests for Google login endpoint
- Test success and failure scenarios

### 4. **Documentation**
**Files:**
- `GOOGLE_OAUTH_SETUP.md` - Complete setup guide
- `GOOGLE_LOGIN_QUICKSTART.md` - Quick start guide
- `GOOGLE_OAUTH_API_SPEC.md` - Detailed API specification

---

## 🔧 Modified Files

### 1. **AuthController.java**
**Location:** `src/main/java/com/example/smartmoney/presentation/controllers/AuthController.java`

**Changes:**
- ✅ Added import for GoogleLoginRequest
- ✅ Added import for GoogleLoginUseCase
- ✅ Added googleLoginUseCase field (dependency injection)
- ✅ Added new endpoint: `POST /api/v1/auth/login/google`
- ✅ Added Swagger documentation for Google login endpoint
- ✅ Added error handling and logging

**New Endpoint:**
```
POST /api/v1/auth/login/google
```

### 2. **User.java** (Domain Model)
**Location:** `src/main/java/com/example/smartmoney/domain/models/user/User.java`

**Changes Added:**
- ✅ Factory method: `createGoogleUser()` - Creates user from Google OAuth data
- ✅ Method: `activate()` - Activates a deactivated account
- ✅ Method: `deactivate()` - Deactivates an account

### 3. **pom.xml**
**Location:** `pom.xml`

**Dependencies Added:**
```xml
<!-- Google OAuth -->
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

### 4. **application.yml**
**Location:** `src/main/resources/application.yml`

**Configuration Added:**
```yaml
app:
  google:
    oauth2:
      client-id: ${GOOGLE_CLIENT_ID:798767812456-27n46msrhfq1jkh357i6ckbrgvq65kp3.apps.googleusercontent.com}
```

---

## 📊 Data Flow

```
Frontend (React/Vue/Angular)
    ↓ (User clicks "Login with Google")
Google OAuth Dialog
    ↓ (User authorizes)
Frontend receives ID Token
    ↓ (POST request)
POST /api/v1/auth/login/google
    ↓
GoogleLoginUseCase.execute()
    ├─ Verify ID Token with Google
    ├─ Extract email, name, picture
    ├─ Check if user exists
    │  ├─ If YES: Use existing user
    │  └─ If NO: Create new user account
    ├─ Generate JWT access token
    ├─ Generate JWT refresh token
    └─ Save refresh token in database
    ↓
AuthResponse returned to Frontend
    ├─ accessToken
    ├─ refreshToken
    ├─ tokenType ("Bearer")
    ├─ expiresIn (3600 seconds)
    └─ user (with all details)
    ↓
Frontend stores tokens in localStorage
    ↓ (Subsequent requests)
Add header: Authorization: Bearer {accessToken}
```

---

## 🔐 Security Features Implemented

1. ✅ **Google Token Verification**
   - All ID tokens verified with Google's public keys
   - Invalid tokens rejected immediately

2. ✅ **Password Security**
   - Google OAuth users have empty password
   - Cannot login with username/password

3. ✅ **Token Expiration**
   - Access Token: 1 hour (3600 seconds)
   - Refresh Token: 7 days

4. ✅ **Email Verification**
   - Email must be verified by Google
   - Validated before user creation

5. ✅ **Database Security**
   - Refresh tokens stored in database (not in JWT)
   - User account status tracked

6. ✅ **CORS Configuration**
   - Only allowed origins can access API
   - Prevents unauthorized cross-origin requests

---

## 🚀 API Endpoint Details

### New Endpoint: POST /api/v1/auth/login/google

**Request:**
```json
{
  "idToken": "eyJhbGciOiJSUzI1NiIs..."
}
```

**Response (Success - 200):**
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

**Response (Error - 401):**
```json
{
  "success": false,
  "message": "Google login failed: Invalid Google ID token"
}
```

---

## 📦 New Classes & Interfaces

### 1. GoogleLoginRequest (DTO)
- Fields: idToken, email, name, picture, givenName, familyName
- Validation: idToken is required

### 2. GoogleLoginUseCase (Service)
- Dependencies: UserRepository, AuthPort, GoogleIdTokenVerifier
- Methods:
  - `execute(GoogleLoginRequest)` → AuthResponse
  - `createUserFromGoogle(email, name, picture)` → User
  - `mapUserToResponse(user)` → UserResponse

---

## 🔄 User Account Lifecycle

### For Google OAuth Users:

1. **First Login:**
   - Email extracted from Google token
   - User account created automatically
   - Random username generated
   - Avatar URL saved from Google
   - Password left empty
   - Role set to USER
   - Coin initialized to 0
   - Account marked as ACTIVE

2. **Subsequent Logins:**
   - Email lookup in database
   - If account is deactivated → reactivate
   - Generate new tokens
   - Update refresh token in database

3. **Account Management:**
   - Can activate/deactivate accounts
   - Can update profile (name, avatar)
   - Cannot change password (OAuth)

---

## 🧪 Testing

### Automated Tests
```bash
mvn test
```

### Manual Testing
1. **Swagger UI:** `http://localhost:8080/swagger-ui.html`
2. **cURL:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/login/google \
  -H "Content-Type: application/json" \
  -d '{"idToken": "..."}'
```
3. **Postman:** Import GOOGLE_OAUTH_API_SPEC.md examples

---

## 📋 Configuration Checklist

- [ ] Google Cloud Console project created
- [ ] OAuth 2.0 Client ID obtained from Google
- [ ] `GOOGLE_CLIENT_ID` environment variable set
- [ ] Dependencies installed: `mvn clean install`
- [ ] Application started: `mvn spring-boot:run`
- [ ] API verified in Swagger
- [ ] CORS allowed origins configured
- [ ] Frontend integration completed
- [ ] Error handling tested
- [ ] Token storage verified (localStorage)
- [ ] Subsequent API calls work with access token

---

## 🔍 Files by Category

### Presentation Layer
- `src/main/java/.../presentation/controllers/AuthController.java` (modified)

### Application Layer
- `src/main/java/.../application/dtos/requests/auth/GoogleLoginRequest.java` (new)
- `src/main/java/.../application/usecases/auth/GoogleLoginUseCase.java` (new)

### Domain Layer
- `src/main/java/.../domain/models/user/User.java` (modified)

### Configuration
- `src/main/resources/application.yml` (modified)
- `pom.xml` (modified)

### Testing
- `src/test/java/.../AuthControllerGoogleLoginTest.java` (new)

### Documentation
- `GOOGLE_OAUTH_SETUP.md` (new)
- `GOOGLE_LOGIN_QUICKSTART.md` (new)
- `GOOGLE_OAUTH_API_SPEC.md` (new)

---

## 🎯 What's Working Now

✅ Users can login with Google account
✅ Automatic user account creation
✅ JWT token generation
✅ Refresh token support
✅ User profile saved with Google data
✅ Account activation/deactivation
✅ Full API documentation (Swagger)
✅ Error handling and logging
✅ Unit tests included
✅ Security best practices implemented

---

## ⚙️ System Requirements

- Java 17+
- Spring Boot 4.0.1
- Maven 3.6+
- MySQL/H2 Database
- Google Cloud Account (for Client ID)

---

## 🚀 Next Steps

1. **Get Google Client ID**
   - Visit Google Cloud Console
   - Create OAuth 2.0 credentials
   - Copy Client ID

2. **Set Environment Variable**
   ```bash
   export GOOGLE_CLIENT_ID="your-client-id-here"
   ```

3. **Install & Run**
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

4. **Test in Swagger**
   - Open http://localhost:8080/swagger-ui.html
   - Find POST /api/v1/auth/login/google
   - Test with a real Google ID Token

5. **Integrate Frontend**
   - Use @react-oauth/google (for React)
   - Or google-signin-button (for other frameworks)
   - Send ID Token to backend endpoint

---

## 📚 Documentation Files

| File | Purpose |
|------|---------|
| `GOOGLE_OAUTH_SETUP.md` | Complete setup and configuration guide |
| `GOOGLE_LOGIN_QUICKSTART.md` | Quick start guide with examples |
| `GOOGLE_OAUTH_API_SPEC.md` | Detailed API specification and examples |

---

## 📞 Support

For issues or questions:
1. Check the documentation files
2. Review application logs: `logs/smart-money.log`
3. Enable debug logging in `application.yml`
4. Check Google Cloud Console audit logs

---

**Implementation Completed:** January 15, 2026
**Status:** ✅ Ready for Testing and Integration
