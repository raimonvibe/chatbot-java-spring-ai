# OAuth Domain Configuration Explanation

## Why You See the Backend URL in Google Login Popup

### The Issue

When users log in with Google, they see:
- ❌ "Sign in to continue to **chatbot-backend-4mp4.onrender.com**"
- ✅ You want: "Sign in to continue to **prayer-chat.com**"

### Why This Happens

This app uses **server-side OAuth flow** (Spring Security OAuth2), which is different from client-side OAuth flows used in many modern apps.

#### Current Architecture (Server-Side OAuth)

```
1. User clicks "Login with Google" on prayer-chat.com
   ↓
2. Frontend redirects to: https://chatbot-backend-4mp4.onrender.com/oauth2/authorization/google
   ↓
3. Backend redirects to Google OAuth
   ↓
4. Google shows consent screen with redirect URI: chatbot-backend-4mp4.onrender.com
   ↓
5. User authorizes
   ↓
6. Google redirects back to: https://chatbot-backend-4mp4.onrender.com/login/oauth2/code/google
   ↓
7. Backend processes OAuth, creates session, redirects to frontend
```

**Key Point**: The OAuth callback goes directly to the **backend**, not the frontend. That's why Google shows the backend URL.

#### Why Backend URL is Required in Google Cloud Console

In Google Cloud Console, you need to add:
- **Authorized JavaScript origins**: `https://chatbot-backend-4mp4.onrender.com`
- **Authorized redirect URIs**: `https://chatbot-backend-4mp4.onrender.com/login/oauth2/code/google`

This is **required** because:
1. The OAuth flow starts from the backend (`/oauth2/authorization/google`)
2. Google redirects back to the backend (`/login/oauth2/code/google`)
3. Google validates that the redirect URI matches what's configured

#### Why Other Apps Don't Need This

Other apps likely use **client-side OAuth** (Google Sign-In JavaScript library):

```
1. User clicks "Login with Google" on yourdomain.com
   ↓
2. JavaScript library directly calls Google OAuth
   ↓
3. Google shows consent screen with redirect URI: yourdomain.com
   ↓
4. User authorizes
   ↓
5. Google redirects back to: yourdomain.com/callback
   ↓
6. Frontend receives token, sends to backend
```

In this case:
- The redirect URI is the **frontend domain** (yourdomain.com)
- The backend URL is **not** in Google Cloud Console
- The backend only receives a token from the frontend, not directly from Google

---

## Solutions to Show prayer-chat.com

### Option 1: Hybrid OAuth Flow (Recommended - Cleanest Solution) ⭐

**This is the approach you described!** It's actually the cleanest solution because:
- ✅ Frontend domain appears in Google popup
- ✅ No need to add backend URL to Google Cloud Console
- ✅ No need for custom domain on backend
- ✅ More secure (authorization code handled by frontend, token exchange by backend)

#### How It Works

```
1. User clicks "Login with Google" on prayer-chat.com
   ↓
2. Frontend directly constructs Google OAuth URL with frontend redirect URI
   window.location.href = `https://accounts.google.com/o/oauth2/v2/auth?
     client_id=${CLIENT_ID}&
     redirect_uri=${FRONTEND_URL}/auth/callback&
     response_type=code&
     scope=email profile`
   ↓
3. Google shows consent screen with redirect URI: prayer-chat.com ✅
   ↓
4. User authorizes
   ↓
5. Google redirects back to: https://prayer-chat.com/auth/callback?code=...
   ↓
6. Frontend receives authorization code
   ↓
7. Frontend sends code to backend: POST /api/auth/oauth2/callback
   ↓
8. Backend exchanges code for tokens (server-side, secure)
   ↓
9. Backend creates session, returns JWT token
   ↓
10. Frontend stores token, redirects to dashboard
```

#### Implementation Steps

**Step 1: Update Frontend Login Button**

Instead of redirecting to backend, construct Google OAuth URL directly:

```typescript
// frontend/app/login/page.tsx
const handleGoogleLogin = () => {
  const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;
  const redirectUri = `${window.location.origin}/auth/callback`;
  const scope = 'email profile';
  const responseType = 'code';
  
  const googleAuthUrl = `https://accounts.google.com/o/oauth2/v2/auth?` +
    `client_id=${encodeURIComponent(clientId)}&` +
    `redirect_uri=${encodeURIComponent(redirectUri)}&` +
    `response_type=${responseType}&` +
    `scope=${encodeURIComponent(scope)}&` +
    `access_type=offline&` +
    `prompt=consent`;
  
  window.location.href = googleAuthUrl;
};
```

**Step 2: Create Frontend Callback Page**

```typescript
// frontend/app/auth/callback/page.tsx
'use client';

import { useEffect } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';

export default function AuthCallback() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const code = searchParams.get('code');
  const error = searchParams.get('error');

  useEffect(() => {
    if (error) {
      router.push(`/login?error=${encodeURIComponent(error)}`);
      return;
    }

    if (code) {
      // Send code to backend for token exchange
      fetch('/api/auth/oauth2/callback', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ code }),
        credentials: 'include',
      })
        .then(res => res.json())
        .then(data => {
          if (data.token) {
            // Store token and redirect
            localStorage.setItem('authToken', data.token);
            router.push('/dashboard');
          } else {
            router.push('/login?error=authentication_failed');
          }
        })
        .catch(() => {
          router.push('/login?error=authentication_failed');
        });
    }
  }, [code, error, router]);

  return <div>Completing login...</div>;
}
```

**Step 3: Create Backend Token Exchange Endpoint**

You'll need to add a new endpoint to handle the authorization code exchange. Here's a simplified example:

```java
// backend/src/main/java/com/prayer_chat/chatbot/controller/AuthController.java

@Autowired
private OAuth2AuthorizedClientService authorizedClientService;

@Autowired
private CustomOAuth2UserService customOAuth2UserService;

@Autowired
private JwtTokenProvider jwtTokenProvider;

@PostMapping("/api/auth/oauth2/callback")
public ResponseEntity<?> handleOAuthCallback(
    @RequestBody Map<String, String> request,
    HttpServletRequest httpRequest) {
    
    String code = request.get("code");
    String redirectUri = request.get("redirect_uri"); // Frontend callback URL
    
    try {
        // 1. Exchange authorization code for access token
        // Use Spring's OAuth2AuthorizedClientService or RestTemplate
        OAuth2AccessTokenResponse tokenResponse = exchangeCodeForToken(code, redirectUri);
        
        // 2. Get user info from Google using access token
        OAuth2User oauth2User = fetchUserInfo(tokenResponse.getAccessToken());
        
        // 3. Process OAuth2 user (create/update in database)
        // Reuse your existing CustomOAuth2UserService logic
        User user = customOAuth2UserService.processOAuth2User(oauth2User);
        
        // 4. Generate JWT token
        String jwtToken = jwtTokenProvider.generateToken(user.getEmail());
        
        // 5. Create session (optional, if using session-based auth)
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("user", user);
        
        return ResponseEntity.ok(Map.of(
            "token", jwtToken,
            "user", Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "name", user.getName()
            )
        ));
    } catch (Exception e) {
        logger.error("OAuth callback failed", e);
        return ResponseEntity.status(401).body(Map.of("error", "Authentication failed"));
    }
}

private OAuth2AccessTokenResponse exchangeCodeForToken(String code, String redirectUri) {
    // Use Spring's OAuth2AuthorizationCodeGrantRequest or RestTemplate
    // to exchange code for tokens
    // This requires GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET
}
```

**Note:** This requires implementing the token exchange logic. You can use Spring's `OAuth2AuthorizedClientService` or make a direct HTTP call to Google's token endpoint.

**Step 4: Update Google Cloud Console**

**Authorized JavaScript origins:**
- `https://prayer-chat.com`
- `https://www.prayer-chat.com`
- `http://localhost:3000` (for development)

**Authorized redirect URIs:**
- `https://prayer-chat.com/auth/callback`
- `https://www.prayer-chat.com/auth/callback`
- `http://localhost:3000/auth/callback` (for development)

**Important:** You can now REMOVE the backend URL from Google Cloud Console!

**Step 5: Add Environment Variables**

Frontend:
```bash
NEXT_PUBLIC_GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
```

Backend (still needs client secret for token exchange):
```bash
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret
```

#### Security Considerations

✅ **Authorization code is short-lived** (typically 10 minutes)
✅ **Token exchange happens server-side** (client secret never exposed)
✅ **JWT token is secure** (signed, contains user info)
✅ **Session management** (backend creates secure session)

#### Advantages Over Current Setup

- ✅ Frontend domain in Google popup (better UX)
- ✅ No backend URL in Google Cloud Console
- ✅ No need for custom domain on backend
- ✅ Cleaner architecture (frontend handles UI, backend handles security)

---

### Option 2: Use Custom Domain for Backend

If you have a reverse proxy or custom domain pointing to your backend:

#### Step 1: Set Up Domain Routing

You need to make your backend accessible via `prayer-chat.com`. Options:

**A. Use a Reverse Proxy (nginx/Cloudflare)**
```
prayer-chat.com → Reverse Proxy → chatbot-backend-4mp4.onrender.com
```

**B. Use Render Custom Domain**
- In Render dashboard, add custom domain `prayer-chat.com` to your backend service
- Or use a subdomain: `api.prayer-chat.com`

#### Step 2: Update Environment Variables

Set `APP_BASE_URL` to use your custom domain:

```bash
# In Render environment variables
APP_BASE_URL=https://prayer-chat.com
# OR if using subdomain:
APP_BASE_URL=https://api.prayer-chat.com
```

#### Step 3: Update Google Cloud Console

**Authorized JavaScript origins:**
- `https://prayer-chat.com` (or `https://api.prayer-chat.com`)

**Authorized redirect URIs:**
- `https://prayer-chat.com/login/oauth2/code/google` (or `https://api.prayer-chat.com/login/oauth2/code/google`)

**Keep existing entries for:**
- `https://chatbot-backend-4mp4.onrender.com` (fallback)
- `http://localhost:8081` (local development)

#### Step 4: Update Frontend

Update `frontend/app/login/page.tsx` to use the custom domain:

```typescript
function getApiBaseUrl(): string {
  if (process.env.NEXT_PUBLIC_API_URL) {
    return process.env.NEXT_PUBLIC_API_URL;
  }
  
  if (typeof window !== 'undefined') {
    const hostname = window.location.hostname;
    if (hostname === 'prayer-chat.com' || hostname === 'www.prayer-chat.com') {
      return 'https://prayer-chat.com'; // Use custom domain instead of backend URL
    }
  }
  
  return 'http://localhost:8081';
}
```

#### Step 5: Update OAuth Consent Screen

In Google Cloud Console → OAuth consent screen:
- **Application name**: Prayer-Chat
- **Authorized domains**: prayer-chat.com
- **Application home page**: https://prayer-chat.com
- **Privacy Policy URL**: https://prayer-chat.com/privacy
- **Terms of Service URL**: https://prayer-chat.com/terms

---

### Option 2: Change to Client-Side OAuth (Complex)

This requires significant refactoring:

1. Install Google Sign-In JavaScript library
2. Handle OAuth flow in frontend
3. Send token to backend for validation
4. Update authentication flow

**Pros:**
- Frontend domain appears in Google popup
- No backend URL in Google Cloud Console

**Cons:**
- Requires major code changes
- More complex token management
- Need to handle token refresh in frontend

---

### Option 3: Keep Current Setup, Update OAuth Consent Screen Name

If you can't change the domain, at least update the app name:

1. Go to Google Cloud Console → OAuth consent screen
2. Change **Application name** to "Prayer-Chat"
3. Add **Authorized domains**: prayer-chat.com

**Note**: The URL will still show `chatbot-backend-4mp4.onrender.com`, but the app name will be "Prayer-Chat".

---

## Current Configuration Analysis

### Backend Configuration

**File**: `backend/src/main/resources/application.yml`

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
```

The `{baseUrl}` is resolved from:
- `app.base-url` property (default: `https://chatbot-backend-4mp4.onrender.com`)
- Can be overridden with `APP_BASE_URL` environment variable

### Frontend Configuration

**File**: `frontend/app/login/page.tsx`

```typescript
window.location.href = `${API_BASE_URL}/oauth2/authorization/google`;
```

Currently redirects to backend URL directly.

---

## Recommended Solution: Custom Domain Setup

### If Using Render with Custom Domain

1. **Add Custom Domain in Render**
   - Go to Render dashboard → Your backend service
   - Settings → Custom Domains
   - Add `prayer-chat.com` or `api.prayer-chat.com`
   - Follow DNS configuration instructions

2. **Update Environment Variables**
   ```bash
   APP_BASE_URL=https://prayer-chat.com
   # OR
   APP_BASE_URL=https://api.prayer-chat.com
   ```

3. **Update Google Cloud Console**
   - Add new authorized origins and redirect URIs
   - Keep old ones as fallback

4. **Update Frontend**
   - Change `getApiBaseUrl()` to use custom domain

5. **Test**
   - Visit `https://prayer-chat.com/login`
   - Click "Login with Google"
   - Should see "prayer-chat.com" in Google popup

---

## Why Server-Side vs Client-Side OAuth

### Server-Side OAuth (Current Setup)

**Pros:**
- ✅ More secure (tokens never exposed to frontend)
- ✅ Better for server-side session management
- ✅ Simpler token refresh (handled by backend)
- ✅ Works well with Spring Security

**Cons:**
- ❌ Backend URL appears in Google popup
- ❌ Requires backend URL in Google Cloud Console
- ❌ More redirects (frontend → backend → Google → backend → frontend)

### Client-Side OAuth (Alternative)

**Pros:**
- ✅ Frontend domain appears in Google popup
- ✅ No backend URL in Google Cloud Console
- ✅ Fewer redirects

**Cons:**
- ❌ Tokens exposed to frontend (security concern)
- ❌ More complex token management
- ❌ Requires significant code changes
- ❌ Need to handle token refresh in frontend

---

## Summary

**Why you see the backend URL:**
- Your app uses server-side OAuth where Google redirects back to the backend
- The redirect URI (`{baseUrl}/login/oauth2/code/google`) uses the backend URL
- Google shows this URL in the consent screen

**Why backend URL is required in Google Cloud Console:**
- Google validates that redirect URIs match what's configured
- Since the callback goes to the backend, the backend URL must be authorized

**⭐ Recommended Solution: Hybrid OAuth Flow**
- Frontend directly initiates OAuth with Google (shows frontend domain in popup)
- Frontend receives authorization code
- Backend exchanges code for tokens (secure, server-side)
- **Result:** Frontend domain appears in Google popup, no backend URL needed in Google Cloud Console

**Alternative Solutions:**
1. Set up a custom domain for your backend (e.g., `prayer-chat.com` or `api.prayer-chat.com`)
2. Keep current setup but update OAuth consent screen name to "Prayer-Chat" (URL will still show backend domain)

