# Backend Test Fixes TODO

## Test Failures Summary
Tests run: 515, Failures: 14, Errors: 41, Skipped: 0

## Tasks to Fix

### 1. ✅ ChatbotServiceTest - PARTIALLY FIXED
- **Issue**: NullPointerException - xssSanitizer is null
- **Status**: Added @Mock for XssSanitizer and default stubbing
- **Remaining**: Need to update `shouldSanitizeUserInput` test to properly mock sanitization behavior

### 2. CustomOAuth2UserServiceTest - TODO
- **Issue**: NullPointerException - OAuth2UserRequest.getClientRegistration() returns null
- **Files**: `/backend/src/test/java/com/tjanabot/chatbot/unit/service/CustomOAuth2UserServiceTest.java`
- **Tests Failing**:
  - shouldCreateNewUserForFirstTimeGoogleLogin:78
  - shouldHandleExistingEmailWithoutGoogleId:134
  - shouldHandleMissingOptionalAttributes:165
  - shouldReturnOAuth2UserWithCorrectAttributes:189
  - shouldSetLastLoginTimestamp:227
  - shouldUpdateExistingUserOnSubsequentLogin:108
- **Fix**: Add proper mocking for OAuth2UserRequest and ClientRegistration

### 3. UnnecessaryStubbingException - TODO
- **Issue**: Tests have unnecessary stubbings that need to be removed or made lenient
- **Files**:
  - WebsiteAnalysisServiceSecurityTest.java:198
  - AuditServiceTest.java:93
- **Fix**: Remove unnecessary stubbings or use lenient strictness

### 4. Other Test Failures - TODO
- Review and fix remaining 14 failures and 41 errors
- Check surefire reports in `/backend/target/surefire-reports` for detailed error logs

## Progress
- [x] Identified test failures
- [x] Fixed ChatbotServiceTest XssSanitizer mock (partial)
- [ ] Complete ChatbotServiceTest sanitization test fix
- [ ] Fix CustomOAuth2UserServiceTest OAuth2UserRequest mocking
- [ ] Fix UnnecessaryStubbingException errors
- [ ] Run full test suite to verify all fixes
- [ ] Address any remaining failures

## Notes
- Tests are currently failing in CI/CD pipeline
- Priority: Fix critical authentication and security-related tests first
- Ensure all mocks are properly initialized before test execution
