# Testing Best Practices - TjanaBot AI Chatbot

> **Essential patterns and anti-patterns** for writing high-quality, maintainable tests.

---

## Core Principles

### 1. Tests Should Be FIRST

- **Fast**: Tests should run quickly (< 1s per unit test)
- **Independent**: No dependencies between tests
- **Repeatable**: Same result every time
- **Self-Validating**: Pass or fail, no manual inspection
- **Timely**: Written alongside production code

### 2. Arrange-Act-Assert (AAA) Pattern

```java
@Test
void shouldCalculateTotalPrice() {
    // Arrange: Set up test data
    Cart cart = new Cart();
    cart.add(new Item("Book", 10.00));
    cart.add(new Item("Pen", 2.50));

    // Act: Execute the behavior
    double total = cart.getTotal();

    // Assert: Verify the result
    assertEquals(12.50, total, 0.01);
}
```

### 3. One Concept Per Test

❌ **Bad**: Testing multiple scenarios in one test
```java
@Test
void testUserOperations() {
    // Create user
    User user = service.create("john@example.com");
    assertNotNull(user);

    // Update user
    user.setName("John Doe");
    service.update(user);
    assertEquals("John Doe", user.getName());

    // Delete user
    service.delete(user.getId());
    assertNull(service.findById(user.getId()));
}
```

✅ **Good**: Separate tests for each scenario
```java
@Test
void shouldCreateUser() {
    User user = service.create("john@example.com");
    assertNotNull(user);
    assertEquals("john@example.com", user.getEmail());
}

@Test
void shouldUpdateUserName() {
    User user = TestDataBuilder.createUser();
    user.setName("John Doe");

    service.update(user);

    assertEquals("John Doe", user.getName());
}

@Test
void shouldDeleteUser() {
    User user = TestDataBuilder.createUser();
    Long userId = user.getId();

    service.delete(userId);

    assertNull(service.findById(userId));
}
```

---

## Backend Best Practices

### Unit Tests

#### Use Constructor Injection for Better Testability

❌ **Bad**: Field injection
```java
@Service
public class ChatbotService {
    @Autowired
    private ChatbotRepository repository;  // Hard to test
}
```

✅ **Good**: Constructor injection
```java
@Service
@RequiredArgsConstructor
public class ChatbotService {
    private final ChatbotRepository repository;  // Easy to test
}

// Test
@Test
void test() {
    ChatbotRepository mockRepo = mock(ChatbotRepository.class);
    ChatbotService service = new ChatbotService(mockRepo);
    // ...
}
```

#### Use TestDataBuilder for Complex Objects

✅ **Good**: Centralized test data creation
```java
public class TestDataBuilder {
    public static User createTestUser() {
        return createTestUser("test@example.com");
    }

    public static User createTestUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(email.split("@")[0]);
        user.setPassword("Test1234!");
        return user;
    }

    public static Chatbot createTestChatbot(User owner) {
        Chatbot chatbot = new Chatbot();
        chatbot.setName("Test Bot");
        chatbot.setWebsiteUrl("https://example.com");
        chatbot.setOwner(owner);
        return chatbot;
    }
}
```

#### Verify Behavior, Not Implementation

❌ **Bad**: Testing implementation details
```java
@Test
void testSave() {
    service.save(user);
    verify(repository).save(user);  // Testing how, not what
}
```

✅ **Good**: Testing behavior/outcome
```java
@Test
void shouldSaveUserWithHashedPassword() {
    String plainPassword = "password123";
    user.setPassword(plainPassword);

    User saved = service.save(user);

    assertNotEquals(plainPassword, saved.getPassword());
    assertTrue(passwordEncoder.matches(plainPassword, saved.getPassword()));
}
```

### Integration Tests

#### Use @Transactional for Auto-Rollback

```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional  // Auto-rollback after each test
class UserRepositoryIT {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveUser() {
        User user = new User();
        user.setEmail("test@example.com");

        User saved = userRepository.save(user);

        assertNotNull(saved.getId());
    }
    // Database automatically rolled back
}
```

#### Mock External Services, Not Internal Logic

```java
@SpringBootTest
@AutoConfigureMockMvc
class SubscriptionControllerIT {

    @MockitoBean
    private StripeService stripeService;  // Mock external service

    @Autowired
    private SubscriptionService subscriptionService;  // Real service

    @Test
    void shouldCreateCheckoutSession() throws Exception {
        // Mock Stripe API response
        when(stripeService.createCheckoutSession(any()))
            .thenReturn("session_123");

        // Test real subscription service
        String sessionId = subscriptionService.createCheckout(userId, "price_basic");

        assertNotNull(sessionId);
    }
}
```

### E2E Tests

#### Use WireMock for External API Mocking

```java
@RegisterExtension
static WireMockExtension wireMockServer = WireMockExtension.newInstance()
    .options(wireMockConfig().dynamicPort())
    .build();

@Test
void shouldHandleStripeWebhook() {
    // Mock Stripe API
    stubFor(post(urlEqualTo("/v1/customers"))
        .willReturn(aResponse()
            .withStatus(200)
            .withBody("{\"id\": \"cus_123\"}")));

    // Test the flow
    Response response = apiClient.post("/api/stripe/webhook", payload);

    response.then().statusCode(200);
}
```

---

## Frontend Best Practices

### Unit Tests (Jest + React Testing Library)

#### Test User Behavior, Not Implementation

❌ **Bad**: Testing implementation
```typescript
test('should have correct state', () => {
  const { result } = renderHook(() => useState(0));
  expect(result.current[0]).toBe(0);  // Testing internal state
});
```

✅ **Good**: Testing user-visible behavior
```typescript
test('should display count when button clicked', () => {
  render(<Counter />);
  const button = screen.getByRole('button', { name: /increment/i });

  fireEvent.click(button);

  expect(screen.getByText('Count: 1')).toBeInTheDocument();
});
```

#### Use Semantic Queries

**Priority Order:**
1. `getByRole` - Accessibility-first
2. `getByLabelText` - Forms
3. `getByPlaceholderText` - Forms (last resort)
4. `getByText` - Non-interactive content
5. `getByTestId` - Only when nothing else works

❌ **Bad**: Using testid as first choice
```typescript
const button = screen.getByTestId('submit-button');
```

✅ **Good**: Using role
```typescript
const button = screen.getByRole('button', { name: /submit/i });
```

#### Mock API Calls, Not Implementation

```typescript
// Good: Mock at fetch level
beforeEach(() => {
  global.fetch = jest.fn(() =>
    Promise.resolve({
      ok: true,
      json: () => Promise.resolve({ chatbots: [] }),
    })
  );
});

test('should display chatbots', async () => {
  render(<ChatbotList />);

  await waitFor(() => {
    expect(screen.getByText(/no chatbots/i)).toBeInTheDocument();
  });
});
```

### E2E Tests (Playwright)

#### Use Page Object Model for Complex Flows

```typescript
// pages/LoginPage.ts
export class LoginPage {
  constructor(private page: Page) {}

  async goto() {
    await this.page.goto('/login');
  }

  async loginWithGoogle(email: string) {
    await this.page.getByRole('button', { name: /google/i }).click();
    // Handle OAuth flow
  }

  async isLoggedIn() {
    return this.page.url().includes('/dashboard');
  }
}

// test file
test('should login successfully', async ({ page }) => {
  const loginPage = new LoginPage(page);

  await loginPage.goto();
  await loginPage.loginWithGoogle('test@example.com');

  expect(await loginPage.isLoggedIn()).toBe(true);
});
```

#### Use Explicit Waits, Not Fixed Timeouts

❌ **Bad**: Fixed timeout
```typescript
await page.waitForTimeout(3000);  // Flaky!
```

✅ **Good**: Wait for specific condition
```typescript
await page.waitForLoadState('networkidle');
await page.waitForSelector('[data-loaded="true"]');
await expect(page.getByText('Welcome')).toBeVisible();
```

#### Mock Network Requests

```typescript
test('should handle API error gracefully', async ({ page }) => {
  // Mock failed API response
  await page.route('**/api/chatbots', route => {
    route.fulfill({
      status: 500,
      body: JSON.stringify({ error: 'Internal server error' })
    });
  });

  await page.goto('/dashboard');

  await expect(page.getByText(/error loading chatbots/i)).toBeVisible();
});
```

---

## Common Anti-Patterns

### ❌ Testing Private Methods

```java
// Bad: Making private method public just to test it
public void helperMethod() { ... }

@Test
void testHelperMethod() {
    service.helperMethod();  // Testing implementation detail
}
```

**Solution**: Test private methods through public API

### ❌ Shared Test State

```java
// Bad: Shared state between tests
private static User testUser;

@BeforeAll
static void setup() {
    testUser = new User("test@example.com");
}

@Test
void test1() {
    testUser.setName("John");  // Modifies shared state
}

@Test
void test2() {
    assertEquals("", testUser.getName());  // Fails if test1 runs first!
}
```

**Solution**: Create fresh data for each test

```java
@BeforeEach
void setup() {
    testUser = TestDataBuilder.createUser();
}
```

### ❌ Testing Too Much in One Test

```java
// Bad: Testing entire user journey in one test
@Test
void testCompleteUserJourney() {
    // 50 lines of test code...
}
```

**Solution**: Break into smaller, focused tests

### ❌ Ignoring Test Failures

```java
// Bad: Commenting out failing test
// @Test
// void testSomething() { ... }

// Bad: Disabling test without reason
@Disabled
@Test
void testSomething() { ... }
```

**Solution**: Fix the test or delete it if no longer relevant

### ❌ Not Testing Edge Cases

```java
// Bad: Only testing happy path
@Test
void shouldCalculateDiscount() {
    assertEquals(90.0, service.applyDiscount(100.0, 10));
}

// Missing tests for:
// - Negative discount
// - Discount > 100%
// - Null values
// - Zero price
```

**Solution**: Test edge cases explicitly

```java
@Test
void shouldRejectNegativeDiscount() {
    assertThrows(IllegalArgumentException.class, () -> {
        service.applyDiscount(100.0, -10);
    });
}

@Test
void shouldRejectDiscountOver100Percent() {
    assertThrows(IllegalArgumentException.class, () -> {
        service.applyDiscount(100.0, 150);
    });
}
```

---

## Test Naming Conventions

### Backend (Java)

```java
// Pattern: should[ExpectedBehavior]When[StateUnderTest]
@Test
void shouldCreateChatbotWhenValidData() { ... }

@Test
void shouldThrowExceptionWhenInvalidUrl() { ... }

@Test
void shouldReturnEmptyListWhenNoChatbotsExist() { ... }
```

### Frontend (TypeScript)

```typescript
// Pattern: should [expected behavior]
test('should display error message when API fails', () => { ... });

test('should enable submit button when form is valid', () => { ... });

test('should navigate to dashboard after login', () => { ... });
```

---

## Test Organization

### Group Related Tests

```java
@DisplayName("Chatbot Service")
class ChatbotServiceTest {

    @Nested
    @DisplayName("Create Chatbot")
    class CreateChatbot {

        @Test
        @DisplayName("Should create chatbot with valid data")
        void shouldCreateWithValidData() { ... }

        @Test
        @DisplayName("Should throw exception with invalid URL")
        void shouldThrowWithInvalidUrl() { ... }
    }

    @Nested
    @DisplayName("Update Chatbot")
    class UpdateChatbot {
        // Update tests
    }
}
```

---

## Coverage Guidelines

### What to Test

✅ **Do test:**
- Business logic
- Edge cases and error handling
- Public APIs and interfaces
- Integration points
- User interactions

❌ **Don't test:**
- Getters/setters (unless they have logic)
- Third-party library internals
- Framework code
- Database queries (test behavior, not SQL)

### Coverage Metrics

| Metric | Target | Priority |
|--------|--------|----------|
| Line Coverage | 80%+ | High |
| Branch Coverage | 70%+ | High |
| Function Coverage | 70%+ | Medium |
| Statement Coverage | 80%+ | High |

**Note**: 100% coverage doesn't guarantee quality - focus on meaningful tests.

---

## Debugging Tests

### Backend

```bash
# Run with debugger
mvn test -Dmaven.surefire.debug

# Run specific test with logs
mvn test -Dtest=ChatbotServiceTest -X

# Skip flaky test temporarily
mvn test -Dtest='!FlakyTest'
```

### Frontend

```bash
# Debug Jest test
node --inspect-brk node_modules/.bin/jest --runInBand

# Debug Playwright test
npx playwright test --debug

# Run test in headed mode
npx playwright test --headed

# Interactive UI mode
npx playwright test --ui
```

---

## Performance Optimization

### Parallel Execution

- ✅ Unit tests: Run in parallel (already configured)
- ✅ Integration tests: Limited parallel (2 threads)
- ✅ E2E tests: Parallel with Playwright workers

### Use Test Fixtures

```java
// Expensive setup
@BeforeAll
static void expensiveSetup() {
    // Run once for all tests
}

// Cheap setup
@BeforeEach
void cheapSetup() {
    // Run before each test
}
```

### Reuse Containers (Testcontainers)

```java
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
    .withReuse(true);  // Reuse container across test runs
```

---

## Conclusion

**Remember:**
1. Tests are documentation - make them readable
2. Tests should fail for the right reasons
3. Flaky tests are worse than no tests
4. Fast feedback is crucial - optimize test speed
5. High coverage ≠ high quality - focus on meaningful tests

**Questions or suggestions?** Update this guide as patterns evolve!

---

**Last Updated:** 2025-12-18
