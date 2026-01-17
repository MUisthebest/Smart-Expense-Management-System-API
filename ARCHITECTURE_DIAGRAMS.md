# Google OAuth Architecture Diagram

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Frontend (React/Vue)                            │
│                                                                              │
│  ┌─────────────────────────┐                                               │
│  │   Google OAuth Button    │                                               │
│  │  (react-oauth/google)    │                                               │
│  └────────────┬─────────────┘                                               │
│               │                                                              │
│               ▼ (User clicks)                                                │
│  ┌─────────────────────────┐         ┌────────────────────────┐            │
│  │ Google Consent Dialog   │ ◄──────► │  Google OAuth Server    │            │
│  │ (oauth.google.com)      │         │  (accounts.google.com) │            │
│  └────────────┬─────────────┘         └────────────────────────┘            │
│               │ (User authorizes)                                             │
│               ▼                                                               │
│  ┌─────────────────────────┐                                               │
│  │  Receive ID Token       │                                               │
│  │  + User Data            │                                               │
│  └────────────┬─────────────┘                                               │
│               │                                                              │
│               │ idToken                                                      │
│               │                                                              │
└───────────────┼──────────────────────────────────────────────────────────────┘
                │
                │ POST /api/v1/auth/login/google
                │ Content-Type: application/json
                │ {
                │   "idToken": "eyJhbGciOiJSUzI1NiIs..."
                │ }
                │
                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          Spring Boot Backend                                │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ AuthController (Presentation Layer)                                │  │
│  │                                                                     │  │
│  │ @PostMapping("/login/google")                                      │  │
│  │ public ResponseEntity<CheckResponse> googleLogin(                  │  │
│  │     GoogleLoginRequest request)                                    │  │
│  └────────────────────────┬────────────────────────────────────────────┘  │
│                           │                                                  │
│                           ▼                                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ GoogleLoginUseCase (Application Layer)                              │  │
│  │                                                                     │  │
│  │ execute(GoogleLoginRequest request) {                              │  │
│  │   1. Verify ID Token with Google                                   │  │
│  │   2. Extract: email, name, picture                                 │  │
│  │   3. Find user by email                                            │  │
│  │   4. If not found: Create new user                                 │  │
│  │   5. Generate JWT tokens                                           │  │
│  │   6. Save refresh token in DB                                      │  │
│  │   7. Return AuthResponse                                           │  │
│  │ }                                                                   │  │
│  └────────────┬──────────────┬──────────────┬──────────────────────────┘  │
│               │              │              │                                │
│               ▼              ▼              ▼                                │
│  ┌──────────────────┐ ┌──────────────┐ ┌──────────────────┐               │
│  │UserRepository    │ │AuthPort      │ │GoogleIdToken     │               │
│  │(Domain Layer)    │ │(Port/DAO)    │ │Verifier          │               │
│  │                  │ │              │ │(Google Lib)      │               │
│  │-findByEmail()    │ │-generateToken│ │                  │               │
│  │-save()           │ │-validatePwd()│ │-verify()         │               │
│  └────────┬─────────┘ └──────────────┘ └────────┬─────────┘               │
│           │                                      │                          │
│           │ Looks up                             │ Calls back                │
│           │ or creates                           │                          │
│           ▼                                      ▼                          │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ User Domain Model                                                    │  │
│  │                                                                     │  │
│  │ User {                                                              │  │
│  │   - id, username, fullName                                         │  │
│  │   - email (Email VO)                                               │  │
│  │   - avatar (Avatar VO)                                             │  │
│  │   - password (Empty for OAuth users)                               │  │
│  │   - refreshToken                                                   │  │
│  │   - role, coin, rate, active                                       │  │
│  │ }                                                                   │  │
│  │                                                                     │  │
│  │ Factory: createGoogleUser(username, email, fullName, avatar)       │  │
│  └────────────────┬─────────────────────────────────────────────────┘  │
│                   │                                                      │
└───────────────────┼──────────────────────────────────────────────────────┘
                    │
                    ▼
         ┌──────────────────────┐
         │   Database (H2/MySQL)│
         │                      │
         │ users table          │
         │ - id                 │
         │ - email              │
         │ - username           │
         │ - fullName           │
         │ - avatar             │
         │ - refreshToken       │
         │ - active             │
         └──────────────────────┘
```

---

## Request-Response Flow

### Detailed Request/Response Cycle

```
┌────────────────────────────────────────────────────────────────┐
│ STEP 1: Frontend sends Google ID Token                         │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│ HTTP Request:                                                   │
│ POST /api/v1/auth/login/google                                 │
│ Content-Type: application/json                                 │
│                                                                 │
│ {                                                               │
│   "idToken": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjdiZjU5NTQ..."    │
│ }                                                               │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│ STEP 2: Backend Receives & Validates Request                   │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│ 1. AuthController.googleLogin() called                          │
│ 2. GoogleLoginRequest validated (@Valid)                        │
│    - idToken must not be blank                                 │
│ 3. GoogleLoginUseCase.execute() called                          │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│ STEP 3: Verify Google ID Token                                 │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│ GoogleIdTokenVerifier verifies token with Google:              │
│ 1. Check token signature with Google's public keys            │
│ 2. Verify token not expired                                    │
│ 3. Verify audience matches clientId                            │
│ 4. Extract payload (email, name, picture, etc)                │
│                                                                 │
│ IF verification fails → throw DomainException                  │
│ ELSE → Continue to Step 4                                      │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│ STEP 4: Extract User Data from Token                           │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│ From idToken payload extract:                                  │
│ - email: "moik5985@gmail.com"                                  │
│ - name: "12T2-04 Ngô Hải Bằng"                                 │
│ - picture: "https://lh3.googleusercontent.com/a/AC..."         │
│ - email_verified: true                                         │
│ - sub: "110844682102203234349"                                 │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│ STEP 5: Check if User Exists in Database                       │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│ UserRepository.findByEmail(email)                              │
│                                                                 │
│ ┌─ User Found ──┐           ┌─ User Not Found ──┐             │
│ │               │           │                   │              │
│ ▼               ▼           ▼                   ▼              │
│ Use existing   Check if   Create new user    Set default:      │
│ user data      active      account with:       - username: auto │
│                                                 - email: from    │
│ If not active,            - email             Google           │
│ reactivate     - fullName                     - fullName:      │
│ account        - avatar                       from Google      │
│                - role: USER                   - avatar: from   │
│                - coin: 0                      Google           │
│                - rate: 5.0                    - role: USER     │
│                - active: true                 - coin: 0        │
│                                               - active: true   │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│ STEP 6: Generate JWT Tokens                                    │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│ 1. Access Token (1 hour expiration):                           │
│    - Header: {alg: "HS256", typ: "JWT"}                        │
│    - Payload: {sub: userId, email, iat, exp}                  │
│    - Signature: HMACSHA256(secret)                             │
│                                                                 │
│ 2. Refresh Token (7 days expiration):                          │
│    - Same structure as access token                            │
│    - Longer expiration period                                  │
│                                                                 │
│ Example Access Token:                                          │
│ eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwia                        │
│ WF0IjoxNzY4NDIxMTIzLCJleHAiOjE3Njg0MjQ3MjN9.abc             │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│ STEP 7: Save Refresh Token in Database                         │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│ user.updateRefreshToken(                                       │
│   new RefreshToken(refreshToken, expirationDate)              │
│ );                                                              │
│                                                                 │
│ userRepository.save(user);                                     │
│ → Updates user record in database                              │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│ STEP 8: Build & Return Response                                │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│ HTTP 200 OK                                                    │
│ Content-Type: application/json                                 │
│                                                                 │
│ {                                                               │
│   "success": true,                                             │
│   "message": "Google login successful",                        │
│   "data": {                                                     │
│     "accessToken": "eyJhbGc...",                               │
│     "refreshToken": "eyJhbGc...",                              │
│     "tokenType": "Bearer",                                     │
│     "expiresIn": 3600,                                         │
│     "user": {                                                   │
│       "id": 1,                                                  │
│       "username": "moik5985_a1b2c3d4",                         │
│       "fullName": "12T2-04 Ngô Hải Bằng",                      │
│       "email": "moik5985@gmail.com",                           │
│       "avatar": "https://...",                                 │
│       "role": "USER",                                          │
│       "coin": 0,                                               │
│       "active": true                                           │
│     }                                                           │
│   }                                                             │
│ }                                                               │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
                             │
                             ▼
┌────────────────────────────────────────────────────────────────┐
│ STEP 9: Frontend Receives Response                             │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│ 1. Extract accessToken                                         │
│ 2. Extract refreshToken                                        │
│ 3. Store in localStorage                                       │
│ 4. Store user info in state/localStorage                       │
│ 5. Redirect to /dashboard                                      │
│                                                                 │
│ localStorage = {                                               │
│   accessToken: "eyJhbGc...",                                   │
│   refreshToken: "eyJhbGc...",                                  │
│   user: { id: 1, email: "..." }                                │
│ }                                                               │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

---

## Class Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│                    GoogleLoginRequest (DTO)                      │
├──────────────────────────────────────────────────────────────────┤
│ - idToken: String (required)                                     │
│ - email: String (optional)                                       │
│ - name: String (optional)                                        │
│ - picture: String (optional)                                     │
│ - givenName: String (optional)                                   │
│ - familyName: String (optional)                                  │
├──────────────────────────────────────────────────────────────────┤
│ + GoogleLoginRequest()                                           │
│ + getters/setters                                                │
└──────────────────────────────────────────────────────────────────┘
                             △
                             │ extends
                             │
┌──────────────────────────────────────────────────────────────────┐
│                  GoogleLoginUseCase (Service)                    │
├──────────────────────────────────────────────────────────────────┤
│ - userRepository: UserRepository                                 │
│ - authPort: AuthPort                                             │
│ - verifier: GoogleIdTokenVerifier                                │
│ - googleClientId: String                                         │
├──────────────────────────────────────────────────────────────────┤
│ + execute(GoogleLoginRequest): AuthResponse                      │
│ - createUserFromGoogle(email, name, picture): User              │
│ - mapUserToResponse(user): UserResponse                         │
└──────────────────────────────────────────────────────────────────┘
         │                      │                    │
         │ uses                 │ uses               │ uses
         │                      │                    │
         ▼                      ▼                    ▼
    ┌─────────────┐      ┌────────────┐      ┌──────────────┐
    │UserRepository│      │ AuthPort   │      │GoogleIdToken │
    │(Repository) │      │(Port/DAO)  │      │Verifier      │
    └─────────────┘      └────────────┘      └──────────────┘
         │                      │
         │ works with           │ uses
         │                      │
         ▼                      ▼
    ┌────────────────────────────────────┐
    │       User (Domain Model)          │
    ├────────────────────────────────────┤
    │ - id: Long                         │
    │ - username: String                 │
    │ - fullName: String                 │
    │ - email: Email (VO)                │
    │ - avatar: Avatar (VO)              │
    │ - password: Password (VO)          │
    │ - role: Role (enum)                │
    │ - coin: Integer                    │
    │ - rate: Double                     │
    │ - refreshToken: RefreshToken (VO) │
    │ - active: boolean                  │
    ├────────────────────────────────────┤
    │ + static create(...): User         │
    │ + static createGoogleUser(...): User
    │ + activate(): void                 │
    │ + deactivate(): void               │
    │ + updateRefreshToken(...): void    │
    └────────────────────────────────────┘
              △
              │ contains
              │
    ┌────────────────────────────────────┐
    │     AuthResponse (DTO)             │
    ├────────────────────────────────────┤
    │ - accessToken: String              │
    │ - refreshToken: String             │
    │ - tokenType: String                │
    │ - expiresIn: Long                  │
    │ - user: UserResponse               │
    └────────────────────────────────────┘
```

---

## Database Schema (User Table)

```
users
┌──────────────────┬──────────────┬─────────┐
│ Column           │ Type         │ Notes   │
├──────────────────┼──────────────┼─────────┤
│ id               │ BIGINT       │ PK      │
│ username         │ VARCHAR(20)  │ NOT NULL│
│ full_name        │ VARCHAR(40)  │ NOT NULL│
│ email            │ VARCHAR(30)  │ UNIQUE  │
│ avatar_url       │ VARCHAR(255) │ NULL    │
│ password_hash    │ VARCHAR(255) │ NULL    │
│ phone            │ VARCHAR(20)  │ NULL    │
│ date_of_birth    │ DATE         │ NULL    │
│ role             │ VARCHAR(20)  │ DEFAULT │
│ coin             │ INTEGER      │ DEFAULT 0
│ rate             │ DOUBLE       │ DEFAULT 5.0
│ refresh_token    │ VARCHAR(512) │ NULL    │
│ active           │ BOOLEAN      │ DEFAULT true
│ created_at       │ TIMESTAMP    │ NOT NULL│
│ updated_at       │ TIMESTAMP    │ NOT NULL│
└──────────────────┴──────────────┴─────────┘
```

---

## Data Flow: New User Creation

```
Google Token
    │
    ▼
Extract email: "moik5985@gmail.com"
    │
    ▼
Check database for existing user
    │
    ├─── User exists ──► Use existing user
    │
    └─── User not found
         │
         ▼
    Generate username: "moik5985_a1b2c3d4"
    (from email prefix + random UUID)
         │
         ▼
    Create new User:
    - username: "moik5985_a1b2c3d4"
    - email: "moik5985@gmail.com"
    - fullName: "12T2-04 Ngô Hải Bằng"
    - avatar: "https://lh3.googleusercontent.com/..."
    - password: "" (empty)
    - role: USER
    - coin: 0
    - rate: 5.0
    - active: true
         │
         ▼
    Save to database
         │
         ▼
    Return user object
```

---

## Error Handling Flow

```
GoogleLoginUseCase.execute()
    │
    ▼
Verify ID Token
    │
    ├─── Invalid token ──► throw DomainException
    │                      "Invalid Google ID token"
    │
    └─── Valid token
         │
         ▼
    Extract user data
         │
         ├─── Email missing ──► throw DomainException
         │                      "Email not found in Google token"
         │
         └─── Email found
              │
              ▼
         Check user exists
              │
              ├─── Exists & inactive ──► Reactivate
              │
              └─── Does not exist ──► Create
              │
              ▼
         Generate tokens
              │
              ├─── Token generation fails ──► throw Exception
              │
              └─── Success
                   │
                   ▼
         Return AuthResponse
         
         
Catch all exceptions in Controller:
    │
    ▼
Return 401 Unauthorized with error message
```

---

This visual architecture helps understand:
- How components interact
- Data flow through the system
- Request/response cycle
- Error handling paths
- Database schema
- Class relationships
