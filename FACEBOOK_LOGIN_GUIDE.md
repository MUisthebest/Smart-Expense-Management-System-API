# Facebook Login Integration Guide

## Overview
This guide explains how to integrate Facebook OAuth login into the Smart Money API.

## Architecture
Facebook login follows the same structure as Google OAuth login:
- **Request DTO**: `FacebookLoginRequest.java` - Contains the Facebook access token
- **Use Case**: `FacebookLoginUseCase.java` - Handles Facebook token verification and user creation
- **Controller**: `AuthController.java` - Exposes the `/api/v1/auth/login/facebook` endpoint
- **Domain Model**: Enhanced `User.java` with `createFacebookUser()` factory method

## Files Created/Modified

### New Files
1. **FacebookLoginRequest.java** - DTO for Facebook login request
   - Location: `src/main/java/com/example/smartmoney/application/dtos/requests/auth/`
   - Fields:
     - `accessToken` (required): Facebook access token from frontend
     - `email` (optional): User email from Facebook
     - `name` (optional): User name from Facebook
     - `picture` (optional): User profile picture URL
     - `userId` (optional): Facebook user ID

2. **FacebookLoginUseCase.java** - Business logic for Facebook authentication
   - Location: `src/main/java/com/example/smartmoney/application/usecases/auth/`
   - Features:
     - Verifies Facebook access token via Facebook Graph API
     - Extracts user information (email, name, picture)
     - Finds or creates user in database
     - Generates JWT tokens (access & refresh)
     - Returns authenticated user information

### Modified Files
1. **AuthController.java**
   - Added `FacebookLoginUseCase` dependency injection
   - Added new endpoint: `POST /api/v1/auth/login/facebook`
   - Added `FacebookLoginRequest` import

2. **User.java**
   - Added `createFacebookUser()` factory method
   - Creates user with social login profile

3. **application.properties**
   - Added Facebook configuration properties:
     ```
     app.facebook.app-id=${APP_FACEBOOK_APP_ID}
     app.facebook.app-secret=${APP_FACEBOOK_APP_SECRET}
     ```

4. **application.yml**
   - Added Facebook OAuth configuration section

## Configuration

### Environment Variables
Set the following environment variables:
```bash
export APP_FACEBOOK_APP_ID="your_facebook_app_id"
export APP_FACEBOOK_APP_SECRET="your_facebook_app_secret"
```

### Facebook App Setup
1. Go to [Facebook Developer Console](https://developers.facebook.com)
2. Create or select your Facebook App
3. Add "Facebook Login" product
4. Configure OAuth Redirect URIs
5. Get your App ID and App Secret

## API Endpoint

### Facebook Login
**Endpoint:** `POST /api/v1/auth/login/facebook`

**Request:**
```json
{
  "accessToken": "facebook_access_token_here",
  "email": "user@example.com",
  "name": "User Name",
  "picture": "https://platform-lookaside.fbsbx.com/...",
  "userId": "123456789"
}
```

**Response (Success - 200):**
```json
{
  "success": true,
  "message": "Facebook login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "refresh_token_here",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "user": {
      "id": 1,
      "username": "user_abc123de",
      "fullName": "User Name",
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

**Response (Error - 401):**
```json
{
  "success": false,
  "message": "Facebook authentication failed: Invalid Facebook access token"
}
```

## Flow Diagram

```
Frontend (React/Vue)
        |
        | POST /login/facebook + accessToken
        |
        v
AuthController.facebookLogin()
        |
        | Call FacebookLoginUseCase.execute()
        |
        v
FacebookLoginUseCase
        |
        +-> Verify token via Facebook Graph API
        |
        +-> Extract user data (email, name, picture)
        |
        +-> Find user by email
        |
        +-> If not found: Create new user via User.createFacebookUser()
        |
        +-> Generate JWT tokens (access + refresh)
        |
        +-> Save refresh token to database
        |
        v
Return AuthResponse with tokens
        |
        |
        v
Frontend (Store tokens in localStorage/sessionStorage)
```

## Token Verification Process

1. **Access Token Verification**: Frontend sends access token to backend
2. **Graph API Call**: Backend calls `https://graph.facebook.com/me` with the access token
3. **User Data Extraction**: Backend parses response to get:
   - `id`: Facebook user ID
   - `email`: User email address
   - `name`: User full name
   - `picture`: Profile picture URL (nested in response)
4. **User Lookup/Creation**: Find existing user by email or create new one
5. **Token Generation**: Create JWT tokens and send back to frontend

## Error Handling

| Error | Cause | Solution |
|-------|-------|----------|
| Invalid access token | Token expired or incorrect | Re-authenticate with Facebook |
| Email not found | User didn't grant email permission | Request email permission in Facebook login dialog |
| Failed to create user | Database or validation error | Check database and user constraints |
| Facebook API error | Network or API issue | Check Facebook Graph API status |

## Security Considerations

1. **Token Validation**: Always verify access token on the server side
2. **HTTPS Only**: Use HTTPS in production
3. **CORS Policy**: Configure allowed origins properly
4. **Token Storage**: Frontend should use secure storage (not localStorage for sensitive tokens)
5. **Scope Permissions**: Request minimal required permissions from Facebook
6. **Refresh Token**: Never expose refresh tokens in response; store securely on backend

## Frontend Integration Example (JavaScript)

```javascript
async function loginWithFacebook(accessToken, userData) {
  const response = await fetch('/api/v1/auth/login/facebook', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      accessToken: accessToken,
      email: userData.email,
      name: userData.name,
      picture: userData.picture.data.url,
      userId: userData.id
    })
  });

  if (!response.ok) {
    throw new Error('Facebook login failed');
  }

  const data = await response.json();
  
  // Store tokens
  localStorage.setItem('accessToken', data.data.accessToken);
  localStorage.setItem('refreshToken', data.data.refreshToken);
  
  return data.data.user;
}
```

## Testing

### Manual Testing
1. Create a Facebook app in [Facebook Developer Console](https://developers.facebook.com)
2. Use Facebook Login Dialog to get an access token
3. Test endpoint with curl:
```bash
curl -X POST http://localhost:8080/api/v1/auth/login/facebook \
  -H "Content-Type: application/json" \
  -d '{
    "accessToken": "your_facebook_access_token",
    "email": "user@example.com",
    "name": "Test User",
    "picture": "https://example.com/avatar.jpg",
    "userId": "123456"
  }'
```

### Unit Testing
Create test class: `FacebookLoginUseCaseTest.java`
- Mock Facebook API responses
- Test token verification
- Test user creation
- Test error handling

## Troubleshooting

### Issue: "Invalid access token"
- **Cause**: Token has expired or is incorrect
- **Solution**: Refresh token on frontend and retry

### Issue: "Email not found in Facebook profile"
- **Cause**: User hasn't granted email permission
- **Solution**: Update Facebook app permissions to request email scope

### Issue: "RestTemplate not found"
- **Cause**: Spring's RestTemplate not configured
- **Solution**: Add `@Bean` configuration in a config class:
```java
@Bean
public RestTemplate restTemplate() {
    return new RestTemplate();
}
```

## Comparison with Google Login

| Feature | Google | Facebook |
|---------|--------|----------|
| Token Type | ID Token (JWT) | Access Token |
| Verification | Google Library | HTTP REST call |
| API Endpoint | Google OAuth 2.0 library | Facebook Graph API |
| User Data | Extracted from JWT | From Graph API response |
| Picture Format | Direct URL | Nested JSON structure |
| Default Fields | email, name, picture | id, email, name, picture |

## Next Steps

1. Configure Facebook App credentials in environment
2. Test the endpoint with a real Facebook token
3. Implement frontend Facebook login button
4. Add email permission request in Facebook login dialog
5. Monitor and log authentication events
6. Set up error tracking for failed logins

## References

- [Facebook Graph API Documentation](https://developers.facebook.com/docs/graph-api)
- [Facebook Login Documentation](https://developers.facebook.com/docs/facebook-login)
- [Facebook App Setup Guide](https://developers.facebook.com/docs/development/create-an-app)
