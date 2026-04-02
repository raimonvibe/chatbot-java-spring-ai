# OAuth2 Setup for Render Deployment

## Problem: redirect_uri_mismatch Error

When deploying to Render, you need to update the Google OAuth2 redirect URI in Google Cloud Console to match your Render backend URL.

## Solution

### Step 1: Find Your Render Backend URL

1. Go to [Render Dashboard](https://dashboard.render.com)
2. Find your backend service (named `chatbot-backend` in render.yaml)
3. Copy the service URL (e.g., `https://chatbot-backend-xxxx.onrender.com`)

### Step 2: Update Google Cloud Console

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Navigate to **APIs & Services** → **Credentials**
3. Find your OAuth 2.0 Client ID (named "Prayer-Chat Web Client")
4. Click **Edit** (pencil icon)

### Step 3: Add Render Redirect URI

**Authorized JavaScript origins:**
- Add: `https://your-backend-url.onrender.com`
- Example: `https://chatbot-backend-xxxx.onrender.com`

**Authorized redirect URIs:**
- Add: `https://your-backend-url.onrender.com/login/oauth2/code/google`
- Example: `https://chatbot-backend-xxxx.onrender.com/login/oauth2/code/google`

**Important:** Keep the localhost entries for local development:
- `http://localhost:8081` (JavaScript origin)
- `http://localhost:8081/login/oauth2/code/google` (Redirect URI)

### Step 4: Save and Wait

1. Click **Save**
2. Wait 1-2 minutes for changes to propagate
3. Try logging in again

## Current Configuration

The backend uses this redirect URI pattern:
```
{baseUrl}/login/oauth2/code/{registrationId}
```

On Render, this resolves to:
```
https://your-backend-url.onrender.com/login/oauth2/code/google
```

## Frontend Configuration

If your frontend is on a different domain, you may also need to:

1. Update **Authorized JavaScript origins** with your frontend URL
2. Update CORS settings in `SecurityConfig.java` to allow your frontend domain

## Troubleshooting

**Error: redirect_uri_mismatch**
- ✅ Check that the redirect URI in Google Console exactly matches: `https://your-backend-url.onrender.com/login/oauth2/code/google`
- ✅ Make sure there are no trailing slashes
- ✅ Wait 1-2 minutes after saving in Google Console

**Error: invalid_client**
- ✅ Check that `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` are set in Render environment variables
- ✅ Verify the credentials match what's in Google Cloud Console

**Error: access_denied**
- ✅ Check that your email is added as a test user in Google Cloud Console (if app is in testing mode)

