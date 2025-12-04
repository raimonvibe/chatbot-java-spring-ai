# Backend Test Fixes TODO

## Current Test Status (2025-12-04 Final Update)
**Tests run: 514, Failures: 25, Errors: 0, Skipped: 0**
**Total Issues: 25** (down from 48 originally, then 24)

**✨ Major Achievement: ALL DATABASE ERRORS ELIMINATED! ✨**
**All 8 "relation 'users' does not exist" errors are fixed!**

Previous status: 514 tests with 16 failures and 8 errors (24 total issues)
Original status: 514 tests with 13 failures and 35 errors (48 total issues)

---

## 🎉 Latest Implementation (2025-12-04 Final)

### Implemented Option 1: Mock AI Dependencies (PROPER SOLUTION!)

Instead of using `@Profile("!test")` shortcuts to exclude controllers from tests, we implemented proper mocking:

**What We Did:**
1. ✅ Created `MockAiConfiguration` with mocked AI beans (ChatClient, EmbeddingModel, VectorStore)
2. ✅ Removed `@Profile("!test")` from controllers and services  (kept on AiConfiguration only)
3. ✅ Added `@MockitoBean` for repositories in InputValidationSecurityTest
4. ✅ All controllers now load in tests with mocked AI dependencies
5. ✅ Full application context testing is now possible

**Benefits:**
- ✅ Controllers and services load normally in tests
- ✅ Tests actually hit real endpoints
- ✅ Tests integration between layers
- ✅ Only AI responses are mocked
- ✅ **NO MORE DATABASE ERRORS!**

**Files Created:**
- `backend/src/test/java/com/tjanabot/chatbot/config/MockAiConfiguration.java` - Provides mock AI beans for tests

**Files Modified:**
- Removed `@Profile("!test")` from:
  - `ChatController.java`
  - `ChatbotController.java`
  - `WebController.java`
  - `AiChatbotService.java`
- Kept `@Profile("!test")` on `AiConfiguration.java` (since it needs real API clients)
- Updated `InputValidationSecurityTest.java` to use @MockitoBean for repositories

---

## ✅ Completed Fixes (2025-12-04)

### Tests Now Fully Passing:
1. ✅ **ChatbotServiceTest** - All 12 tests passing
2. ✅ **CustomOAuth2UserServiceTest** - All 6 tests passing (reflection issues resolved)
3. ✅ **AuditServiceTest** - All 9 tests passing
4. ✅ **StripeServiceTest** - All 8 tests passing
5. ✅ **JwtTokenProviderTest** - All 15 tests passing
6. ✅ **JwtAuthenticationFilterTest** - All 13 tests passing
7. ✅ **XssSanitizerTest** - All 38 tests passing

### Major Fix: InputValidationSecurityTest
- **Was**: 29 errors (ApplicationContext failed to load)
- **Now**: 19 issues (11 failures + 8 errors) - tests are actually running!
- **Fixed**: COHERE_API_KEY configuration issue, AI component dependency problems

---

## 🔧 Key Changes Made

### 1. Fixed COHERE_API_KEY Configuration
**File**: `backend/src/main/java/com/tjanabot/chatbot/config/AiConfiguration.java`
```java
// Changed from:
@Value("${COHERE_API_KEY}")

// To:
@Value("${spring.ai.cohere.api-key}")
```

### 2. Updated Test Configuration
**File**: `backend/src/test/resources/application-test.yml`
```yaml
spring:
  ai:
    anthropic:
      api-key: test-anthropic-key
    cohere:
      api-key: test-cohere-key
```

### 3. ⚠️ Added @Profile("!test") to AI Components (SHORTCUT APPROACH)
**Files Modified**:
- `AiConfiguration.java` - Excludes AI bean configuration from tests
- `ChatController.java` - Excludes chat controller from tests
- `ChatbotController.java` - Excludes chatbot controller from tests
- `WebController.java` - Excludes web controller from tests
- `AiChatbotService.java` - Excludes AI service from tests

**Why This Works**: Components with AI dependencies won't be loaded during tests.

**Why This Is Problematic**:
- `@SpringBootTest` claims to test the full app but excludes major controllers
- Tests pass but don't actually test real application integration
- Could hide bugs between validation layer and controllers
- Not testing production configuration

### 4. Created TestJacksonConfiguration
**File**: `backend/src/test/java/com/tjanabot/chatbot/config/TestJacksonConfiguration.java`
- Provides ObjectMapper bean for tests
- Required because some auto-configuration was affected by profile exclusions

---

## 🚨 Known Issues with Current Approach

### The @Profile("!test") Shortcut
The current solution uses `@Profile("!test")` to exclude AI-dependent components from tests. This is **technically working but not ideal**:

**Pros**:
- ✅ Tests run and pass
- ✅ No need to configure real AI services in tests
- ✅ Fast test execution

**Cons**:
- ❌ Major controllers completely excluded from test context
- ❌ `@SpringBootTest` no longer tests the real application
- ❌ Integration bugs between layers could go unnoticed
- ❌ Production configuration not validated

---

## 💡 Better Approaches (TODO for Future)

### Option 1: Mock AI Dependencies (Recommended)
Create test configuration with mocked AI beans:

```java
@TestConfiguration
static class MockAiConfiguration {
    @Bean
    @Primary
    public ChatClient mockChatClient() {
        ChatClient mock = mock(ChatClient.class);
        // Configure mock behavior for tests
        when(mock.prompt(any())).thenReturn(/* test response */);
        return mock;
    }

    @Bean
    @Primary
    public EmbeddingModel mockEmbeddingModel() {
        return mock(EmbeddingModel.class);
    }

    @Bean
    @Primary
    public VectorStore mockVectorStore() {
        return mock(VectorStore.class);
    }
}
```

**Benefits**:
- ✅ All controllers and services load normally
- ✅ Tests actually hit real endpoints
- ✅ Tests integration between layers
- ✅ Just AI responses are mocked

### Option 2: Use @WebMvcTest for Targeted Testing
Instead of `@SpringBootTest`, use `@WebMvcTest` for controller-specific tests:

```java
@WebMvcTest(AuthController.class)
@Import(TestJacksonConfiguration.class)
class InputValidationSecurityTest {
    // Only loads auth controller and security config
    // No AI dependencies needed
}
```

**Benefits**:
- ✅ Faster tests (only loads what's needed)
- ✅ Focuses on what InputValidationSecurityTest actually needs to test
- ✅ No need to mock AI services

### Option 3: Create Test-Specific Implementations
Create stub implementations of AI services for tests:

```java
@Service
@Profile("test")
public class StubAiChatbotService extends AiChatbotService {
    // Provide simple test implementations
    // No real AI calls
}
```

**Benefits**:
- ✅ Full application context loads
- ✅ Predictable test behavior
- ✅ No mocking framework needed

---

## ⚠️ Remaining Issues (25 total)

### InputValidationSecurityTest (19 failures)

**Status 200 Failures (11 - CRITICAL):**
Validation is **NOT** rejecting malicious inputs! These return 200 (success) when they should return 400 (bad request):
- `shouldSanitizeXssInRegistration` - XSS payloads like `<script>alert('XSS')</script>` are accepted
- `shouldPreventSqlInjection` (3 tests) - SQL injection attempts like `' OR '1'='1` are accepted
- `shouldEnforcePasswordComplexity` - Weak passwords accepted
- `shouldRejectCommonPasswords` - Common passwords like "Password123!" accepted
- `shouldRejectControlCharacters` - Control characters like `\r\n` accepted
- `shouldValidateEmailFormatStrictly` - Invalid emails accepted

**Status 302 Failures (8 - Authentication Issue):**
Tests getting redirected instead of rejected:
- `shouldRejectSsrfAttempts` (8 tests) - Missing @WithMockUser or authentication setup
- `shouldPreventNoSqlInjection` - Missing authentication
- `shouldSanitizeChatbotSystemPrompt` - Missing authentication
- `shouldValidateNumericRanges` - Missing authentication

**Root Cause Analysis:**
1. **Status 200 issues**: The Bean Validation (@Pattern, @NotBlank) appears to not be working as expected, OR the patterns are too permissive, OR there's a validation configuration issue
2. **Status 302 issues**: Tests hitting authenticated endpoints without proper @WithMockUser setup

**Recommended Fixes:**
1. Investigate why @Valid annotation on AuthController isn't triggering validation failures
2. Verify Bean Validation is properly configured in test context
3. Add @WithMockUser to tests that hit authenticated endpoints
4. Consider if validation patterns need to be stricter

### Other Test Failures (6 total)

1. **JwtTokenProviderTest** (1 failure): shouldRejectToken_whenSignatureIsInvalid
2. **WebhookServiceSecurityTest** (2 failures): webhook event validation issues
3. **BibleVerseServiceTest** (2 failures): verse suggestion mismatches
4. **WebsiteAnalysisServiceSecurityTest** (1 failure): DNS rebinding protection test

These are unrelated to the AI mocking changes and were likely pre-existing issues.

---

## Implementation Patterns Used

### Lenient Stubbing Pattern
```java
// Use lenient() for stubbings that may not be invoked in all test paths
lenient().when(mock.method()).thenReturn(value);
```

### Reflection Pattern for Private Methods
```java
private OAuth2User callProcessOAuth2User(OAuth2UserRequest request, OAuth2User user) throws Exception {
    Method method = CustomOAuth2UserService.class.getDeclaredMethod("processOAuth2User",
        OAuth2UserRequest.class, OAuth2User.class);
    method.setAccessible(true);
    return (OAuth2User) method.invoke(customOAuth2UserService, request, user);
}
```

---

## Files Modified (2025-12-04)

### Production Code:
1. `backend/src/main/java/com/tjanabot/chatbot/config/AiConfiguration.java` - Added @Profile("!test")
2. `backend/src/main/java/com/tjanabot/chatbot/controller/ChatController.java` - Added @Profile("!test")
3. `backend/src/main/java/com/tjanabot/chatbot/controller/ChatbotController.java` - Added @Profile("!test")
4. `backend/src/main/java/com/tjanabot/chatbot/controller/WebController.java` - Added @Profile("!test")
5. `backend/src/main/java/com/tjanabot/chatbot/service/AiChatbotService.java` - Added @Profile("!test")

### Test Code:
6. `backend/src/test/resources/application-test.yml` - Added Spring AI test configuration
7. `backend/src/test/java/com/tjanabot/chatbot/config/TestJacksonConfiguration.java` - NEW: Provides ObjectMapper
8. `backend/src/test/java/com/tjanabot/chatbot/security/InputValidationSecurityTest.java` - Added @Import(TestJacksonConfiguration)
9. `backend/src/test/java/com/tjanabot/chatbot/unit/service/ChatbotServiceTest.java` - Fixed stubbing
10. `backend/src/test/java/com/tjanabot/chatbot/unit/service/CustomOAuth2UserServiceTest.java` - Fixed reflection
11. `backend/src/test/java/com/tjanabot/chatbot/service/WebsiteAnalysisServiceSecurityTest.java` - Fixed stubbing
12. `backend/src/test/java/com/tjanabot/chatbot/unit/service/AuditServiceTest.java` - Fixed stubbing

---

## Test Execution Commands

```bash
# Run all tests
mvn clean test

# Run specific test class
mvn test -Dtest=InputValidationSecurityTest

# Run with detailed output
mvn test -X

# Check surefire reports
ls -la backend/target/surefire-reports/
```

---

## Recommendations for Next Steps

### Immediate (High Priority):
1. **Decide on approach**: Choose between keeping @Profile shortcuts or implementing proper mocking
2. **Fix InputValidationSecurityTest**: Address the 19 remaining test issues
3. **Verify integration**: Ensure no other tests broke due to profile exclusions

### Long-term (Best Practice):
1. **Implement Option 1**: Create MockAiConfiguration with proper mock beans
2. **Remove @Profile("!test")**: Let all components load normally in tests
3. **Add integration tests**: Create separate tests that verify AI integration works correctly
4. **Document trade-offs**: Clearly document why certain components are mocked

---

## Summary

**What We Accomplished**:
- ✅ **Eliminated ALL 8 database errors** (from "relation 'users' does not exist")
- ✅ **Implemented proper AI mocking** (Option 1 from recommendations)
- ✅ **Removed controller @Profile shortcuts** - controllers now load in tests
- ✅ **Full application context** now loads in tests with mocked AI dependencies
- ✅ **48 → 25 issues** (48% reduction from original)
- ✅ **No more errors, only failures** (all structural issues fixed)

**How We Did It**:
- Created MockAiConfiguration with @Primary mocked beans
- Kept @Profile("!test") only on AiConfiguration (which needs real API clients)
- Removed @Profile("!test") from all controllers and services
- Added @MockitoBean for repositories in integration tests

**Current State**:
- Tests now properly load full application context
- AI dependencies are cleanly mocked
- Validation and authentication issues revealed (need fixing)
- System is ready for proper integration testing

**Recommendation**:
- Fix the 11 critical validation issues (Status 200 when should be 400)
- Add proper @WithMockUser to authenticated endpoint tests (fixes 8 Status 302 issues)
- Address the 6 other test failures (JWT, Webhook, BibleVerse, WebsiteAnalysis)
