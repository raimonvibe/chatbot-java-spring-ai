# Vercel Environment Variables Setup Guide

## Quick Fix: Add NEXT_PUBLIC_GOOGLE_CLIENT_ID

### Step-by-Step Instructions

1. **Go to Vercel Dashboard**
   - Visit: https://vercel.com/dashboard
   - Login to your account

2. **Select Your Project**
   - Find and click on your project (likely named something like "chatbot-java-spring-ai" or "prayer-chat")

3. **Navigate to Settings**
   - Click on the **Settings** tab (top navigation)
   - In the left sidebar, click **Environment Variables**

4. **Add New Environment Variable**
   - Click the **"Add New"** button (or **"Add"** button)
   - You'll see a form with:
     - **Key** field
     - **Value** field
     - **Environment** checkboxes (Production, Preview, Development)

5. **Fill in the Form**
   ```
   Key: NEXT_PUBLIC_GOOGLE_CLIENT_ID
   Value: [paste your Google Client ID here]
   ```

   **Important:**
   - ✅ Check **Production**
   - ✅ Check **Preview**  
   - ✅ Check **Development**
   
   This ensures it works in all environments.

6. **Get Your Google Client ID**
   - Go to: https://console.cloud.google.com/
   - Navigate to: **APIs & Services** → **Credentials**
   - Find your **OAuth 2.0 Client ID**
   - Copy the **Client ID** (looks like: `123456789-abc.apps.googleusercontent.com`)
   - Paste it as the **Value** in Vercel

7. **Save**
   - Click **Save** (or **Add**)
   - The variable will appear in the list

8. **Redeploy** (CRITICAL!)
   - Go to **Deployments** tab
   - Find your latest deployment
   - Click the **"..."** (three dots) menu
   - Click **"Redeploy"**
   - Confirm the redeploy
   - Wait for build to complete (2-5 minutes)

## Visual Guide

```
Vercel Dashboard
  └── Your Project
      └── Settings (tab)
          └── Environment Variables (left sidebar)
              └── Add New (button)
                  ├── Key: NEXT_PUBLIC_GOOGLE_CLIENT_ID
                  ├── Value: [your-client-id]
                  └── Environments: ☑ Production ☑ Preview ☑ Development
                      └── Save
```

## Verify It's Set

After adding and redeploying:

1. **Check in Vercel:**
   - Go to Settings → Environment Variables
   - You should see `NEXT_PUBLIC_GOOGLE_CLIENT_ID` in the list

2. **Check in Browser:**
   - Visit your site: https://prayer-chat.com/login
   - Open browser DevTools (F12)
   - Go to Console tab
   - Click "Continue with Google"
   - You should NOT see: "NEXT_PUBLIC_GOOGLE_CLIENT_ID is not set"
   - Instead, you should be redirected to Google login

## Troubleshooting

### "I don't see Environment Variables option"
- Make sure you're in **Settings** tab
- Look in the **left sidebar** (not top navigation)
- It might be under "Build & Development Settings" → "Environment Variables"

### "Variable not working after adding"
- **Did you redeploy?** Environment variables only apply to NEW builds
- Go to Deployments → Click "..." → Redeploy
- Wait for build to complete

### "Still getting error after redeploy"
- Check the variable name is exactly: `NEXT_PUBLIC_GOOGLE_CLIENT_ID` (case-sensitive)
- Check all environments are selected (Production, Preview, Development)
- Check the value doesn't have extra spaces or quotes
- Clear browser cache and try again

### "Where do I find my Google Client ID?"
1. Go to: https://console.cloud.google.com/
2. Select your project
3. Go to: **APIs & Services** → **Credentials**
4. Find **OAuth 2.0 Client IDs** section
5. Click on your client ID (or create one if you don't have it)
6. Copy the **Client ID** (not the Client Secret!)

## Required Environment Variables Summary

For the hybrid OAuth flow to work, you need:

**Frontend (Vercel):**
- ✅ `NEXT_PUBLIC_GOOGLE_CLIENT_ID` - Your Google OAuth Client ID
- ✅ `NEXT_PUBLIC_API_URL` - Your backend URL (probably already set)

**Backend (Render):**
- ✅ `GOOGLE_CLIENT_ID` - Same as above
- ✅ `GOOGLE_CLIENT_SECRET` - Your Google OAuth Client Secret
- ✅ `JWT_SECRET` - Your JWT secret key

## Still Having Issues?

If you're still having trouble:
1. Check Vercel build logs for any errors
2. Verify the Google Client ID is correct
3. Make sure Google Cloud Console has the correct redirect URIs:
   - `https://prayer-chat.com/auth/callback`
   - `https://www.prayer-chat.com/auth/callback`

