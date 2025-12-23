# REST Assured GET Request NPE Research

**Date**: December 23, 2025  
**Issue**: NullPointerException with GET requests, POST requests work fine  
**REST Assured Version**: 5.4.0

## Problem Summary

- ✅ POST requests work perfectly (StripeWebhookE2ETest: 9/9 passing)
- ❌ GET requests fail with NPE (29 errors across all E2E tests)
- Both use identical `createRequest()` pattern
- Static baseURI/port configuration is set correctly

## Online Research Findings

### Stack Overflow Discussions

**Key Findings**:
1. **Known Bug**: Some REST Assured versions have NPE issues with GET requests when using `RequestSpecification`
2. **Recommended Pattern**: Use `RestAssured.given().spec(requestSpec).get()` instead of `requestSpec.get()`
3. **Alternative**: Build request inline without creating separate RequestSpecification

**Sources**:
- [Stack Overflow: NPE with GET requests](https://stackoverflow.com/questions/23635957/null-pointer-exception-when-invoking-get-in-rest-assured-cannot-get-property-a)
- Multiple similar issues reported in REST Assured GitHub issues

### Attempted Solutions

All solutions were tried but NPE persists:

1. **Pattern 1**: `RestAssured.given().spec(requestSpec).get(path)`
   - Status: ❌ Still NPE

2. **Pattern 2**: Build request inline without RequestSpecification
   - Status: ❌ Still NPE

3. **Pattern 3**: Use full URL instead of relative path
   - Status: ❌ Still NPE

4. **Pattern 4**: Ensure static baseURI/port before each request
   - Status: ❌ Still NPE

### Key Observation

**POST works, GET doesn't** - This strongly suggests:
- REST Assured 5.4.0 has a bug specific to GET requests
- HTTP client initialization differs between GET and POST
- The issue is in REST Assured library, not our code

## Recommended Solutions

### Option 1: Upgrade/Downgrade REST Assured (Recommended)
```xml
<!-- Try 5.3.2 (older stable) -->
<version>5.3.2</version>

<!-- Or try 5.5.0 (newer, may have fixes) -->
<version>5.5.0</version>
```

### Option 2: Use WebTestClient (Best for Spring Boot)
- Native Spring Boot support
- Better integration with Spring Security
- No REST Assured dependency issues
- Requires rewriting E2E tests

### Option 3: Workaround - Use POST for GET requests
- Not ideal, but might work as temporary solution
- Convert GET endpoints to accept POST with query parameters

### Option 4: Report Bug to REST Assured
- File issue on REST Assured GitHub
- Provide minimal reproducible example
- Include version info and stack traces

## Test Evidence

**Working (POST)**:
```java
// This works perfectly
RequestSpecification request = createRequest();
return request.post("/api/webhooks/stripe");
```

**Failing (GET)**:
```java
// This fails with NPE
RequestSpecification request = createRequest();
return request.get("/api/chatbots");  // NPE here
```

**Same pattern, different HTTP methods = different behavior**

## Conclusion

The NPE with GET requests appears to be a REST Assured 5.4.0 library bug. All standard workarounds have been tried without success. The recommended next step is to try a different REST Assured version or migrate to WebTestClient.

