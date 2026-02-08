# Fix OAuth Consent Screen - Show "Prayer-Chat" Instead of "chatbot-backend-4mp4.onrender.com"

## Problem
When users log in with Google, the OAuth consent screen shows "Inloggen bij chatbot-backend-4mp4.onrender.com" instead of "Prayer-Chat". This is confusing and unprofessional.

## Solution
The **App name** in Google Cloud Console's OAuth consent screen configuration needs to be updated to "Prayer-Chat".

## Step-by-Step Instructions

### 1. Go to Google Cloud Console
1. Open [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project (the one with OAuth credentials)

### 2. Navigate to OAuth Consent Screen
1. In the left sidebar, go to **"APIs & Services"**
2. Click **"OAuth consent screen"**

### 3. Update the App Name
1. You should see a form with several fields
2. Find the **"App name"** field (usually at the top)
3. Change it from whatever it currently says (e.g., "chatbot-backend-4mp4.onrender.com" or "Tjanabot") to:
   ```
   Prayer-Chat
   ```

### 4. Save Changes
1. Scroll down and click **"Save and Continue"** (or just **"Save"** if you're editing)
2. If you're in "Testing" mode, you may need to click through the steps
3. If you're in "Production" mode, you may need to submit for verification (this can take a few days)

### 5. Verify the Change
1. After saving, wait a few minutes for changes to propagate
2. Try logging in again with Google
3. The consent screen should now show "Inloggen bij Prayer-Chat" instead of the backend URL

## Important Notes

- **App name vs Project name**: The "App name" in the OAuth consent screen is different from the "Project name" in Google Cloud Console. The App name is what users see during login.
- **Testing vs Production**: 
  - If your app is in "Testing" mode, only test users can log in
  - If your app is in "Production" mode, anyone can log in (but you may need to submit for verification)
- **Verification**: If you change the app name in Production mode, Google may require you to verify your app again. This is normal and usually takes 1-3 business days.

## Troubleshooting

### Still seeing the old name?
- Wait 5-10 minutes for changes to propagate
- Clear your browser cache and cookies
- Try logging in from an incognito/private window
- Make sure you saved the changes in Google Cloud Console

### Can't find the OAuth consent screen?
- Make sure you're in the correct Google Cloud project
- Check that OAuth 2.0 is enabled for your project
- You may need to create the OAuth consent screen first (click "Create" if you see that option)

### Need to change other fields?
While you're there, you can also update:
- **User support email**: Your email address
- **Developer contact information**: Your email address
- **App logo**: Upload a Prayer-Chat logo (optional)
- **App domain**: prayer-chat.com (if you have a custom domain)
- **Privacy Policy URL**: https://prayer-chat.com/privacy (if you have one)
- **Terms of Service URL**: https://prayer-chat.com/terms (if you have one)

## Visual Guide

The OAuth consent screen configuration page should look something like this:

```
┌─────────────────────────────────────┐
│ OAuth consent screen                │
├─────────────────────────────────────┤
│ App name: [Prayer-Chat        ]     │ ← Change this!
│ User support email: [your@email.com]│
│ App logo: [Upload]                  │
│ ...                                 │
│                                     │
│ [Save and Continue]                  │
└─────────────────────────────────────┘
```

After updating, the login screen will show:
- ✅ "Inloggen bij Prayer-Chat" (correct)
- ❌ "Inloggen bij chatbot-backend-4mp4.onrender.com" (old, incorrect)

