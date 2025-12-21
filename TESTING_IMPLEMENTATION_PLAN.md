# Comprehensive E2E Testing Implementation Plan

> **Created**: 2025-12-17
> **Scope**: Complete E2E test coverage for Prayer-Chat application
> **Focus**: Google OAuth, Stripe payments, and entire application workflow

---

## Quick Summary

**Total New Tests**: ~120-150 E2E tests
- **Backend**: 8 new E2E test files + enhancements to 3 existing + 3 new integration tests
- **Frontend**: 12 new E2E test files (Playwright) + 3 new page unit tests + enhancements to 3 existing

**Timeline**: 5 weeks (phased implementation)
**Priority**: E2E tests first, then enhancements

---

## Phase 1: Foundation Setup (Week 1) - START HERE

### Frontend Setup
```bash
cd frontend
npm install -D @playwright/test
npx playwright install
```

### Create These Files First

1. **`frontend/playwright.config.ts`** - Playwright configuration
2. **`backend/src/test/java/com/tjanabot/chatbot/helpers/E2ETestBase.java`** - Base class for backend E2E tests
3. **`backend/src/test/java/com/tjanabot/chatbot/helpers/ApiTestClient.java`** - REST Assured wrapper
4. **`frontend/e2e/helpers/auth.ts`** - Authentication helper
5. **`frontend/e2e/helpers/api-mock.ts`** - API mocking utilities
6. **`frontend/e2e/fixtures/users.ts`** - Test user fixtures

### Validation
- Write one sample E2E test (backend + frontend) to ensure setup works
- Run the sample test successfully before proceeding to Phase 2

---

## Backend E2E Tests (8 files)

### Core User Journeys
**File**: `backend/src/test/java/com/tjanabot/chatbot/e2e/UserJourneyE2ETest.java`
- Complete flow: Registration → Login → Create chatbot → Chat
- Complete flow: Login → Pricing → Stripe checkout → Webhook → Active subscription
- Subscription upgrade/downgrade flows
- Cancel subscription flow

### API E2E Tests (`backend/src/test/java/com/tjanabot/chatbot/e2e/api/`)

1. **AuthApiE2ETest.java**
   - POST /api/auth/register → /api/auth/login → GET /api/chatbots (authenticated)
   - OAuth2 callback flow with Google (mocked)
   - JWT token lifecycle (issue → refresh → expire)
   - Rate limiting on failed login attempts

2. **ChatbotApiE2ETest.java**
   - Full CRUD lifecycle: Create → Read → Update → Delete
   - POST /api/chatbots → POST /api/chatbots/{id}/analyze → GET /api/chatbots/{id}/embed
   - Chatbot ownership verification
   - Multi-user scenarios

3. **ChatApiE2ETest.java**
   - POST /api/chat/{chatbotId} → GET conversation history
   - Multi-turn conversation with session management
   - Language switching mid-conversation
   - Public chatbot access via embed code
   - Rate limiting enforcement

4. **SubscriptionApiE2ETest.java**
   - GET /api/subscription/status → POST /api/subscription/create-checkout-session
   - Stripe checkout → webhook → subscription activation → chatbot access
   - Subscription upgrade: FREE → BASIC → PRO
   - Subscription downgrade and cancellation
   - Grace period handling

5. **StripeWebhookE2ETest.java**
   - Complete webhook lifecycle with signature verification
   - invoice.payment_succeeded → subscription activation
   - customer.subscription.updated → plan change reflection
   - customer.subscription.deleted → subscription termination
   - Idempotency and duplicate event handling

### Security E2E Tests (`backend/src/test/java/com/tjanabot/chatbot/e2e/security/`)

6. **SecurityE2ETest.java**
   - XSS attack prevention across all inputs
   - SQL injection prevention
   - CSRF protection
   - Unauthorized access attempts
   - JWT token manipulation attempts
   - Rate limiting enforcement

7. **ErrorHandlingE2ETest.java**
   - External service failures (Stripe API down, AI service down)
   - Database connection loss
   - Invalid data handling
   - Concurrent modification conflicts

---

## Frontend E2E Tests (12 files using Playwright)

### Page Tests (`frontend/e2e/pages/`)

1. **home.spec.ts**
   - Navigate to home → verify hero section → click CTA → redirect to login
   - Feature showcase interactions
   - Mobile responsiveness

2. **login.spec.ts**
   - Click Google OAuth button → mock OAuth flow → redirect to dashboard
   - Error handling (OAuth failure, network error)
   - Redirect to pricing if no subscription

3. **pricing.spec.ts**
   - View pricing tiers → select plan → initiate Stripe checkout
   - Plan comparison and feature highlights
   - Stripe checkout integration (mock)

4. **dashboard.spec.ts**
   - View subscription status
   - List all chatbots
   - Create new chatbot → redirect to chatbot detail
   - Edit chatbot → save changes
   - Delete chatbot → confirm deletion

5. **chatbot-detail.spec.ts**
   - View chatbot details
   - Analyze website → show progress → display results
   - Get embed code → copy to clipboard
   - Delete chatbot

6. **chatbot-preview.spec.ts**
   - Load chatbot by ID
   - Send messages → receive responses
   - Quick reply interactions
   - Multi-turn conversation
   - Session management
   - Error handling

### Flow Tests (`frontend/e2e/flows/`)

7. **new-user-flow.spec.ts**
   - Home → Login (OAuth) → Dashboard (welcome) → Pricing → Checkout → Dashboard
   - Complete onboarding experience
   - Verify FREE plan auto-creation

8. **create-chatbot-flow.spec.ts**
   - Login → Dashboard → Create chatbot → Analyze website → Train → Test chat
   - Complete chatbot creation and testing workflow

9. **subscription-upgrade-flow.spec.ts**
   - Login → Dashboard → Pricing → Select upgrade → Stripe checkout → Webhook → Dashboard
   - Verify new features unlocked

10. **chatbot-usage-flow.spec.ts**
    - Load chatbot embed → send message → receive response → continue conversation
    - End-to-end chat experience

### Integration Tests (`frontend/e2e/components/`)

11. **chat-interface-integration.spec.ts**
    - ChatInterface component with real API calls (mocked backend)
    - Real-time message updates

12. **navigation-integration.spec.ts**
    - Full navigation flow across all pages
    - Auth state persistence
    - Protected route redirects

---

## Enhancements to Existing Tests

### Backend

**AuthControllerIT.java** - Add 5 tests:
- OAuth2 success handler flow
- JWT token refresh
- Concurrent login attempts
- Account lockout after failed attempts
- Session management

**ChatbotControllerIT.java** - Add 5 tests:
- Chatbot lifecycle (create → train → activate → use → deactivate)
- Concurrent access (two users editing same bot)
- Embed code generation and validation
- Chatbot cloning

**StripeWebhookControllerIT.java** - Add 5 tests:
- Timestamp verification (reject old events for replay attack prevention)
- Webhook retry mechanism
- Partial payment handling
- Subscription trial period
- Proration calculation

**New Integration Tests** (3 files):
- `ChatControllerIT.java` - Chat message processing with AI service (mocked), session management
- `SubscriptionControllerIT.java` - Subscription status checks, plan changes
- `AuditLogControllerIT.java` - Audit log creation, export, filtering

### Frontend

**ChatInterface.test.tsx** - Add 4 tests:
- WebSocket connection (if implemented)
- File upload (if supported)
- Markdown rendering
- Conversation export

**Message.test.tsx** - Add 3 tests:
- Timestamp formatting (different locales)
- Message editing (if supported)
- Message deletion (if supported)

**api.test.ts** - Add 4 tests:
- Retry logic
- Request cancellation
- Concurrent request handling
- Authentication token refresh

**New Page Unit Tests** (3 files):
- `app/dashboard/__tests__/page.test.tsx` - Rendering, loading states, error states
- `app/pricing/__tests__/page.test.tsx` - Plan comparison rendering
- `app/login/__tests__/page.test.tsx` - OAuth button, error messages

---

## External Service Mocking

### Services to Mock
1. **Google OAuth2** - WireMock (backend) + Playwright route interception (frontend)
2. **Stripe API** - WireMock + Stripe test event builder
3. **Anthropic AI (Claude)** - WireMock with fixture responses
4. **Cohere Embeddings** - WireMock with deterministic responses

### Backend WireMock Example
```java
@RegisterExtension
static WireMockExtension wireMockServer = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

@BeforeAll
static void setupWireMock() {
    // Mock Google OAuth
    stubFor(post(urlEqualTo("/oauth2/token"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"access_token\": \"test_token\"}")));
}
```

### Frontend Playwright Example
```typescript
test.beforeEach(async ({ page }) => {
  // Mock API calls
  await page.route('**/api/chatbots', async route => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(mockChatbots)
    });
  });
});
```

---

## Implementation Phases

### Phase 1: Foundation (Week 1) ⭐ PRIORITY HIGH
**Goal**: Set up E2E infrastructure

**Tasks**:
1. Install Playwright
2. Create Playwright config
3. Create E2E base classes (backend)
4. Create test helpers and fixtures
5. Set up WireMock for external services
6. Write one sample E2E test to validate setup

**Deliverables**:
- ✅ Playwright installed and configured
- ✅ E2E base classes created
- ✅ Sample E2E test passing
- ✅ CI/CD pipeline configured

### Phase 2: Critical User Journeys (Week 2) ⭐ PRIORITY HIGH
**Goal**: Test core business flows E2E

**Backend E2E**:
- UserJourneyE2ETest.java
- AuthApiE2ETest.java
- SubscriptionApiE2ETest.java
- StripeWebhookE2ETest.java

**Frontend E2E**:
- new-user-flow.spec.ts
- create-chatbot-flow.spec.ts
- subscription-upgrade-flow.spec.ts
- login.spec.ts

**Deliverables**:
- ✅ All critical user journeys tested
- ✅ Payment/subscription flows validated
- ✅ Authentication flows covered

### Phase 3: API E2E Coverage (Week 3) 📊 PRIORITY MEDIUM-HIGH
**Goal**: Complete API endpoint E2E testing

**Backend E2E**:
- ChatbotApiE2ETest.java
- ChatApiE2ETest.java
- SecurityE2ETest.java
- Enhancements to existing integration tests

**Frontend E2E**:
- dashboard.spec.ts
- pricing.spec.ts
- chatbot-detail.spec.ts
- chatbot-preview.spec.ts

**Deliverables**:
- ✅ All REST endpoints covered
- ✅ All main pages have E2E coverage
- ✅ Enhanced integration tests

### Phase 4: Component & Edge Cases (Week 4) 📝 PRIORITY MEDIUM
**Goal**: Fill testing gaps and edge cases

**Backend**:
- ErrorHandlingE2ETest.java
- ChatControllerIT.java
- SubscriptionControllerIT.java
- AuditLogControllerIT.java

**Frontend**:
- home.spec.ts
- Component integration tests
- Page-level unit tests
- Mobile responsiveness tests

**Deliverables**:
- ✅ Edge cases covered
- ✅ Component integration tests
- ✅ Mobile E2E testing

### Phase 5: Optimization & CI/CD (Week 5) 🔧 PRIORITY LOW-MEDIUM
**Goal**: Optimize test suite and CI/CD pipeline

**Tasks**:
- Optimize test execution time (parallel execution)
- Reduce flaky tests
- Add test result reporting and dashboards
- Set up test coverage tracking
- Document test strategy

**Deliverables**:
- ✅ Fast, reliable test suite
- ✅ Comprehensive CI/CD pipeline
- ✅ Test documentation

---

## Success Criteria

- ✅ ~120-150 new E2E tests created
- ✅ All critical user journeys tested E2E
- ✅ Backend coverage maintains/improves 70% (JaCoCo)
- ✅ Frontend coverage maintains/improves 80%
- ✅ All tests pass in CI/CD pipeline
- ✅ Test execution < 15 minutes total
- ✅ Zero flaky tests
- ✅ Comprehensive test documentation

---

## Critical Files Reference

### Files to Create (Infrastructure)
- `/home/stefan/Documenten/web development/022chatbot-java-spring-ai/frontend/playwright.config.ts`
- `/home/stefan/Documenten/web development/022chatbot-java-spring-ai/backend/src/test/java/com/tjanabot/chatbot/helpers/E2ETestBase.java`
- `/home/stefan/Documenten/web development/022chatbot-java-spring-ai/backend/src/test/java/com/tjanabot/chatbot/helpers/ApiTestClient.java`
- `/home/stefan/Documenten/web development/022chatbot-java-spring-ai/frontend/e2e/helpers/auth.ts`

### Files to Reference (Existing Implementation)
- `/home/stefan/Documenten/web development/022chatbot-java-spring-ai/backend/src/main/java/com/tjanabot/chatbot/security/OAuth2AuthenticationSuccessHandler.java`
- `/home/stefan/Documenten/web development/022chatbot-java-spring-ai/backend/src/main/java/com/tjanabot/chatbot/service/StripeService.java`
- `/home/stefan/Documenten/web development/022chatbot-java-spring-ai/backend/src/main/java/com/tjanabot/chatbot/controller/StripeWebhookController.java`
- `/home/stefan/Documenten/web development/022chatbot-java-spring-ai/backend/src/test/java/com/tjanabot/chatbot/helpers/TestDataBuilder.java`
- `/home/stefan/Documenten/web development/022chatbot-java-spring-ai/frontend/app/login/page.tsx`
- `/home/stefan/Documenten/web development/022chatbot-java-spring-ai/frontend/app/pricing/page.tsx`
- `/home/stefan/Documenten/web development/022chatbot-java-spring-ai/frontend/app/dashboard/page.tsx`
- `/home/stefan/Documenten/web development/022chatbot-java-spring-ai/frontend/jest.setup.js`

---

## Testing Best Practices (2025)

### Backend (Spring Boot)
- Use `@ExtendWith(MockitoExtension.class)` for unit tests
- `@DataJpaTest` for repository tests, `@SpringBootTest` for integration tests
- Keep unit tests fast (milliseconds)
- Use descriptive test method names: `shouldDoSomethingWhenCondition()`
- Constructor injection for better testability

### Frontend (Next.js/React)
- Test components as users interact with them (React Testing Library principle)
- Mock API calls, browser APIs, third-party libraries
- Use Arrange-Act-Assert pattern
- Test error states and edge cases, not just happy path
- Use `@testing-library/jest-dom` matchers

### E2E Testing
- Keep tests independent (no shared state)
- Use Page Object Model for Playwright tests
- Centralize test data in fixtures
- Mock external services (never call real APIs)
- Fix flaky tests immediately

---

## Research Sources

- [Spring Boot Testing with JUnit 5 and Mockito (2025)](https://medium.com/@rasinthadilshan9/spring-boot-testing-with-junit-and-mockito-complete-guide-c18d9444d409)
- [Testing Next.js with Jest and React Testing Library (2025)](https://medium.com/@sureshdotariya/testing-next-js-components-with-jest-and-react-testing-library-in-2025-478ecf7dcb7d)
- [Best practices for testing Stripe webhooks](https://launchdarkly.com/blog/best-practices-for-testing-stripe-webhook-event-processing/)
- [Stripe Java webhook tests](https://github.com/stripe/stripe-java/blob/master/src/test/java/com/stripe/net/WebhookTest.java)

---

## Next Steps

1. **Start with Phase 1** - Set up infrastructure (Week 1)
2. **Review the plan** - Ensure alignment with your goals
3. **Begin implementation** - Follow the phases sequentially
4. **Track progress** - Mark completed phases and tests
5. **Adjust as needed** - Adapt the plan based on discoveries

**Questions or clarifications?** Feel free to ask before starting implementation!

---

*Plan created: 2025-12-17*
*Ready for implementation in next session*
