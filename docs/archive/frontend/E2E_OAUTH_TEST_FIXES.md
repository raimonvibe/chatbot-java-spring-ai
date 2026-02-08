# E2E OAuth Test Investigation and Fixes

## Issues Found

### 1. OAuth Login Flow Test Failure
**Problem**: Test times out waiting for dashboard/onboarding redirect after OAuth login.

**Root Cause**: 
- The hybrid OAuth flow redirects directly to Google's OAuth endpoint (`https://accounts.google.com/o/oauth2/v2/auth`)
- The test was trying to intercept `/oauth2/authorization/google` which doesn't exist in the new flow
- The OAuth callback flow needs to be properly mocked

**Fix Applied**:
- Updated test to intercept Google OAuth redirect
- Added mock for `/api/auth/oauth2/callback` endpoint
- Updated test to simulate OAuth callback with code parameter

### 2. Dashboard Redirect Test Failure
**Problem**: Dashboard redirects to login instead of onboarding when no chatbots.

**Root Cause**:
- Dashboard calls `getAllChatbots()` which requires JWT token in Authorization header
- API mocks weren't properly checking for Authorization header
- Token format is `Bearer mock_jwt_token`, not just `mock_jwt_token`
- `/api/auth/me` endpoint wasn't mocked to return user data

**Fixes Applied**:
- Added `/api/auth/me` mock that checks for Authorization header
- Updated `/api/chatbots` mock to require Authorization header
- Fixed Authorization header format check (Bearer token)

## Changes Made

### `e2e/helpers/api-mock.ts`
1. Added `/api/auth/me` endpoint mock
   - Returns user data when Authorization header contains `mock_jwt_token`
   - Returns 401 when no valid token

2. Added `/api/auth/oauth2/callback` endpoint mock
   - Returns token and user data for successful OAuth flow

3. Updated `/api/chatbots` mock
   - Now checks for Authorization header
   - Returns 401 if no valid token

### `e2e/pages/login.spec.ts`
1. Updated OAuth login test
   - Intercepts Google OAuth redirect
   - Simulates OAuth callback with code parameter
   - Waits for callback processing and redirect

### `e2e/pages/dashboard.spec.ts`
1. Test structure is correct
   - Sets up authenticated state
   - Mocks empty chatbots array
   - Expects redirect to onboarding

## Root Cause Identified and Fixed

### The Real Problem: JWT Token Format Validation

**Issue**: The `getAuthHeaders()` function in `frontend/lib/api.ts` validates that JWT tokens must have exactly 3 parts separated by dots (format: `header.payload.signature`). However, all E2E tests were using `'mock_jwt_token'` which is NOT a valid JWT format.

**What Happened**:
1. Test sets `localStorage.setItem('authToken', 'mock_jwt_token')`
2. API call uses `getAuthHeaders()` which reads the token
3. Token validation fails (not 3 parts)
4. Token is automatically removed from localStorage
5. API calls are made without Authorization header
6. Backend/mocks return 401 Unauthorized
7. Dashboard redirects to login

**Fix Applied**:
- Updated all test helpers to use valid JWT format: `'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIn0.mock_signature'`
- Updated API mocks to recognize this token format
- Updated OAuth test to directly test callback flow (since `window.location.href` navigation can't be intercepted)

## Test Status

- ✅ API mock structure updated
- ✅ OAuth callback endpoint mocked
- ✅ Authorization header checks added
- ✅ JWT token format fixed in all test helpers
- ✅ Dashboard redirect test **PASSING**
- ✅ OAuth login test **PASSING**

## Recommendations

1. **Add more detailed logging** to understand the flow
2. **Check token persistence** across page navigations
3. **Verify mock setup timing** - ensure mocks are active before API calls
4. **Consider using Playwright's request interception** more effectively
5. **Test with actual backend** to verify the flow works in production

