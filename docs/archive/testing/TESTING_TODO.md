# Testing & Deployment TODO Plan

**Document Version:** 1.0
**Created:** December 5, 2025
**Status:** Active Development

---

## IMMEDIATE FIXES (Critical - Deploy Blockers)

### 1. Fix Logging Configuration for Cloud Deployment ⚠️ CRITICAL
**Priority:** P0 - BLOCKER
**Status:** 🔴 Not Started

**Problem:**
- Application fails to start on Render because it cannot create `/app/logs/chatbot.log`
- File system is read-only or restricted in containerized environment
- Error: `Failed to create parent directories for [/app/logs/chatbot.log]`

**Solution:**
- [ ] Create custom `logback-spring.xml` configuration
- [ ] Use console logging for production environment
- [ ] Keep file logging only for local development
- [ ] Add conditional logging based on active profile

**Files to Modify:**
- Create: `backend/src/main/resources/logback-spring.xml`
- Update: `backend/src/main/resources/application.yml` (remove file logging config)

**Test Plan:**
- [ ] Test logging in local development (should write to file)
- [ ] Test logging in production profile (should only use console)
- [ ] Verify no file system access errors on startup

---

### 2. Fix AnthropicChatModel Bean Creation ⚠️ CRITICAL
**Priority:** P0 - BLOCKER
**Status:** 🔴 Not Started

**Problem:**
- Spring AI auto-configuration not creating `AnthropicChatModel` bean
- Error: `No qualifying bean of type 'org.springframework.ai.anthropic.AnthropicChatModel' available`
- AiConfiguration expects bean but Spring Boot isn't auto-configuring it

**Root Cause:**
- Missing Spring AI auto-configuration properties
- Possible version mismatch in Spring AI dependencies
- Auto-configuration may not be triggered correctly

**Solution:**
- [ ] Review `AiConfiguration.java` - check bean creation
- [ ] Add explicit `AnthropicChatModel` bean definition if auto-config fails
- [ ] Verify `spring-ai-anthropic` dependency version matches Spring Boot 4.0
- [ ] Check application.yml has correct Spring AI configuration properties
- [ ] Add `@EnableAutoConfiguration` if needed

**Files to Modify:**
- Review: `backend/src/main/java/com/tjanabot/chatbot/config/AiConfiguration.java`
- Review: `backend/pom.xml` (Spring AI BOM version)
- Review: `backend/src/main/resources/application.yml`

**Test Plan:**
- [ ] Application starts successfully
- [ ] AnthropicChatModel bean is created
- [ ] Chat functionality works end-to-end

---

### 3. Verify Port Binding for Render ⚠️ HIGH
**Priority:** P1 - HIGH
**Status:** 🔴 Not Started

**Problem:**
- Render shows: `No open ports detected, continuing to scan...`
- Application may not be binding to the correct port
- Render requires binding to `0.0.0.0` on port specified in `PORT` env var

**Current Config:**
```yaml
server:
  port: ${PORT:8081}
```

**Solution:**
- [ ] Verify server binds to `0.0.0.0` (not just localhost)
- [ ] Ensure `PORT` environment variable is set in Render (should be 10000)
- [ ] Add explicit server address binding if needed
- [ ] Check Tomcat connector configuration

**Files to Modify:**
- Review: `backend/src/main/resources/application.yml`
- Possibly add: Server configuration in main application class

**Test Plan:**
- [ ] Deploy to Render
- [ ] Verify "Tomcat started on port" message appears
- [ ] Verify Render detects open port
- [ ] Health check endpoint responds

---

## SECURITY IMPROVEMENTS (Based on Security Audit)

### 4. Add CSRF Protection Tests 🔒
**Priority:** P2 - MEDIUM
**Status:** 🟡 Planned

**Gap Identified:**
- No explicit CSRF token validation tests
- Architecture mentions security but tests are missing

**Implementation:**
- [ ] Create `CsrfProtectionTest.java`
- [ ] Test CSRF token generation on login
- [ ] Test CSRF token validation on state-changing operations (POST, PUT, DELETE)
- [ ] Test CSRF token rejection for invalid tokens
- [ ] Test CSRF exempt endpoints (public APIs)

**Test Cases:**
```java
- shouldGenerateCsrfToken_onLogin()
- shouldRejectRequest_withoutCsrfToken()
- shouldRejectRequest_withInvalidCsrfToken()
- shouldAcceptRequest_withValidCsrfToken()
- shouldExemptPublicEndpoints_fromCsrfCheck()
```

**Files to Create:**
- `backend/src/test/java/com/tjanabot/chatbot/security/CsrfProtectionTest.java`

---

### 5. Add Content Security Policy (CSP) Tests 🔒
**Priority:** P2 - MEDIUM
**Status:** 🟡 Planned

**Gap Identified:**
- CSP headers configured but not tested
- Need integration tests to verify headers

**Implementation:**
- [ ] Create `SecurityHeadersTest.java`
- [ ] Test CSP header presence
- [ ] Test CSP directives (script-src, style-src, etc.)
- [ ] Test X-Frame-Options header
- [ ] Test X-Content-Type-Options header
- [ ] Test Strict-Transport-Security header

**Test Cases:**
```java
- shouldSetContentSecurityPolicyHeader()
- shouldSetXFrameOptionsHeader()
- shouldSetXContentTypeOptionsHeader()
- shouldSetStrictTransportSecurityHeader()
- shouldSetReferrerPolicyHeader()
- shouldSetPermissionsPolicyHeader()
```

**Files to Create:**
- `backend/src/test/java/com/tjanabot/chatbot/security/SecurityHeadersTest.java`

---

### 6. Add Session Management Security Tests 🔒
**Priority:** P2 - MEDIUM
**Status:** 🟡 Planned

**Gap Identified:**
- No session fixation attack tests
- No session timeout tests
- No concurrent session tests

**Implementation:**
- [ ] Create `SessionSecurityTest.java`
- [ ] Test session ID changes after login
- [ ] Test session timeout enforcement
- [ ] Test concurrent session limits
- [ ] Test session invalidation on logout
- [ ] Test session hijacking prevention

**Test Cases:**
```java
- shouldChangeSessionId_afterSuccessfulLogin()
- shouldInvalidateSession_afterTimeout()
- shouldPreventSessionFixation_attacks()
- shouldInvalidateSession_onLogout()
- shouldLimitConcurrentSessions_perUser()
```

**Files to Create:**
- `backend/src/test/java/com/tjanabot/chatbot/security/SessionSecurityTest.java`

---

### 7. Add Clickjacking Prevention Tests 🔒
**Priority:** P3 - LOW
**Status:** 🟡 Planned

**Gap Identified:**
- X-Frame-Options configured but not explicitly tested

**Implementation:**
- [ ] Test X-Frame-Options: SAMEORIGIN
- [ ] Test frame-ancestors CSP directive
- [ ] Test embedding in iframe is blocked

**Test Cases:**
```java
- shouldSetXFrameOptions_toSameOrigin()
- shouldBlockEmbedding_inIframe()
- shouldAllowEmbedding_fromSameOrigin()
```

**Integration with:**
- `SecurityHeadersTest.java` (combine with #5)

---

## DEPLOYMENT IMPROVEMENTS

### 8. Add Health Check Endpoint 💚
**Priority:** P1 - HIGH
**Status:** 🟡 Planned

**Requirement:**
- Render needs a health check endpoint
- Database connection health check
- External API health checks (optional)

**Implementation:**
- [ ] Add Spring Boot Actuator dependency
- [ ] Configure `/actuator/health` endpoint
- [ ] Add custom health indicators:
  - Database connectivity
  - Anthropic API (optional)
  - Cohere API (optional)
- [ ] Configure in Render dashboard

**Files to Modify:**
- `backend/pom.xml` - add actuator dependency
- `backend/src/main/resources/application.yml` - configure actuator
- Render dashboard - set health check path

---

### 9. Add Database Migration Strategy 📊
**Priority:** P1 - HIGH
**Status:** 🟡 Planned

**Current Issue:**
- Using `ddl-auto: update` in production (risky)
- No migration versioning
- Cannot rollback schema changes

**Recommendation:**
- [ ] Add Flyway or Liquibase
- [ ] Create initial schema migration
- [ ] Version control all schema changes
- [ ] Change `ddl-auto` to `validate` in production

**Files to Create:**
- `backend/src/main/resources/db/migration/V1__Initial_schema.sql`
- Update: `backend/pom.xml` (add Flyway)
- Update: `backend/src/main/resources/application.yml`

---

### 10. Add Environment-Specific Configuration 🔧
**Priority:** P2 - MEDIUM
**Status:** 🟡 Planned

**Improvement:**
- Create separate configs for dev, test, staging, prod
- Use Spring profiles effectively

**Implementation:**
- [ ] Create `application-dev.yml`
- [ ] Create `application-test.yml`
- [ ] Create `application-staging.yml`
- [ ] Create `application-production.yml`
- [ ] Document environment variables required for each profile

**Files to Create:**
- `backend/src/main/resources/application-dev.yml`
- `backend/src/main/resources/application-test.yml`
- `backend/src/main/resources/application-staging.yml`
- `backend/src/main/resources/application-production.yml`

---

## TESTING IMPROVEMENTS

### 11. Add Integration Tests for Full Flows 🧪
**Priority:** P2 - MEDIUM
**Status:** 🟡 Planned

**Coverage Needed:**
- [ ] Full user registration → login → create chatbot flow
- [ ] Payment flow: subscribe → webhook → access granted
- [ ] Chat flow: send message → AI response → save to DB
- [ ] OAuth flow: Google login → user creation → JWT generation

**Files to Create:**
- `backend/src/test/java/com/tjanabot/chatbot/integration/UserRegistrationFlowIT.java`
- `backend/src/test/java/com/tjanabot/chatbot/integration/PaymentFlowIT.java`
- `backend/src/test/java/com/tjanabot/chatbot/integration/ChatFlowIT.java`
- `backend/src/test/java/com/tjanabot/chatbot/integration/OAuth2FlowIT.java`

---

### 12. Add Performance Tests 📈
**Priority:** P3 - LOW
**Status:** 🟡 Planned

**Coverage Needed:**
- [ ] Load testing for chat endpoints
- [ ] Database query performance
- [ ] Rate limiting effectiveness
- [ ] Memory usage under load

**Tools:**
- JMeter or Gatling
- Spring Boot Actuator metrics

---

## DOCUMENTATION UPDATES

### 13. Update Deployment Documentation 📚
**Priority:** P2 - MEDIUM
**Status:** 🟡 Planned

**Updates Needed:**
- [ ] Document Render deployment process
- [ ] Document environment variables setup
- [ ] Document database setup on Render
- [ ] Document troubleshooting common issues
- [ ] Add deployment checklist

**Files to Update:**
- `README.md`
- Create: `DEPLOYMENT.md`
- Create: `TROUBLESHOOTING.md`

---

### 14. Create Security Testing Guide 🔒
**Priority:** P2 - MEDIUM
**Status:** 🟡 Planned

**Documentation:**
- [ ] How to run security tests
- [ ] How to interpret results
- [ ] Security testing checklist
- [ ] Common vulnerabilities to test for
- [ ] Remediation guidelines

**Files to Create:**
- `SECURITY_TESTING.md`

---

## COMPLIANCE & GOVERNANCE

### 15. GDPR Compliance Features 🇪🇺
**Priority:** P1 - HIGH (if serving EU users)
**Status:** 🟡 Planned

**Requirements:**
- [ ] User data export functionality
- [ ] Right to be forgotten (data deletion)
- [ ] Consent management
- [ ] Data retention policies
- [ ] Privacy policy updates

**Implementation:**
- [ ] Add `/api/user/export-data` endpoint
- [ ] Add `/api/user/delete-account` endpoint
- [ ] Add consent tracking in database
- [ ] Add data retention job (scheduled)

---

### 16. Add Audit Log Retention Policy 📝
**Priority:** P2 - MEDIUM
**Status:** 🟡 Planned

**Requirement:**
- Define log retention period (e.g., 90 days)
- Automated cleanup of old logs
- Archive critical security events

**Implementation:**
- [ ] Add scheduled job to clean old audit logs
- [ ] Archive security events before deletion
- [ ] Configure retention period via environment variable

---

## MONITORING & ALERTING

### 17. Add Real-Time Security Alerting 🚨
**Priority:** P1 - HIGH
**Status:** 🟡 Planned (Future Enhancement)

**Requirements:**
- Alert on fraud detection (CRITICAL risk level)
- Alert on failed login spikes
- Alert on payment failures
- Alert on SSRF attempts

**Implementation:**
- [ ] Integrate with alerting service (e.g., PagerDuty, Slack)
- [ ] Configure alert thresholds
- [ ] Add email notifications for security events

---

## SUCCESS CRITERIA

### Deployment Success ✅
- [ ] Application starts without errors
- [ ] Database connections successful
- [ ] All health checks passing
- [ ] No file system permission errors
- [ ] Port binding successful
- [ ] Environment variables loaded correctly

### Security Testing Success ✅
- [ ] All existing tests passing (100%)
- [ ] New security tests added and passing
- [ ] No critical vulnerabilities detected
- [ ] OWASP Top 10 coverage maintained
- [ ] Code coverage > 80% for security components

### Production Readiness ✅
- [ ] HTTPS enforced
- [ ] Security headers configured
- [ ] Rate limiting active
- [ ] Fraud detection operational
- [ ] Audit logging working
- [ ] Backups configured
- [ ] Monitoring dashboards set up

---

## TIMELINE ESTIMATE

### Phase 1: Critical Fixes (1-2 days) 🔥
- Fix logging configuration
- Fix AnthropicChatModel bean
- Fix port binding
- Deploy successfully to Render

### Phase 2: Security Tests (3-5 days) 🔒
- Add CSRF protection tests
- Add security headers tests
- Add session security tests

### Phase 3: Deployment Improvements (2-3 days) 🚀
- Add health checks
- Add database migrations
- Environment-specific configs

### Phase 4: Advanced Features (5-7 days) 🎯
- GDPR compliance
- Advanced monitoring
- Performance testing

---

## NOTES

- All security tests should be added to CI/CD pipeline
- Each PR should maintain or improve test coverage
- Security tests must pass before merging to main
- Production deployments require all P0 and P1 items completed

---

**Status Legend:**
- 🔴 Not Started
- 🟡 Planned
- 🟢 In Progress
- ✅ Completed
- ⚠️ Blocked

**Priority Legend:**
- P0: Blocker (must fix immediately)
- P1: High (fix within 1 week)
- P2: Medium (fix within 1 month)
- P3: Low (fix when possible)
