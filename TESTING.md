# Testing - Prayer-Chat AI Chatbot

How to run tests, where they live, and how to write them.

---

## Quick start

**Backend (all tests):**
```bash
cd backend
mvn clean test
```

**Backend (single class):**
```bash
mvn test -Dtest=ChatbotServiceTest
```

**Backend (with coverage):**
```bash
mvn test jacoco:report
# Report: target/site/jacoco/index.html
```

**Frontend unit tests:**
```bash
cd frontend
npm test
```

**Frontend E2E (Playwright):**
```bash
cd frontend
npx playwright test
```

**Faster backend runs (parallel):**
```bash
mvn -T 4 test
```

---

## Test structure

### Backend (`backend/src/test/java/`)

- **Unit:** services, utils (e.g. `ChatbotServiceTest`, `StripeServiceTest`, `XssSanitizerTest`)
- **Integration:** controllers, repos (e.g. `SubscriptionControllerIT`)
- **E2E:** API flows, security (e.g. `SubscriptionApiE2ETest`)
- **Helpers:** `ApiTestClient`, `WebTestClientApiTestClient`, `E2ETestBase`, test data builders

### Frontend (`frontend/`)

- **Unit:** Jest in `__tests__/` (e.g. components)
- **E2E:** Playwright in `e2e/` (pages, flows, fixtures)
- Config: `jest.config.js`, `playwright.config.ts`

---

## What’s covered

- **Security:** input validation (XSS, SQL/NoSQL injection, SSRF), URL validation, webhook signatures, rate limiting, JWT, OAuth-related flows
- **Services:** chatbot CRUD, Stripe checkout/portal, Bible verse suggestion, webhooks, audit logging
- **API:** subscription checkout/portal (auth, 503 when Stripe not configured, return URL validation), chatbots, auth

Run `mvn test` (or CI) for current counts and status. Coverage reports: `mvn test jacoco:report` then open `target/site/jacoco/index.html`.

---

## Best practices

- **FIRST:** Fast, Independent, Repeatable, Self-validating, Timely
- **AAA:** Arrange (set up) → Act (call code) → Assert (verify)
- **One scenario per test** – avoid testing create + update + delete in a single test
- Prefer **constructor injection** in services so tests can pass mocks easily
- Use **test data builders** or small fixtures instead of huge inline objects
- Keep tests **independent** (no shared mutable state) and **repeatable** (no flaky timeouts or network)

---

## CI/CD

Tests run on push and pull requests (see `.github/workflows/`). Fix failing tests before merging.

---

## More detail

- **Full strategy** (pyramid, security testing, Stripe/OAuth testing): see `docs/archive/testing/` (historical strategy/implementation docs).
- **Backend test details:** see `docs/archive/backend/TEST_DOCUMENTATION.md` if you need per-suite descriptions.

---

*Merged from TESTING.md, TESTING_GUIDE.md, TESTING_STRATEGY.md, and TESTING_BEST_PRACTICES.md.*
