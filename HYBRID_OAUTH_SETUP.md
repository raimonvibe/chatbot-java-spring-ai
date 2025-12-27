# Hybrid OAuth Flow Setup Guide

This guide explains how to set up the hybrid OAuth flow that shows your frontend domain (`prayer-chat.com`) in the Google login popup instead of the backend URL.

## Overview

The hybrid OAuth flow works as follows:
1. Frontend directly initiates OAuth with Google (shows frontend domain)
2. Google redirects back to frontend with authorization code
3. Frontend sends code to backend for secure token exchange
4. Backend returns JWT token and user info

## Environment Variables

### Frontend (Next.js)

Add to your `.env.local` or Vercel environment variables:

```bash
# Google OAuth Client ID (public, safe to expose)
NEXT_PUBLIC_GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com

# Backend API URL (for token exchange)
NEXT_PUBLIC_API_URL=https://chatbot-backend-4mp4.onrender.com
# OR for production:
# NEXT_PUBLIC_API_URL=https://prayer-chat.com
```

### Backend (Spring Boot)

Add to your `.env` file or Render environment variables:

```bash
# Google OAuth credentials (required for token exchange)
GOOGLE_CLIENT_ID=your-client-id.apps.googleusercontent.com
GOOGLE_CLIENT_SECRET=your-client-secret

# JWT configuration
JWT_SECRET=your-secret-key-min-32-chars
JWT_EXPIRATION=86400000  # 24 hours in milliseconds
```

## Google Cloud Console Configuration

### Step 1: Update Authorized JavaScript Origins

Go to [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → Credentials → Your OAuth 2.0 Client ID

**Authorized JavaScript origins:**
- `https://prayer-chat.com`
- `https://www.prayer-chat.com`
- `http://localhost:3000` (for local development)

**Remove or keep as fallback:**
- `https://chatbot-backend-4mp4.onrender.com` (no longer needed, but can keep as fallback)

### Step 2: Update Authorized Redirect URIs

**Authorized redirect URIs:**
- `https://prayer-chat.com/auth/callback`
- `https://www.prayer-chat.com/auth/callback`
- `http://localhost:3000/auth/callback` (for local development)

**Remove:**
- `https://chatbot-backend-4mp4.onrender.com/login/oauth2/code/google` (no longer needed)

### Step 3: Update OAuth Consent Screen

- **Application name**: Prayer-Chat
- **Authorized domains**: prayer-chat.com
- **Application home page**: https://prayer-chat.com
- **Privacy Policy URL**: https://prayer-chat.com/privacy (if available)
- **Terms of Service URL**: https://prayer-chat.com/terms (if available)

## Implementation Details

### Frontend Flow

1. **Login Page** (`frontend/app/login/page.tsx`)
   - Constructs Google OAuth URL directly
   - Redirects to Google with frontend callback URL

2. **Callback Page** (`frontend/app/auth/callback/page.tsx`)
   - Receives authorization code from Google
   - Sends code to backend `/api/auth/oauth2/callback`
   - Stores JWT token and redirects to dashboard

### Backend Flow

1. **Token Exchange Endpoint** (`/api/auth/oauth2/callback`)
   - Receives authorization code from frontend
   - Exchanges code for access token with Google
   - Fetches user info from Google
   - Creates/updates user in database
   - Generates JWT token
   - Returns token and user info

## Testing

### Local Development

1. **Frontend:**
   ```bash
   cd frontend
   npm run dev
   # Visit http://localhost:3000/login
   ```

2. **Backend:**
   ```bash
   cd backend
   mvn spring-boot:run
   # Backend runs on http://localhost:8081
   ```

3. **Test Flow:**
   - Click "Login with Google" on frontend
   - Should see `localhost:3000` in Google popup
   - After authorization, should redirect to dashboard

### Production

1. Deploy frontend to Vercel
2. Deploy backend to Render
3. Set environment variables in both platforms
4. Update Google Cloud Console with production URLs
5. Test login flow

## Troubleshooting

### Error: "NEXT_PUBLIC_GOOGLE_CLIENT_ID is not set"

**Solution:** Add `NEXT_PUBLIC_GOOGLE_CLIENT_ID` to your frontend environment variables.

### Error: "No authorization code received"

**Solution:** 
- Check that redirect URI in Google Cloud Console matches exactly
- Ensure callback URL is `https://prayer-chat.com/auth/callback` (no trailing slash)

### Error: "Failed to exchange code for token"

**Solution:**
- Verify `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are set in backend
- Check that redirect URI used in token exchange matches the one in Google Cloud Console

### Error: "Authentication failed"

**Solution:**
- Check backend logs for detailed error messages
- Verify JWT_SECRET is set in backend
- Ensure database connection is working

## Migration from Server-Side OAuth

If you're migrating from the old server-side OAuth flow:

1. **Keep old endpoints** (they're still configured for backward compatibility)
2. **Update frontend** to use new hybrid flow
3. **Update Google Cloud Console** to add frontend redirect URIs
4. **Test thoroughly** before removing old backend redirect URIs

## Security Considerations

✅ **Authorization code is short-lived** (typically 10 minutes)
✅ **Token exchange happens server-side** (client secret never exposed)
✅ **JWT token is secure** (signed, contains user info)
✅ **Session management** (backend creates secure session)

## Benefits

- ✅ Frontend domain appears in Google popup (better UX)
- ✅ No backend URL in Google Cloud Console
- ✅ No need for custom domain on backend
- ✅ Cleaner architecture (frontend handles UI, backend handles security)
- ✅ More secure (authorization code handled by frontend, token exchange by backend)

