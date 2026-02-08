# Production URL Configuration Verification

**Date:** 2025-12-24  
**Step:** 2.3 - Verify Production URL Configuration  
**Status:** ⏳ **IN PROGRESS**

---

## 📋 Verification Checklist

### 1. Frontend Environment Variables (Vercel)

#### Required Variable:
- `NEXT_PUBLIC_API_URL` = `https://chatbot-backend-4mp4.onrender.com`

#### Verification Steps:
1. Go to Vercel Dashboard → Project Settings → Environment Variables
2. Verify `NEXT_PUBLIC_API_URL` is set for **Production** environment
3. Verify value is: `https://chatbot-backend-4mp4.onrender.com` (no trailing slash)

#### Current Status:
- [ ] Vercel environment variable configured
- [ ] Variable set for Production environment
- [ ] Value is correct (no trailing slash)

---

### 2. Backend CORS Configuration (Render)

#### Required Configuration:
- `CORS_ALLOWED_ORIGINS` environment variable in Render
- Should include production frontend URLs

#### Expected Value:
```
https://prayer-chat.com,https://www.prayer-chat.com,https://*.vercel.app
```

#### Verification Steps:
1. Go to Render Dashboard → Service Settings → Environment
2. Verify `CORS_ALLOWED_ORIGINS` is set
3. Verify it includes:
   - `https://prayer-chat.com`
   - `https://www.prayer-chat.com`
   - `https://*.vercel.app` (for preview deployments)

#### Current Status:
- [ ] Render environment variable configured
- [ ] Production frontend URLs included
- [ ] Vercel preview URLs included

---

### 3. API Call Testing

#### Test Endpoints:
1. **Health Check:**
   ```
   GET https://chatbot-backend-4mp4.onrender.com/api/health
   ```
   Expected: `200 OK` with health status

2. **Root API Info:**
   ```
   GET https://chatbot-backend-4mp4.onrender.com/
   ```
   Expected: `200 OK` with JSON containing `frontend_url`

3. **CORS Preflight:**
   ```
   OPTIONS https://chatbot-backend-4mp4.onrender.com/api/auth/me
   Headers:
     Origin: https://prayer-chat.com
     Access-Control-Request-Method: GET
   ```
   Expected: `200 OK` with CORS headers

#### Current Status:
- [ ] Health check endpoint works
- [ ] Root endpoint returns correct frontend URL
- [ ] CORS preflight requests succeed
- [ ] API calls from production frontend work

---

### 4. Integration Script URL Verification

#### Expected Behavior:
- Integration script should use production backend URL
- Embed code should contain: `https://chatbot-backend-4mp4.onrender.com/js/chatbot-widget.js`
- API URL in embed code should be: `https://chatbot-backend-4mp4.onrender.com/api`

#### Verification Steps:
1. Log in to production frontend
2. Create or select a chatbot
3. Click "Get Embed Code"
4. Verify embed code contains production URLs (not localhost)

#### Current Status:
- [ ] Integration script uses production URL
- [ ] Embed code contains correct API URL
- [ ] No localhost URLs in production

---

## 🔍 Current Configuration Analysis

### Frontend API URL Detection Logic

**Location:** `frontend/lib/api.ts` and `frontend/components/PaywallModal.tsx`

**Logic:**
```typescript
function getApiBaseUrl(): string {
  // 1. Check environment variable (highest priority)
  if (process.env.NEXT_PUBLIC_API_URL) {
    return process.env.NEXT_PUBLIC_API_URL;
  }
  
  // 2. Check hostname (fallback)
  if (typeof window !== 'undefined') {
    const hostname = window.location.hostname;
    if (hostname === 'prayer-chat.com' || hostname === 'www.prayer-chat.com') {
      return 'https://chatbot-backend-4mp4.onrender.com';
    }
    if (hostname.includes('vercel.app')) {
      return 'https://chatbot-backend-4mp4.onrender.com';
    }
  }
  
  // 3. Default to localhost (development)
  return 'http://localhost:8081';
}
```

**Status:** ✅ Logic is correct, but requires `NEXT_PUBLIC_API_URL` to be set in Vercel

---

### Backend CORS Configuration

**Location:** `backend/src/main/resources/application.yml` line 240

**Current Configuration:**
```yaml
cors:
  allowed-origins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,https://prayer-chat.com,https://www.prayer-chat.com,https://chatbot-java-spring-ai.vercel.app,https://*.vercel.app}
```

**Status:** ✅ Default includes production URLs, but should be set via environment variable in Render

---

### Integration Script URL Configuration

**Location:** `backend/src/main/java/com/prayer_chat/chatbot/controller/ChatbotController.java`

**Current Configuration:**
```java
@Value("${app.base-url:https://chatbot-backend-4mp4.onrender.com}")
private String baseUrl;
```

**Status:** ✅ Default is production URL, but should be set via `APP_BASE_URL` environment variable in Render

---

## 🚀 Action Items

### Immediate Actions (Required):

1. **Set Vercel Environment Variable:**
   ```bash
   # In Vercel Dashboard:
   NEXT_PUBLIC_API_URL = https://chatbot-backend-4mp4.onrender.com
   ```

2. **Set Render Environment Variables:**
   ```bash
   # In Render Dashboard:
   CORS_ALLOWED_ORIGINS = https://prayer-chat.com,https://www.prayer-chat.com,https://*.vercel.app
   APP_BASE_URL = https://chatbot-backend-4mp4.onrender.com
   FRONTEND_URL = https://prayer-chat.com
   ```

3. **Redeploy Services:**
   - Redeploy Vercel frontend (to pick up `NEXT_PUBLIC_API_URL`)
   - Restart Render backend (to pick up CORS and base URL)

4. **Test Production:**
   - Test API calls from production frontend
   - Test integration script generation
   - Verify CORS headers in browser DevTools

---

## ✅ Verification Test Script

### Manual Testing Steps:

1. **Test Frontend API URL Detection:**
   ```javascript
   // In browser console on production frontend:
   console.log('API URL:', process.env.NEXT_PUBLIC_API_URL || 'Not set');
   ```

2. **Test Backend CORS:**
   ```bash
   curl -X OPTIONS https://chatbot-backend-4mp4.onrender.com/api/auth/me \
     -H "Origin: https://prayer-chat.com" \
     -H "Access-Control-Request-Method: GET" \
     -v
   ```
   Should return `200 OK` with `Access-Control-Allow-Origin: https://prayer-chat.com`

3. **Test Integration Script:**
   - Log in to production
   - Get embed code
   - Verify URLs are production (not localhost)

---

## 📊 Status Summary

| Component | Status | Notes |
|-----------|--------|-------|
| Frontend API URL Logic | ✅ Correct | Needs env var in Vercel |
| Backend CORS Config | ✅ Correct | Needs env var in Render |
| Integration Script URLs | ✅ Correct | Needs env var in Render |
| Environment Variables | ⏳ Pending | Need to be set in Vercel/Render |
| Production Testing | ⏳ Pending | After env vars are set |

---

## 🎯 Next Steps

1. Set environment variables in Vercel and Render
2. Redeploy services
3. Run verification tests
4. Update this document with results

---

**Last Updated:** 2025-12-24  
**Next Review:** After environment variables are set

