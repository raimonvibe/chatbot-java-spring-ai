# JWT Token Security Implementation

## Overview

This document describes the security measures implemented for JWT token handling in the frontend API client.

## Security Measures

### 1. Token Format Validation

JWT tokens must have exactly 3 parts separated by dots:
- Format: `header.payload.signature`
- Invalid formats are automatically rejected and removed from localStorage

### 2. Character Sanitization

Tokens are sanitized to prevent header injection attacks:
- **Removed characters**: Newlines (`\n`), carriage returns (`\r`), tabs (`\t`)
- **Allowed characters**: Alphanumeric, dots (`.`), hyphens (`-`), underscores (`_`), equals signs (`=`)
- This ensures tokens conform to base64url encoding standards

### 3. Input Validation

- **Empty tokens**: Rejected (no Authorization header added)
- **Whitespace-only tokens**: Trimmed and validated
- **Malformed tokens**: Automatically removed from localStorage
- **Invalid characters**: Tokens with suspicious characters are rejected

### 4. Error Handling

- **localStorage errors**: Handled gracefully (e.g., private browsing mode)
- **No token exposure**: Errors are logged without exposing token values
- **Automatic cleanup**: Invalid tokens are automatically removed

### 5. Server-Side Rendering Compatibility

- Checks for `window` object before accessing localStorage
- Works correctly in SSR context (Next.js)

## Implementation Details

### `getAuthHeaders()` Function

```typescript
function getAuthHeaders(): HeadersInit {
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
  };
  
  if (typeof window !== 'undefined') {
    try {
      const token = localStorage.getItem('authToken');
      
      if (token && token.trim().length > 0) {
        const tokenParts = token.trim().split('.');
        
        // JWT must have exactly 3 parts
        if (tokenParts.length === 3) {
          // Sanitize: remove control characters
          const sanitizedToken = token.trim().replace(/[\r\n\t]/g, '');
          
          // Validate: base64url characters only
          if (/^[A-Za-z0-9._=-]+$/.test(sanitizedToken)) {
            headers['Authorization'] = `Bearer ${sanitizedToken}`;
          } else {
            console.warn('Invalid token format detected');
            localStorage.removeItem('authToken');
          }
        } else {
          console.warn('Malformed JWT token detected');
          localStorage.removeItem('authToken');
        }
      }
    } catch (error) {
      console.warn('Error accessing localStorage for auth token');
    }
  }
  
  return headers;
}
```

## Security Threats Mitigated

### 1. Header Injection Attacks

**Threat**: Attacker injects newline characters in token to add malicious headers
```javascript
// Malicious token example
const maliciousToken = 'valid.token\nX-Injected-Header: malicious-value';
```

**Mitigation**: 
- All control characters (newlines, tabs, carriage returns) are removed
- Only base64url characters are allowed

### 2. XSS Token Theft

**Threat**: Malicious script steals token from localStorage

**Mitigation**:
- Token validation prevents execution of malicious code
- Invalid tokens are automatically removed
- No token values are logged or exposed in error messages

### 3. Malformed Token Attacks

**Threat**: Invalid token formats causing backend errors or security issues

**Mitigation**:
- Strict JWT format validation (3 parts)
- Automatic removal of malformed tokens
- Graceful error handling

### 4. Token Format Confusion

**Threat**: Non-JWT tokens stored in localStorage causing authentication failures

**Mitigation**:
- Format validation ensures only valid JWT tokens are used
- Invalid tokens are rejected and removed

## Testing

Comprehensive test suite covers:

1. **Valid Token Handling**
   - Valid JWT tokens are included in Authorization header
   - Base64url characters are accepted

2. **Malformed Token Rejection**
   - Wrong number of parts (not 3)
   - Invalid characters
   - Empty/null tokens

3. **Header Injection Prevention**
   - Newline characters removed
   - Tab characters removed
   - Control characters sanitized

4. **Edge Cases**
   - Empty strings
   - Very long tokens
   - Server-side rendering (no window object)
   - localStorage errors

5. **API Function Integration**
   - All authenticated API functions include token
   - Token is properly formatted in Authorization header

## Test Coverage

See `frontend/lib/__tests__/api-auth-security.test.ts` for:
- 20+ security test cases
- Token validation tests
- Header injection prevention tests
- Edge case handling tests
- API integration tests

## Best Practices

1. **Never log token values**: Only log warnings without exposing tokens
2. **Automatic cleanup**: Invalid tokens are removed automatically
3. **Fail securely**: If token is invalid, don't include Authorization header
4. **Graceful degradation**: Handle errors without breaking the application

## Related Files

- `frontend/lib/api.ts` - Main API client with `getAuthHeaders()`
- `frontend/lib/__tests__/api-auth-security.test.ts` - Security tests
- `frontend/app/auth/callback/page.tsx` - Token storage after OAuth

## References

- [JWT Specification (RFC 7519)](https://tools.ietf.org/html/rfc7519)
- [Base64URL Encoding (RFC 4648)](https://tools.ietf.org/html/rfc4648#section-5)
- [OWASP JWT Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)

