# Facebook Login - Frontend Integration Guide

## 🎯 Overview
This guide explains how to integrate Facebook OAuth login on the frontend to work with the Smart Money API backend.

## 📦 Frontend Setup

### 1. Install Facebook SDK

#### For React
```bash
npm install react-facebook-login
# or
npm install @react-oauth/facebook
```

#### For Vue
```bash
npm install vue-facebook-sdk
```

#### For Vanilla JavaScript
```html
<script async defer crossorigin="anonymous" 
  src="https://connect.facebook.net/en_US/sdk.js#xfbml=1&version=v18.0&appId=YOUR_APP_ID&cookie=true" 
  nonce="XXXXXXXX">
</script>
```

### 2. Initialize Facebook SDK

#### React (Using @react-oauth/facebook)
```javascript
import { FacebookOAuthProvider, FacebookLogin } from '@react-oauth/facebook';

function App() {
  return (
    <FacebookOAuthProvider appId="YOUR_APP_ID">
      <YourAuthComponent />
    </FacebookOAuthProvider>
  );
}
```

#### React (Using react-facebook-login)
```javascript
import FacebookLogin from 'react-facebook-login/dist/facebook-login-render-props';

function LoginComponent() {
  return (
    <FacebookLogin
      appId="YOUR_APP_ID"
      fields="name,email,picture"
      callback={handleFacebookResponse}
      plugin_url="https://connect.facebook.net/en_US/sdk.js"
    />
  );
}
```

#### Vue
```javascript
import { createApp } from 'vue';
import App from './App.vue';

const app = createApp(App);

app.config.globalProperties.$facebook = window.FB;

app.mount('#app');
```

## 🔐 Facebook App Configuration

### 1. Create Facebook App
1. Go to [Facebook Developers](https://developers.facebook.com)
2. Click "Create App"
3. Choose "Business" type
4. Fill in app details

### 2. Add Facebook Login Product
1. Go to "Add a Product"
2. Search for "Facebook Login"
3. Click "Set Up"
4. Choose "Web"

### 3. Configure Settings
```
Settings > Basic
- Copy your App ID
- Copy your App Secret (never expose to frontend!)

Settings > Facebook Login > Settings
- Add Valid OAuth Redirect URIs:
  - http://localhost:3000/auth/facebook/callback (dev)
  - https://yourdomain.com/auth/facebook/callback (prod)

App Roles
- Add your email as App Tester or higher
```

### 4. Configure Permissions
```
Facebook Login > Tools
- Request email permission
- Request public_profile permission
- Configure other required permissions
```

## 💻 Implementation Examples

### React Implementation (Class Component)

```javascript
import React from 'react';
import FacebookLogin from 'react-facebook-login/dist/facebook-login-render-props';

class AuthService {
  async loginWithFacebook(response) {
    try {
      const apiResponse = await fetch('/api/v1/auth/login/facebook', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          accessToken: response.accessToken,
          email: response.email,
          name: response.name,
          picture: response.picture?.data?.url,
          userId: response.userID,
        }),
      });

      if (!apiResponse.ok) {
        throw new Error('Facebook login failed');
      }

      const data = await apiResponse.json();
      
      if (data.success) {
        // Store tokens
        localStorage.setItem('accessToken', data.data.accessToken);
        localStorage.setItem('refreshToken', data.data.refreshToken);
        localStorage.setItem('user', JSON.stringify(data.data.user));
        
        // Redirect to dashboard
        window.location.href = '/dashboard';
      } else {
        throw new Error(data.message);
      }
    } catch (error) {
      console.error('Login error:', error);
      // Show error message to user
    }
  }
}

class LoginPage extends React.Component {
  constructor(props) {
    super(props);
    this.authService = new AuthService();
  }

  render() {
    return (
      <div>
        <h1>Login with Facebook</h1>
        <FacebookLogin
          appId="YOUR_APP_ID"
          fields="name,email,picture"
          callback={(response) => this.authService.loginWithFacebook(response)}
          render={(renderProps) => (
            <button 
              onClick={renderProps.onClick}
              className="facebook-login-btn"
            >
              Login with Facebook
            </button>
          )}
        />
      </div>
    );
  }
}

export default LoginPage;
```

### React Implementation (Functional Component with Hooks)

```javascript
import React, { useState } from 'react';
import FacebookLogin from 'react-facebook-login/dist/facebook-login-render-props';
import { useNavigate } from 'react-router-dom';

function LoginPage() {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const navigate = useNavigate();

  const handleFacebookResponse = async (response) => {
    setLoading(true);
    setError('');

    try {
      const apiResponse = await fetch('/api/v1/auth/login/facebook', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          accessToken: response.accessToken,
          email: response.email,
          name: response.name,
          picture: response.picture?.data?.url,
          userId: response.userID,
        }),
      });

      const data = await apiResponse.json();

      if (data.success) {
        // Store authentication data
        localStorage.setItem('accessToken', data.data.accessToken);
        localStorage.setItem('refreshToken', data.data.refreshToken);
        localStorage.setItem('user', JSON.stringify(data.data.user));

        // Redirect to dashboard
        navigate('/dashboard');
      } else {
        setError(data.message || 'Login failed');
      }
    } catch (err) {
      setError('An error occurred during login');
      console.error('Login error:', err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-container">
      <h1>Smart Money Login</h1>
      
      {error && <div className="error-message">{error}</div>}
      
      <FacebookLogin
        appId="YOUR_APP_ID"
        fields="name,email,picture"
        callback={handleFacebookResponse}
        disabled={loading}
        render={(renderProps) => (
          <button 
            onClick={renderProps.onClick}
            disabled={renderProps.isProcessing || loading}
            className="facebook-btn"
          >
            {loading ? 'Logging in...' : 'Login with Facebook'}
          </button>
        )}
      />
    </div>
  );
}

export default LoginPage;
```

### Vue 3 Implementation

```vue
<template>
  <div class="login-container">
    <h1>Smart Money Login</h1>
    
    <div v-if="error" class="error-message">
      {{ error }}
    </div>
    
    <button 
      @click="loginWithFacebook"
      :disabled="loading"
      class="facebook-btn"
    >
      {{ loading ? 'Logging in...' : 'Login with Facebook' }}
    </button>
  </div>
</template>

<script>
import { ref } from 'vue';
import { useRouter } from 'vue-router';

export default {
  setup() {
    const loading = ref(false);
    const error = ref('');
    const router = useRouter();

    const loginWithFacebook = () => {
      FB.login(async (response) => {
        if (response.authResponse) {
          loading.value = true;
          error.value = '';

          try {
            const userInfo = await new Promise((resolve) => {
              FB.api('/me', { fields: 'id,name,email,picture' }, resolve);
            });

            const apiResponse = await fetch('/api/v1/auth/login/facebook', {
              method: 'POST',
              headers: {
                'Content-Type': 'application/json',
              },
              body: JSON.stringify({
                accessToken: response.authResponse.accessToken,
                email: userInfo.email,
                name: userInfo.name,
                picture: userInfo.picture?.data?.url,
                userId: userInfo.id,
              }),
            });

            const data = await apiResponse.json();

            if (data.success) {
              // Store tokens
              localStorage.setItem('accessToken', data.data.accessToken);
              localStorage.setItem('refreshToken', data.data.refreshToken);
              localStorage.setItem('user', JSON.stringify(data.data.user));

              // Redirect
              router.push('/dashboard');
            } else {
              error.value = data.message || 'Login failed';
            }
          } catch (err) {
            error.value = 'An error occurred during login';
            console.error(err);
          } finally {
            loading.value = false;
          }
        }
      }, { scope: 'public_profile,email' });
    };

    return {
      loading,
      error,
      loginWithFacebook,
    };
  },
};
</script>

<style scoped>
.login-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.error-message {
  color: red;
  padding: 10px;
  background-color: #ffe6e6;
  border-radius: 4px;
}

.facebook-btn {
  background-color: #1877f2;
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 16px;
}

.facebook-btn:hover {
  background-color: #0a66c2;
}

.facebook-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
```

### Vanilla JavaScript Implementation

```html
<!DOCTYPE html>
<html>
<head>
  <title>Facebook Login</title>
  <script async defer crossorigin="anonymous" 
    src="https://connect.facebook.net/en_US/sdk.js#xfbml=1&version=v18.0&appId=YOUR_APP_ID&cookie=true" 
    nonce="XXXXXXXX">
  </script>
</head>
<body>
  <div id="app">
    <h1>Smart Money Login</h1>
    <button id="facebookLoginBtn">Login with Facebook</button>
    <div id="error-message" style="color: red; display: none;"></div>
  </div>

  <script>
    // Initialize Facebook SDK
    window.fbAsyncInit = function() {
      FB.init({
        appId: 'YOUR_APP_ID',
        xfbml: true,
        version: 'v18.0'
      });
    };

    // Handle Facebook login
    document.getElementById('facebookLoginBtn').addEventListener('click', () => {
      FB.login(async function(response) {
        if (response.authResponse) {
          const accessToken = response.authResponse.accessToken;

          // Get user info
          FB.api('/me', { fields: 'id,name,email,picture' }, async (userInfo) => {
            try {
              const apiResponse = await fetch('/api/v1/auth/login/facebook', {
                method: 'POST',
                headers: {
                  'Content-Type': 'application/json',
                },
                body: JSON.stringify({
                  accessToken: accessToken,
                  email: userInfo.email,
                  name: userInfo.name,
                  picture: userInfo.picture?.data?.url,
                  userId: userInfo.id,
                }),
              });

              const data = await apiResponse.json();

              if (data.success) {
                // Store tokens
                localStorage.setItem('accessToken', data.data.accessToken);
                localStorage.setItem('refreshToken', data.data.refreshToken);
                localStorage.setItem('user', JSON.stringify(data.data.user));

                // Redirect
                window.location.href = '/dashboard';
              } else {
                document.getElementById('error-message').textContent = data.message;
                document.getElementById('error-message').style.display = 'block';
              }
            } catch (error) {
              console.error('Error:', error);
              document.getElementById('error-message').textContent = 'Login failed';
              document.getElementById('error-message').style.display = 'block';
            }
          });
        } else {
          console.log('User cancelled login or did not fully authorize.');
        }
      }, { scope: 'public_profile,email' });
    });
  </script>
</body>
</html>
```

## 🔐 Token Management

### Store Tokens
```javascript
// After successful login
localStorage.setItem('accessToken', response.data.accessToken);
localStorage.setItem('refreshToken', response.data.refreshToken);
localStorage.setItem('user', JSON.stringify(response.data.user));
```

### Use Access Token in API Calls
```javascript
// Axios example
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8080/api/v1',
  headers: {
    'Authorization': `Bearer ${localStorage.getItem('accessToken')}`
  }
});

// Fetch protected resource
api.get('/users/me').then(response => {
  console.log('User data:', response.data);
});
```

### Refresh Token
```javascript
async function refreshAccessToken() {
  try {
    const response = await fetch('/api/v1/auth/refresh-token', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        refreshToken: localStorage.getItem('refreshToken'),
      }),
    });

    const data = await response.json();

    if (data.success) {
      localStorage.setItem('accessToken', data.data.accessToken);
      localStorage.setItem('refreshToken', data.data.refreshToken);
      return data.data.accessToken;
    } else {
      // Refresh failed, redirect to login
      localStorage.clear();
      window.location.href = '/login';
    }
  } catch (error) {
    console.error('Token refresh failed:', error);
    localStorage.clear();
    window.location.href = '/login';
  }
}
```

### Logout
```javascript
function logout() {
  // Clear local storage
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('user');

  // Logout from Facebook
  FB.logout(() => {
    // Redirect to login
    window.location.href = '/login';
  });
}
```

## 🧪 Testing

### Test with cURL
```bash
# Get Facebook access token first, then test
curl -X POST http://localhost:8080/api/v1/auth/login/facebook \
  -H "Content-Type: application/json" \
  -d '{
    "accessToken": "YOUR_FACEBOOK_ACCESS_TOKEN",
    "email": "test@example.com",
    "name": "Test User",
    "picture": "https://example.com/avatar.jpg",
    "userId": "123456"
  }'
```

### Test in Browser Console
```javascript
// After Facebook login
const response = await fetch('/api/v1/auth/login/facebook', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({
    accessToken: 'YOUR_ACCESS_TOKEN',
    email: 'test@example.com',
    name: 'Test User',
  }),
});

const data = await response.json();
console.log(data);
```

## ✅ Checklist

- [ ] Install Facebook SDK
- [ ] Create Facebook App
- [ ] Get App ID
- [ ] Configure OAuth Redirect URIs
- [ ] Add email permission request
- [ ] Implement Facebook login button
- [ ] Send access token to backend
- [ ] Store JWT tokens locally
- [ ] Use access token for API calls
- [ ] Implement token refresh
- [ ] Implement logout
- [ ] Test login flow
- [ ] Test token refresh
- [ ] Test error handling

## 🐛 Troubleshooting

### Issue: "Invalid OAuth scope"
**Solution**: Make sure you're requesting the right scopes:
```javascript
FB.login(callback, { scope: 'public_profile,email' });
```

### Issue: "No 'Access-Control-Allow-Origin' header"
**Solution**: CORS should be configured on backend. Check SecurityConfig.java

### Issue: "User cancelled login"
**Solution**: Handle this gracefully:
```javascript
if (response.authResponse) {
  // User logged in
} else {
  console.log('User cancelled');
}
```

### Issue: "Access token expired"
**Solution**: Implement token refresh before making requests

## 📚 References

- [Facebook SDK for JavaScript](https://developers.facebook.com/docs/facebook-sdk/reference/javascript)
- [Facebook Login Documentation](https://developers.facebook.com/docs/facebook-login)
- [React Facebook Login npm](https://www.npmjs.com/package/react-facebook-login)
- [@react-oauth/facebook npm](https://www.npmjs.com/package/@react-oauth/facebook)
- [Vue Facebook SDK](https://www.npmjs.com/package/vue-facebook-sdk)

## 🎊 Summary

You now have everything needed to:
1. ✅ Set up Facebook OAuth on frontend
2. ✅ Implement login button with Facebook SDK
3. ✅ Send access token to backend
4. ✅ Handle authentication response
5. ✅ Store and manage JWT tokens
6. ✅ Make authenticated API calls
7. ✅ Refresh tokens when needed
8. ✅ Implement logout functionality
