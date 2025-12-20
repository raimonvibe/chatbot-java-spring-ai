# Testing Guide - TjanaBot AI Chatbot

> **Comprehensive guide** for running, writing, and debugging tests in the TjanaBot application.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Test Structure](#test-structure)
3. [Running Tests](#running-tests)
4. [Writing Tests](#writing-tests)
5. [Coverage Reports](#coverage-reports)
6. [Troubleshooting](#troubleshooting)
7. [CI/CD Integration](#cicd-integration)
8. [Best Practices](#best-practices)

---

## Quick Start

### Prerequisites

- Java 17+
- Node.js 20+
- Docker (for integration tests)
- Maven 3.9+
- npm/npx

### Run All Tests

```bash
# Backend tests
cd backend
mvn clean test

# Frontend unit tests
cd frontend
npm test

# Frontend E2E tests
cd frontend
npx playwright test
```

---

## Test Structure

### Backend Tests (`backend/src/test/java/`)

```
src/test/java/com/tjanabot/chatbot/
├── unit/                     # Unit tests
│   ├── service/             # Service layer tests
│   └── util/                # Utility tests
├── integration/             # Integration tests
│   ├── controller/          # API endpoint tests
│   └── repository/          # Database tests
├── e2e/                     # End-to-end tests
│   ├── api/                 # API flow tests
│   └── security/            # Security tests
└── helpers/                 # Test utilities
    ├── E2ETestBase.java    # Base class for E2E tests
    ├── ApiTestClient.java  # REST client helper
    └── TestDataBuilder.java # Test data factory
```

### Frontend Tests (`frontend/`)

```
frontend/
├── __tests__/              # Jest unit tests
│   └── components/         # Component tests
├── e2e/                    # Playwright E2E tests
│   ├── pages/              # Page-level tests
│   ├── flows/              # User journey tests
│   ├── components/         # Component integration tests
│   ├── helpers/            # Test helpers
│   └── fixtures/           # Test data
├── jest.config.js          # Jest configuration
└── playwright.config.ts    # Playwright configuration
```

---

## Running Tests

### Backend

#### Unit Tests Only

```bash
cd backend
mvn test
```

#### Integration Tests Only

```bash
cd backend
mvn verify -DskipUnitTests
```

#### All Tests (Unit + Integration + E2E)

```bash
cd backend
mvn clean verify
```

#### Run Specific Test Class

```bash
mvn test -Dtest=ChatbotServiceTest
```

#### Run Tests in Parallel (Faster)

```bash
mvn test -Dparallel=classes -DthreadCount=4
```

### Frontend

#### Unit Tests

```bash
cd frontend
npm test                    # Watch mode
npm run test:ci             # CI mode with coverage
npm run test:coverage       # Generate coverage report
```

#### E2E Tests

```bash
cd frontend
npx playwright test                     # Run all E2E tests
npx playwright test --headed            # Run with visible browser
npx playwright test --debug             # Debug mode
npx playwright test --ui                # Interactive UI mode
npx playwright test home.spec.ts        # Run specific test file
```

#### E2E Tests on Specific Browser

```bash
npx playwright test --project=chromium
npx playwright test --project=firefox
npx playwright test --project=webkit
```

#### E2E Tests in Parallel

```bash
npx playwright test --workers=4
```

---

## Writing Tests

### Backend Unit Test Example

```java
@ExtendWith(MockitoExtension.class)
@DisplayName("Chatbot Service Tests")
class ChatbotServiceTest {

    @Mock
    private ChatbotRepository chatbotRepository;

    @InjectMocks
    private ChatbotService chatbotService;

    @Test
    @DisplayName("Should create chatbot successfully")
    void shouldCreateChatbotSuccessfully() {
        // Arrange
        Chatbot chatbot = TestDataBuilder.createTestChatbot();
        when(chatbotRepository.save(any(Chatbot.class))).thenReturn(chatbot);

        // Act
        Chatbot result = chatbotService.create(chatbot);

        // Assert
        assertNotNull(result);
        assertEquals("Test Bot", result.getName());
        verify(chatbotRepository, times(1)).save(any(Chatbot.class));
    }
}
```

### Backend Integration Test Example

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Chatbot Controller Integration Tests")
class ChatbotControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should get all chatbots")
    void shouldGetAllChatbots() throws Exception {
        mockMvc.perform(get("/api/chatbots")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThan(0))));
    }
}
```

### Frontend Unit Test Example

```typescript
import { render, screen, fireEvent } from '@testing-library/react';
import ChatInterface from './ChatInterface';

describe('ChatInterface', () => {
  it('should send message when button clicked', () => {
    const onSend = jest.fn();
    render(<ChatInterface onSend={onSend} />);

    const input = screen.getByRole('textbox');
    const button = screen.getByRole('button', { name: /send/i });

    fireEvent.change(input, { target: { value: 'Hello' } });
    fireEvent.click(button);

    expect(onSend).toHaveBeenCalledWith('Hello');
  });
});
```

### Frontend E2E Test Example

```typescript
import { test, expect } from '@playwright/test';

test('should complete user registration flow', async ({ page }) => {
  await page.goto('/login');

  await page.getByRole('button', { name: /sign in with google/i }).click();

  await expect(page).toHaveURL(/dashboard/);
  await expect(page.getByText(/welcome/i)).toBeVisible();
});
```

---

## Coverage Reports

### Backend Coverage (JaCoCo)

```bash
cd backend
mvn clean test jacoco:report

# View report
open target/site/jacoco/index.html
```

**Coverage Thresholds:**
- Line Coverage: 70%
- Branch Coverage: 70%

### Frontend Coverage (Istanbul/Jest)

```bash
cd frontend
npm run test:coverage

# View report
open coverage/lcov-report/index.html
```

**Coverage Thresholds:**
- Branches: 80%
- Functions: 70%
- Lines: 80%
- Statements: 80%

---

## Troubleshooting

### Common Issues

#### 1. Tests Fail with "Port Already in Use"

**Problem:** Another process is using the test port.

**Solution:**
```bash
# Find and kill the process
lsof -ti:8081 | xargs kill -9  # Backend (local development)
lsof -ti:3000 | xargs kill -9  # Frontend
```

#### 2. Testcontainers Fails to Start

**Problem:** Docker not running or permissions issue.

**Solution:**
```bash
# Start Docker
sudo systemctl start docker

# Add user to docker group
sudo usermod -aG docker $USER
newgrp docker
```

#### 3. Playwright Tests Fail with "Browser Not Installed"

**Problem:** Playwright browsers not installed.

**Solution:**
```bash
cd frontend
npx playwright install --with-deps
```

#### 4. Frontend Tests Fail with Module Not Found

**Problem:** Dependencies not installed.

**Solution:**
```bash
cd frontend
rm -rf node_modules package-lock.json
npm install
```

#### 5. Backend Tests Fail with "Cannot Open Module"

**Problem:** Java module access issues.

**Solution:** Already configured in `pom.xml` with `--add-opens` flags.

#### 6. Coverage Below Threshold

**Problem:** Not enough test coverage.

**Solution:**
```bash
# Identify uncovered code
mvn jacoco:report  # Backend
npm run test:coverage  # Frontend

# Write tests for uncovered lines
```

### Flaky Test Detection

If a test fails intermittently:

1. **Add retries** (Playwright already configured)
2. **Increase timeouts** if network-dependent
3. **Fix race conditions** with proper waits
4. **Isolate test data** to avoid conflicts

```typescript
// Good: Explicit wait
await page.waitForLoadState('networkidle');

// Bad: Fixed timeout
await page.waitForTimeout(1000);
```

---

## CI/CD Integration

### GitHub Actions Workflow

Tests run automatically on:
- Push to `main` or `develop`
- Pull requests to `main` or `develop`
- Manual workflow dispatch

### Workflow Jobs

1. **backend-test**: Unit + Integration tests
2. **frontend-test**: Jest unit tests
3. **e2e-test**: Playwright E2E tests
4. **security-scan**: Security vulnerability scanning
5. **code-quality**: SonarCloud analysis

### Viewing Test Results

- **GitHub Actions**: Check the "Actions" tab
- **Artifacts**: Download test reports from workflow runs
- **Coverage**: View coverage reports in artifacts

### Local CI Simulation

```bash
# Run tests as they run in CI
cd backend
mvn clean test -Dparallel=classes -DthreadCount=4

cd frontend
npm run test:ci
npx playwright test --reporter=html,json
```

---

## Best Practices

### General

1. ✅ **Write tests first** (TDD) when possible
2. ✅ **Keep tests independent** - no shared state
3. ✅ **Use descriptive test names** - `shouldDoSomethingWhenCondition()`
4. ✅ **Follow AAA pattern** - Arrange, Act, Assert
5. ✅ **Mock external dependencies** - APIs, databases (except integration tests)
6. ✅ **Test edge cases** - null values, empty strings, boundary conditions
7. ✅ **Maintain high coverage** - Aim for 80%+

### Backend

1. ✅ **Use TestDataBuilder** for creating test objects
2. ✅ **Use @DisplayName** for readable test reports
3. ✅ **Extend E2ETestBase** for E2E tests
4. ✅ **Mock external services** with WireMock
5. ✅ **Use @Transactional** for integration tests to auto-rollback

### Frontend

1. ✅ **Test user interactions**, not implementation
2. ✅ **Use getByRole** over getByTestId when possible
3. ✅ **Mock API calls** in E2E tests
4. ✅ **Test accessibility** - ARIA labels, keyboard navigation
5. ✅ **Use Page Object Model** for complex E2E scenarios

### Performance

1. ✅ **Run tests in parallel** - Configured by default
2. ✅ **Use test fixtures** - Reuse test data
3. ✅ **Skip slow tests** in development: `@Disabled` or `.skip`
4. ✅ **Optimize database queries** in integration tests

### Debugging

```bash
# Backend: Run test with debugger
mvn test -Dmaven.surefire.debug

# Frontend: Debug Jest test
node --inspect-brk node_modules/.bin/jest --runInBand

# Frontend: Debug Playwright test
npx playwright test --debug
```

---

## Test Execution Times

**Target:** < 15 minutes total

| Test Suite | Target Time | Current Optimization |
|------------|-------------|---------------------|
| Backend Unit | < 2 min | Parallel (4 threads) |
| Backend Integration | < 5 min | Parallel (2 threads) |
| Backend E2E | < 3 min | Testcontainers + WireMock |
| Frontend Unit | < 1 min | Jest parallel |
| Frontend E2E | < 4 min | Playwright (4 workers) |

---

## Additional Resources

- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Spring Boot Testing](https://spring.io/guides/gs/testing-web/)
- [Jest Documentation](https://jestjs.io/docs/getting-started)
- [React Testing Library](https://testing-library.com/docs/react-testing-library/intro/)
- [Playwright Documentation](https://playwright.dev/)
- [Testcontainers](https://www.testcontainers.org/)

---

**Last Updated:** 2025-12-18
**Maintained by:** TjanaBot Development Team
