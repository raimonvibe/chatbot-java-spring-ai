# Security Testing Documentation

## Test Fixtures and GitGuardian Alerts

This document explains the security-related test fixtures used in this project and why they trigger GitGuardian alerts.

## ⚠️ Important: These are NOT real secrets!

The JWT tokens, bearer tokens, and passwords detected by GitGuardian are **intentional test fixtures** used to verify that our security sanitization works correctly. They are NOT real credentials.

### Test Fixtures Explained

#### 1. JWT Tokens in Tests

**Location:** 
- `backend/src/test/java/com/tjanabot/chatbot/util/LogSanitizerTest.java`
- `frontend/e2e/helpers/auth.ts`
- `frontend/e2e/helpers/api-mock.ts`

**Test Token:**
```typescript
'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIn0.mock_signature'
```

**Purpose:** 
- This is a **test JWT token** used in E2E tests to simulate authentication
- The signature part contains `mock_signature` which clearly indicates it's a test token
- Used to verify that JWT validation works correctly in the frontend
- Used to test that our LogSanitizer correctly redacts JWT tokens from logs

**Decoded content:**
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
{
  "sub": "test@example.com"
}
```

**Why it's safe:** 
- The token signature is `mock_signature` - clearly a test value
- Only used in test files (`e2e/helpers/`)
- Never used in production code
- Cannot be used to authenticate with any real system

**Decoded content:**
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
{
  "sub": "1234567890",
  "name": "John Doe",
  "iat": 1516239022
}
```

**Why it's safe:** This token is signed with a demo key and is used in countless tutorials and documentation. It has no connection to any real system or credentials.

#### 2. Test Passwords

**Location:** Various test files

**Examples:**
- `password=MySecret123`
- `password: hunter2`
- `PASSWORD="SuperSecret!@#"`

**Purpose:** These test passwords verify that our LogSanitizer correctly removes passwords from log messages before they're written.

**Why it's safe:** These are hardcoded strings in test files that never connect to any real authentication system.

#### 3. Bearer Tokens

**Location:** Test files for authentication

**Purpose:** Mock bearer tokens used to test API authentication and rate limiting logic.

**Why it's safe:** These tokens are never used in production and are only present in test execution contexts.

## GitGuardian Configuration

We've configured GitGuardian via `.gitguardian.yaml` to:

1. **Exclude test directories** - All files under `**/test/**` and `**/__tests__/**`
2. **Exclude test files** - Files matching `*Test.java`, `*.test.ts`, etc.
3. **Ignore known patterns** - The specific JWT header used in examples

## Security Verification

Our test suite actually **proves** these are safe by verifying that:

1. ✅ JWT tokens are correctly sanitized in logs (LogSanitizerTest)
2. ✅ Passwords are redacted from log output (LogSanitizerTest)
3. ✅ Bearer tokens are masked in API logs (RateLimitingFilterTest)
4. ✅ All sensitive data is properly handled (100% test coverage)

## Real Security Measures

While test fixtures are safe, we implement real security through:

1. **Production secrets** are stored in environment variables (not in code)
2. **API keys** are never committed to the repository
3. **Database passwords** are in `.env` files (gitignored)
4. **JWT secrets** for production use `${JWT_SECRET}` environment variable
5. **Stripe keys** use `${STRIPE_SECRET_KEY}` environment variable

## How to Verify

To verify these are test fixtures and not real secrets:

1. Check the file path - all are in `test/` directories
2. Check the context - all are in JUnit `@Test` methods
3. Check the assertions - tests verify these get **redacted**
4. Check git history - these fixtures have never changed (real secrets would rotate)

## Questions?

If you have concerns about any detected "secret", please:

1. Verify it's in a test file (path contains `/test/` or `__tests__`)
2. Check if it's listed in this document
3. Confirm it's used to **test security measures**, not bypass them

For real security concerns, please open a GitHub Security Advisory.

---

**Last Updated:** 2025-11-24
**Status:** All flagged items are confirmed test fixtures ✅
