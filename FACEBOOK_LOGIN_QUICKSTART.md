# Facebook Login - Quick Start Guide

## 📋 Files Created

```
src/main/java/com/example/smartmoney/
├── application/
│   ├── dtos/
│   │   └── requests/auth/
│   │       └── FacebookLoginRequest.java (NEW)
│   └── usecases/auth/
│       └── FacebookLoginUseCase.java (NEW)
├── infrastructure/
│   └── config/
│       └── RestClientConfig.java (NEW)
└── presentation/controllers/
    └── AuthController.java (MODIFIED)

Domain Model:
└── domain/models/user/User.java (MODIFIED - added createFacebookUser)

Configuration:
├── application.properties (MODIFIED)
└── application.yml (MODIFIED)

Documentation:
├── FACEBOOK_LOGIN_GUIDE.md (NEW)
└── FACEBOOK_LOGIN_IMPLEMENTATION.md (NEW)
```

## 🚀 Setup Steps

### 1. Set Environment Variables
```bash
# On Linux/macOS
export APP_FACEBOOK_APP_ID="your_app_id_here"
export APP_FACEBOOK_APP_SECRET="your_app_secret_here"

# On Windows PowerShell
$env:APP_FACEBOOK_APP_ID="your_app_id_here"
$env:APP_FACEBOOK_APP_SECRET="your_app_secret_here"
```

### 2. Facebook App Configuration
1. Go to [Facebook Developers](https://developers.facebook.com)
2. Create a new app or select existing one
3. Add "Facebook Login" product
4. Configure OAuth Redirect URIs:
   - Add your frontend URL (e.g., `http://localhost:3000`)
   - Add callback URL
5. Copy App ID and App Secret

### 3. Build & Run
```bash
# Build with Maven
mvn clean install

# Run the application
mvn spring-boot:run

# Or run the JAR
java -jar target/smartmoney-0.0.1-SNAPSHOT.jar
```

## 🔐 API Usage

### Endpoint
```
POST /api/v1/auth/login/facebook
Content-Type: application/json
```

### Request
```json
{
  "accessToken": "EAAZ5ZA...",
  "email": "user@example.com",
  "name": "John Doe",
  "picture": "https://platform-lookaside.fbsbx.com/...",
  "userId": "123456789"
}
```

### Response (Success)
```json
{
  "success": true,
  "message": "Facebook login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": 1,
      "username": "john_abc123de",
      "fullName": "John Doe",
      "email": "user@example.com",
      "avatar": "https://platform-lookaside.fbsbx.com/...",
      "dateOfBirth": "01/01/2006",
      "phone": "0000000000",
      "active": true,
      "role": "USER",
      "coin": 0
    }
  }
}
```

## 📋 File Structure Explanation

### FacebookLoginRequest.java
- **Purpose**: Data transfer object for Facebook login requests
- **Fields**: 
  - `accessToken` (required): Token from Facebook SDK
  - `email`, `name`, `picture`, `userId` (optional): User data from Facebook
- **Validation**: `accessToken` must not be blank

### FacebookLoginUseCase.java
- **Purpose**: Core business logic for Facebook authentication
- **Main Methods**:
  - `execute()`: Main method to authenticate user
  - `verifyFacebookToken()`: Calls Facebook Graph API to verify token
  - `extractPictureUrl()`: Extracts picture URL from nested JSON response
  - `createUserFromFacebook()`: Creates new user if not exists
  - `mapUserToResponse()`: Converts User entity to response DTO
- **Dependencies**: UserRepository, AuthPort, RestTemplate

### RestClientConfig.java
- **Purpose**: Provides RestTemplate bean for HTTP calls
- **Usage**: Used by FacebookLoginUseCase to call Facebook Graph API

### User.java (Modified)
- **New Method**: `createFacebookUser()`
- **Purpose**: Factory method to create users from Facebook OAuth
- **Behavior**: Same as Google users (social login profile)

## 🔑 Key Differences from Google Login

| Aspect | Google | Facebook |
|--------|--------|----------|
| Token Format | JWT ID Token | Access Token |
| Verification | Google's JWT Library | HTTP to Graph API |
| API Used | Google OAuth 2.0 | Facebook Graph API |
| Picture URL | Direct string | Nested in JSON object |
| Request Fields | `idToken` | `accessToken` |

## 🧪 Testing with cURL

```bash
# Test successful login
curl -X POST http://localhost:8080/api/v1/auth/login/facebook \
  -H "Content-Type: application/json" \
  -d '{
    "accessToken": "YOUR_FACEBOOK_ACCESS_TOKEN",
    "email": "test@example.com",
    "name": "Test User",
    "picture": "https://example.com/avatar.jpg",
    "userId": "123456"
  }'

# Test with invalid token (should return 401)
curl -X POST http://localhost:8080/api/v1/auth/login/facebook \
  -H "Content-Type: application/json" \
  -d '{
    "accessToken": "invalid_token"
  }'
```

## 📚 Swagger Documentation

Once the app is running, view API docs at:
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

The Facebook login endpoint will be documented with:
- Request/response schemas
- Example data
- Error responses

## ✅ Verification Checklist

After setup, verify:
- [ ] Environment variables are set correctly
- [ ] Application starts without errors
- [ ] Swagger UI displays new endpoint
- [ ] Test endpoint with valid Facebook token
- [ ] Verify user is created/updated in database
- [ ] Check JWT tokens are generated
- [ ] Verify refresh token functionality
- [ ] Test error handling with invalid tokens

## 🐛 Troubleshooting

### Issue: "Could not autowire field: facebookLoginUseCase"
**Solution**: Ensure FacebookLoginUseCase is properly annotated with `@Service` and located in correct package.

### Issue: "RestTemplate not found"
**Solution**: RestClientConfig.java should be created and Spring should scan it. Check if it's in the correct package under `config`.

### Issue: "Invalid or malformed JSON from Facebook"
**Solution**: Verify that the `accessToken` is valid and has the required permissions (email, public_profile).

### Issue: "Email not found in Facebook profile"
**Solution**: User needs to grant email permission when logging in to your app on Facebook.

## 📖 Additional Resources

- [Complete Implementation Guide](FACEBOOK_LOGIN_IMPLEMENTATION.md)
- [Detailed Setup Instructions](FACEBOOK_LOGIN_GUIDE.md)
- [Facebook Graph API Docs](https://developers.facebook.com/docs/graph-api)
- [Facebook Login Documentation](https://developers.facebook.com/docs/facebook-login)

## 🔄 Flow Diagram

```
User Frontend
     ↓
Facebook Login SDK
     ↓
Get Access Token
     ↓
Send to Backend
POST /api/v1/auth/login/facebook
     ↓
FacebookLoginUseCase
     ↓
Verify Token via Graph API
     ↓
Extract User Data
     ↓
Find/Create User
     ↓
Generate JWT Tokens
     ↓
Return Auth Response
     ↓
Frontend Stores Tokens
```

## 💡 Next Steps

1. **Frontend Integration**: Implement Facebook Login button in your React/Vue app
2. **Token Management**: Store access token securely on frontend
3. **Auto-Refresh**: Implement token refresh mechanism
4. **Error Handling**: Add proper error messages for users
5. **Analytics**: Track login events and success rates
6. **Security**: Review and implement additional security measures

## 📝 Notes

- Both Google and Facebook login are now available
- Users can use either method to authenticate
- Same user account is retrieved if email matches
- Refresh tokens are stored securely in database
- Access tokens have 1-hour expiration (configurable)
