# Google OAuth vs Facebook OAuth - Comparison

## Architecture Comparison

### File Structure

#### Google OAuth
```
src/main/java/com/example/smartmoney/
├── application/
│   ├── dtos/requests/auth/
│   │   └── GoogleLoginRequest.java
│   └── usecases/auth/
│       └── GoogleLoginUseCase.java
├── domain/models/user/
│   └── User.java (has createGoogleUser())
└── presentation/controllers/
    └── AuthController.java (has googleLogin() endpoint)
```

#### Facebook OAuth
```
src/main/java/com/example/smartmoney/
├── application/
│   ├── dtos/requests/auth/
│   │   └── FacebookLoginRequest.java
│   └── usecases/auth/
│       └── FacebookLoginUseCase.java
├── domain/models/user/
│   └── User.java (has createFacebookUser())
├── infrastructure/config/
│   └── RestClientConfig.java (NEW for Facebook)
└── presentation/controllers/
    └── AuthController.java (has facebookLogin() endpoint)
```

## Detailed Comparison

### 1. Token Handling

| Aspect | Google OAuth | Facebook OAuth |
|--------|--------------|---|
| Token Type | ID Token (JWT) | Access Token |
| Token Format | `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...` | `EAAZ5ZA...` (opaque) |
| Verification Method | Google JWT Library | REST API call |
| Expiration | Typically 1 hour | Typically 60 days |
| Refresh Method | Use refresh token | Get new token from Facebook SDK |
| Library Used | `com.google.api.client` | Standard `RestTemplate` |

### 2. Request DTOs

#### GoogleLoginRequest
```java
@Data
public class GoogleLoginRequest {
    @NotBlank(message = "idToken is required")
    private String idToken;
    
    private String email;
    private String name;
    private String picture;
    private String givenName;
    private String familyName;
}
```

#### FacebookLoginRequest
```java
@Data
public class FacebookLoginRequest {
    @NotBlank(message = "accessToken is required")
    private String accessToken;
    
    private String email;
    private String name;
    private String picture;
    private String userId;
}
```

### 3. Token Verification

#### Google Verification
```java
// Using Google library
GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
    new NetHttpTransport(), 
    new GsonFactory())
    .setAudience(Collections.singletonList(googleClientId))
    .build();

GoogleIdToken idToken = verifier.verify(request.getIdToken());
```

#### Facebook Verification
```java
// Using REST API
String url = UriComponentsBuilder.fromHttpUrl("https://graph.facebook.com/me")
    .queryParam("fields", "id,name,email,picture")
    .queryParam("access_token", accessToken)
    .toUriString();

Map<String, Object> response = restTemplate.getForObject(url, Map.class);
```

### 4. Data Extraction

#### Google
```java
String email = (String) idToken.getPayload().get("email");
String name = (String) idToken.getPayload().get("name");
String picture = (String) idToken.getPayload().get("picture");
```

Simple direct extraction from JWT payload.

#### Facebook
```java
// Email and name are direct
String email = (String) userInfo.get("email");
String name = (String) userInfo.get("name");

// Picture is nested in response
@SuppressWarnings("unchecked")
Map<String, Object> pictureMap = (Map<String, Object>) userInfo.get("picture");
Map<String, Object> dataMap = (Map<String, Object>) pictureMap.get("data");
String picture = (String) dataMap.get("url");
```

Requires nested JSON traversal for picture.

### 5. API Endpoints

#### Google
```
Endpoint: POST /api/v1/auth/login/google
Method: GoogleLoginUseCase.execute(GoogleLoginRequest)
```

#### Facebook
```
Endpoint: POST /api/v1/auth/login/facebook
Method: FacebookLoginUseCase.execute(FacebookLoginRequest)
```

Both endpoints return identical `AuthResponse` structure.

### 6. Dependencies

#### Google Login
```java
// Dependencies
private final UserRepository userRepository;
private final AuthPort authPort;
private final GoogleIdTokenVerifier verifier; // From Google library

// Configuration
private String googleClientId;

// Libraries
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
```

#### Facebook Login
```java
// Dependencies
private final UserRepository userRepository;
private final AuthPort authPort;
private final RestTemplate restTemplate; // From Spring

// Configuration
private String facebookAppId;
private String facebookAppSecret;

// Libraries (built-in Spring)
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
```

### 7. Error Handling

| Error Type | Google | Facebook |
|-----------|--------|----------|
| Invalid Token | Caught by Google library | Check `error` field in response |
| Missing Email | Check if `null` from payload | Check if `null` from response |
| Verification Failed | Exception from library | HTTP error or error in response |
| User Creation | Standard DB exceptions | Standard DB exceptions |

### 8. Configuration Files

#### application.properties
```properties
# Google
app.google.oauth2.client-id=${APP_GOOGLE_CLIENT_ID}
app.google.oauth2.client-secret=${APP_GOOGLE_CLIENT_SECRET}

# Facebook
app.facebook.app-id=${APP_FACEBOOK_APP_ID}
app.facebook.app-secret=${APP_FACEBOOK_APP_SECRET}
```

#### application.yml
```yaml
app:
  google:
    oauth2:
      client-id: ${APP_GOOGLE_CLIENT_ID}
      client-secret: ${APP_GOOGLE_CLIENT_SECRET}
  
  facebook:
    app-id: ${APP_FACEBOOK_APP_ID}
    app-secret: ${APP_FACEBOOK_APP_SECRET}
```

### 9. User Creation

Both use similar factory methods:

```java
// Google
User newUser = User.createGoogleUser(username, email, name, picture);

// Facebook
User newUser = User.createFacebookUser(username, email, name, picture);
```

Both:
- Set SOCIAL_LOGIN as password hash
- Set 18 years as default date of birth
- Set default phone "0000000000"
- Set role as USER
- Set active = true

### 10. Controller Integration

#### AuthController Changes
```java
// Both imports
import com.example.smartmoney.application.usecases.auth.GoogleLoginUseCase;
import com.example.smartmoney.application.usecases.auth.FacebookLoginUseCase;

// Both fields
private final GoogleLoginUseCase googleLoginUseCase;
private final FacebookLoginUseCase facebookLoginUseCase;

// Both endpoints
@PostMapping("/login/google")
public ResponseEntity<CheckResponse<AuthResponse>> googleLogin(...)

@PostMapping("/login/facebook")
public ResponseEntity<CheckResponse<AuthResponse>> facebookLogin(...)
```

## Implementation Differences Summary

### Google OAuth
✅ **Advantages**:
- Uses official Google JWT library
- Direct payload extraction (no nested JSON)
- No HTTP call needed for verification
- Faster verification

❌ **Disadvantages**:
- Requires Google library dependency
- Client ID needed for verification
- JWT library only for Google

### Facebook OAuth
✅ **Advantages**:
- No external library needed (uses Spring RestTemplate)
- Works with REST API
- More flexible for future changes
- Can verify token validity on backend

❌ **Disadvantages**:
- Requires HTTP call to verify
- Slightly slower due to API call
- Picture extraction requires nested JSON parsing
- Need app secret (should never reach frontend)

## Response Structure (Identical)

Both Google and Facebook logins return the same response:

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "JWT_TOKEN",
    "refreshToken": "REFRESH_TOKEN",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": 1,
      "username": "user_xyz123",
      "fullName": "User Name",
      "email": "user@example.com",
      "avatar": "URL",
      "dateOfBirth": "01/01/2006",
      "phone": "0000000000",
      "active": true,
      "role": "USER",
      "coin": 0
    }
  }
}
```

## Configuration Comparison

| Aspect | Google | Facebook |
|--------|--------|----------|
| Env Variables | APP_GOOGLE_CLIENT_ID, APP_GOOGLE_CLIENT_SECRET | APP_FACEBOOK_APP_ID, APP_FACEBOOK_APP_SECRET |
| Config Class | None (used directly in UseCase) | RestClientConfig (for RestTemplate bean) |
| Constructor Args | googleClientId only | facebookAppId, facebookAppSecret |
| Bean Requirements | None | RestTemplate bean |

## Testing Comparison

### Google Testing
```bash
# Get ID token from Google OAuth flow
curl -X POST http://localhost:8080/api/v1/auth/login/google \
  -H "Content-Type: application/json" \
  -d '{
    "idToken": "eyJhbGc...",
    "email": "user@gmail.com"
  }'
```

### Facebook Testing
```bash
# Get access token from Facebook OAuth SDK
curl -X POST http://localhost:8080/api/v1/auth/login/facebook \
  -H "Content-Type: application/json" \
  -d '{
    "accessToken": "EAAZ5...",
    "email": "user@example.com"
  }'
```

## Security Comparison

| Security Aspect | Google | Facebook |
|---------|--------|----------|
| Secret Exposure | ID token contains user data | Access token is opaque, secret needed |
| Backend Verification | JWT signature check | API call verification |
| Token Storage | Frontend can store JWT | Frontend stores opaque token |
| Scope Permissions | Defined in Google API | Defined in Facebook app settings |
| HTTPS Requirement | Recommended | Required for token validation |

## Migration Path

If you want to add more OAuth providers (GitHub, Microsoft, etc.):

1. Create `{Provider}LoginRequest.java` DTO
2. Create `{Provider}LoginUseCase.java` with verification logic
3. Add `create{Provider}User()` method to User model
4. Add endpoint to AuthController
5. Add configuration to properties files
6. Update RestTemplate if using REST API verification

## Performance Comparison

| Operation | Google | Facebook | Notes |
|-----------|--------|----------|-------|
| Token Verification | ~10ms | ~100-500ms | Google is local, Facebook requires API call |
| User Lookup | Same | Same | Database query |
| Token Generation | Same | Same | JWT generation |
| Total Time | ~100ms | ~200-600ms | Facebook may be slower due to API call |

## Recommended Best Practices

### For Both Google and Facebook

1. **Never expose secrets** to frontend
2. **Always verify tokens** on backend
3. **Use HTTPS** in production
4. **Set proper CORS** headers
5. **Log all authentication** events
6. **Monitor failed** logins
7. **Cache user data** appropriately
8. **Implement rate limiting** on auth endpoints
9. **Use secure token storage** on frontend
10. **Refresh tokens regularly** for security

### Specific to Google
- Use official Google library for JWT verification
- Cache verified tokens briefly to reduce verification time

### Specific to Facebook
- Request minimal required permissions
- Handle nested JSON responses carefully
- Be prepared for API rate limits
- Use query parameter encoding for Graph API calls

## Conclusion

Both implementations follow the same architecture pattern and return identical responses. The main differences are:

- **Verification Method**: Google uses JWT library, Facebook uses REST API
- **Data Structure**: Google has flat payload, Facebook has nested picture data
- **Dependencies**: Google needs external library, Facebook uses Spring's RestTemplate
- **Performance**: Google is slightly faster due to local verification

Choose based on your user base and requirements:
- **Use Google OAuth** if: Most users have Google accounts, faster verification needed
- **Use Facebook OAuth** if: Targeting Facebook user base, want flexibility
- **Use Both** if: Want maximum reach and flexibility (recommended)
