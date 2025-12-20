# TjanaBot AI Chatbot - Professional Testing Strategy

**Document Version:** 1.0
**Last Updated:** December 2, 2025
**Status:** Testing Framework & Best Practices Guide

---

## Executive Summary

This document outlines a comprehensive, professional testing strategy for the TjanaBot AI Chatbot application, incorporating industry best practices from Spring Boot testing in 2025, Stripe payment testing, and Google OAuth 2.0 authentication testing. The strategy follows the Testing Pyramid principle and modern testing methodologies.

---

## Table of Contents

1. [Testing Philosophy](#testing-philosophy)
2. [Testing Pyramid](#testing-pyramid)
3. [Test Categories](#test-categories)
4. [Technology Stack for Testing](#technology-stack-for-testing)
5. [Unit Testing Strategy](#unit-testing-strategy)
6. [Integration Testing Strategy](#integration-testing-strategy)
7. [End-to-End Testing Strategy](#end-to-end-testing-strategy)
8. [Security Testing](#security-testing)
9. [Payment Testing (Stripe)](#payment-testing-stripe)
10. [OAuth 2.0 Testing (Google)](#oauth-20-testing-google)
11. [Performance Testing](#performance-testing)
12. [Test Data Management](#test-data-management)
13. [CI/CD Integration](#cicd-integration)
14. [Test Coverage Goals](#test-coverage-goals)
15. [Best Practices](#best-practices)
16. [Testing Checklist](#testing-checklist)

---

## 1. Testing Philosophy

### Core Principles

1. **Test Pyramid First**: More unit tests, fewer integration tests, minimal E2E tests
2. **Fast Feedback**: Tests should run quickly to encourage frequent execution
3. **Isolation**: Each test should be independent and not affect others
4. **Repeatability**: Tests must produce consistent results
5. **Maintainability**: Tests should be easy to understand and modify
6. **Realistic Testing**: Use real databases with Testcontainers, not in-memory substitutes
7. **Security First**: Security tests are not optional

### Testing Goals

- ✅ Prevent regressions
- ✅ Document behavior
- ✅ Enable refactoring confidence
- ✅ Catch bugs early
- ✅ Ensure security compliance
- ✅ Validate business logic
- ✅ Verify integrations

---

## 2. Testing Pyramid

```
                    ▲
                   ╱ ╲
                  ╱   ╲
                 ╱ E2E ╲          ~5 tests
                ╱───────╲         Manual + Automated
               ╱         ╲
              ╱Integration╲       ~50 tests
             ╱─────────────╲      API + Database
            ╱               ╲
           ╱  Unit Tests     ╲    ~500 tests
          ╱───────────────────╲   Fast, Isolated
         ╱                     ╲
        ╱_______________________╲
```

### Distribution
- **70% Unit Tests**: Fast, isolated, no external dependencies
- **25% Integration Tests**: Test component interactions
- **5% End-to-End Tests**: Full system validation

---

## 3. Test Categories

### 3.1 Unit Tests
- **Scope**: Individual classes/methods
- **Speed**: < 1 second per test
- **Dependencies**: Mocked
- **Examples**: Service logic, utility functions, validators

### 3.2 Integration Tests
- **Scope**: Multiple components working together
- **Speed**: < 5 seconds per test
- **Dependencies**: Real databases, mock external APIs
- **Examples**: Repository tests, REST API tests, service integration

### 3.3 Contract Tests
- **Scope**: API contracts
- **Speed**: < 3 seconds per test
- **Dependencies**: Minimal
- **Examples**: Request/response validation, API versioning

### 3.4 End-to-End Tests
- **Scope**: Complete user workflows
- **Speed**: < 30 seconds per test
- **Dependencies**: Full system
- **Examples**: User registration → payment → chatbot creation

### 3.5 Security Tests
- **Scope**: Security vulnerabilities
- **Speed**: Varies
- **Dependencies**: Security scanners
- **Examples**: SQL injection, XSS, CSRF, authentication bypass

### 3.6 Performance Tests
- **Scope**: System performance
- **Speed**: Minutes
- **Dependencies**: Load testing tools
- **Examples**: Load tests, stress tests, spike tests

---

## 4. Technology Stack for Testing

### Core Testing Framework
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

Includes:
- JUnit 5 (Jupiter)
- Mockito
- AssertJ
- Hamcrest
- JSONassert
- Spring Test

### Additional Testing Libraries

**Database Testing:**
```xml
<!-- Testcontainers for realistic database testing -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
```

**HTTP Mocking:**
```xml
<!-- WireMock for mocking external HTTP APIs -->
<dependency>
    <groupId>com.github.tomakehurst</groupId>
    <artifactId>wiremock-jre8</artifactId>
    <version>2.35.0</version>
    <scope>test</scope>
</dependency>
```

**Security Testing:**
```xml
<!-- Spring Security Test -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

**REST API Testing:**
```xml
<!-- REST Assured for API testing -->
<dependency>
    <groupId>io.rest-assured</groupId>
    <artifactId>rest-assured</artifactId>
    <version>5.3.2</version>
    <scope>test</scope>
</dependency>
```

**Test Data Generation:**
```xml
<!-- Faker for generating test data -->
<dependency>
    <groupId>com.github.javafaker</groupId>
    <artifactId>javafaker</artifactId>
    <version>1.0.2</version>
    <scope>test</scope>
</dependency>
```

---

## 5. Unit Testing Strategy

### 5.1 Principles

**✅ DO:**
- Use `@ExtendWith(MockitoExtension.class)` instead of `@SpringBootTest`
- Mock all dependencies
- Test single responsibility
- Use descriptive test names
- Follow AAA pattern (Arrange, Act, Assert)
- Test edge cases and error conditions

**❌ DON'T:**
- Load Spring context for unit tests
- Access external systems
- Share state between tests
- Use Thread.sleep()
- Test private methods directly

### 5.2 Test Structure

```java
@ExtendWith(MockitoExtension.class)
class ServiceNameTest {

    @Mock
    private DependencyRepository repository;

    @InjectMocks
    private ServiceName serviceUnderTest;

    @Test
    @DisplayName("Should return user when valid ID is provided")
    void shouldReturnUser_whenValidIdProvided() {
        // Arrange (Given)
        Long userId = 1L;
        User expectedUser = new User(userId, "test@example.com");
        when(repository.findById(userId)).thenReturn(Optional.of(expectedUser));

        // Act (When)
        User actualUser = serviceUnderTest.getUserById(userId);

        // Assert (Then)
        assertThat(actualUser).isNotNull();
        assertThat(actualUser.getId()).isEqualTo(userId);
        assertThat(actualUser.getEmail()).isEqualTo("test@example.com");
        verify(repository, times(1)).findById(userId);
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    void shouldThrowException_whenUserNotFound() {
        // Arrange
        Long userId = 999L;
        when(repository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> serviceUnderTest.getUserById(userId))
            .isInstanceOf(UserNotFoundException.class)
            .hasMessage("User not found with ID: 999");
    }
}
```

### 5.3 Testing Layers

**Services (Business Logic):**
```java
@ExtendWith(MockitoExtension.class)
class StripeServiceTest {

    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private StripeService stripeService;

    @Test
    void shouldCalculateGracePeriodEnd() {
        // Test grace period calculation logic
    }

    @Test
    void shouldIncrementRetryCount() {
        // Test payment retry logic
    }
}
```

**Validators:**
```java
class UrlValidationServiceTest {

    private UrlValidationService validator = new UrlValidationService();

    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost",
        "http://127.0.0.1",
        "http://10.0.0.1",
        "http://192.168.1.1"
    })
    void shouldRejectPrivateIps(String url) {
        assertThat(validator.isValid(url)).isFalse();
    }
}
```

**Utilities:**
```java
class LogSanitizerTest {

    @Test
    void shouldMaskApiKeys() {
        String input = "api_key=sk_test_1234567890";
        String result = LogSanitizer.sanitize(input);
        assertThat(result).doesNotContain("1234567890");
    }
}
```

---

## 6. Integration Testing Strategy

### 6.1 Principles

**✅ DO:**
- Use Test Slices (`@WebMvcTest`, `@DataJpaTest`)
- Use Testcontainers for real databases
- Mock external APIs with WireMock
- Use `@Transactional` to rollback changes
- Test actual HTTP requests and responses
- Verify database state changes

**❌ DON'T:**
- Use H2 for integration tests (behavior differs from PostgreSQL)
- Hit real external APIs
- Share database state between tests
- Use `@SpringBootTest` when a test slice suffices

### 6.2 Repository Testing

```java
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldFindUserByEmail() {
        // Arrange
        User user = new User("test@example.com", "testuser");
        userRepository.save(user);

        // Act
        Optional<User> found = userRepository.findByEmail("test@example.com");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    void shouldEnforceUniqueEmailConstraint() {
        // Arrange
        User user1 = new User("duplicate@example.com", "user1");
        User user2 = new User("duplicate@example.com", "user2");
        userRepository.save(user1);

        // Act & Assert
        assertThatThrownBy(() -> userRepository.save(user2))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
```

### 6.3 REST API Testing

```java
@WebMvcTest(ChatbotController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class})
class ChatbotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatbotService chatbotService;

    @MockBean
    private SubscriptionService subscriptionService;

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldReturnChatbots_whenAuthenticated() throws Exception {
        // Arrange
        List<Chatbot> chatbots = Arrays.asList(
            new Chatbot(1L, "Bot 1"),
            new Chatbot(2L, "Bot 2")
        );
        when(chatbotService.getUserChatbots(any())).thenReturn(chatbots);

        // Act & Assert
        mockMvc.perform(get("/api/chatbots")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].name").value("Bot 1"))
            .andExpect(jsonPath("$[1].name").value("Bot 2"));
    }

    @Test
    void shouldReturn401_whenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/chatbots"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void shouldValidateInput_whenCreatingChatbot() throws Exception {
        // Arrange
        String invalidJson = "{\"name\": \"\"}"; // Empty name

        // Act & Assert
        mockMvc.perform(post("/api/chatbots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest());
    }
}
```

### 6.4 Service Integration Testing

```java
@SpringBootTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class SubscriptionServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Autowired
    private SubscriptionService subscriptionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Test
    void shouldHandlePaymentFailure_withGracePeriod() {
        // Arrange
        User user = createTestUser();
        Subscription subscription = createActiveSubscription(user);

        // Act
        subscriptionService.handlePaymentFailure(subscription.getId());

        // Assert
        Subscription updated = subscriptionRepository.findById(subscription.getId()).get();
        assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(updated.getPaymentRetryCount()).isEqualTo(1);
        assertThat(updated.getGracePeriodEnd()).isNotNull();
        assertThat(updated.getGracePeriodEnd()).isAfter(LocalDateTime.now());
    }
}
```

### 6.5 External API Mocking

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class StripeWebhookIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldProcessStripeWebhook() {
        // Arrange
        String webhookPayload = createStripeWebhookPayload();
        String signature = generateStripeSignature(webhookPayload);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Stripe-Signature", signature);

        // Act
        ResponseEntity<String> response = restTemplate.exchange(
            "/stripe/webhook",
            HttpMethod.POST,
            new HttpEntity<>(webhookPayload, headers),
            String.class
        );

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
```

---

## 7. End-to-End Testing Strategy

### 7.1 Principles

- Test critical user journeys
- Use real browsers (Selenium/Playwright)
- Run against staging environment
- Keep E2E tests minimal (expensive)
- Focus on happy paths and critical failures

### 7.2 Critical Test Scenarios

**User Journey 1: Registration to First Chatbot**
```
1. User visits landing page
2. Click "Sign in with Google"
3. Complete OAuth flow
4. Redirected to dashboard
5. Click "Create Subscription"
6. Complete Stripe checkout (test mode)
7. Create first chatbot
8. Send test message
9. Verify response
```

**User Journey 2: Subscription Management**
```
1. User logs in
2. Navigate to subscription page
3. Upgrade plan
4. Verify prorated charge
5. Downgrade plan
6. Verify end-of-period change
7. Cancel subscription
8. Verify access revocation
```

### 7.3 E2E Test Framework (Future)

```java
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
@ExtendWith(SeleniumExtension.class)
class UserJourneyE2ETest {

    private WebDriver driver;

    @Test
    void completeUserJourney_fromRegistrationToChatbot() {
        // Step 1: Navigate to landing page
        driver.get("http://localhost:3000");

        // Step 2: Click Google Sign In
        driver.findElement(By.id("google-signin-btn")).click();

        // Step 3: Handle OAuth (using test credentials)
        handleGoogleOAuth(driver);

        // Step 4: Verify dashboard load
        WebElement dashboard = new WebDriverWait(driver, Duration.ofSeconds(10))
            .until(ExpectedConditions.presenceOfElementLocated(By.id("dashboard")));
        assertThat(dashboard.isDisplayed()).isTrue();

        // Step 5-9: Continue user journey...
    }
}
```

---

## 8. Security Testing

### 8.1 Authentication Testing

```java
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/chatbots"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectExpiredJwtToken() throws Exception {
        String expiredToken = generateExpiredToken();

        mockMvc.perform(get("/api/chatbots")
                .header("Authorization", "Bearer " + expiredToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectMalformedJwtToken() throws Exception {
        mockMvc.perform(get("/api/chatbots")
                .header("Authorization", "Bearer invalid.token.here"))
            .andExpect(status().isUnauthorized());
    }
}
```

### 8.2 Authorization Testing

```java
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationSecurityTest {

    @Test
    @WithMockUser(username = "user1@example.com")
    void shouldNotAccessOtherUsersChatbots() throws Exception {
        // Arrange: Create chatbot for user2
        Long user2ChatbotId = createChatbotForUser("user2@example.com");

        // Act & Assert: user1 tries to access user2's chatbot
        mockMvc.perform(get("/api/chatbots/" + user2ChatbotId))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "inactive-sub@example.com")
    void shouldRejectInactiveSubscriptionAccess() throws Exception {
        mockMvc.perform(post("/api/chatbots")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Test Bot\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("Active subscription required"));
    }
}
```

### 8.3 Input Validation Testing

```java
@WebMvcTest(ChatbotController.class)
class InputValidationSecurityTest {

    @Test
    void shouldRejectSqlInjectionAttempt() throws Exception {
        String maliciousInput = "'; DROP TABLE users; --";

        mockMvc.perform(post("/api/chatbots")
                .content("{\"name\":\"" + maliciousInput + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectXssAttempt() throws Exception {
        String xssPayload = "<script>alert('XSS')</script>";

        mockMvc.perform(post("/api/chatbots")
                .content("{\"description\":\"" + xssPayload + "\"}"))
            .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost/admin",
        "http://127.0.0.1/internal",
        "http://169.254.169.254/latest/meta-data/",
        "file:///etc/passwd"
    })
    void shouldRejectSsrfAttempts(String maliciousUrl) throws Exception {
        mockMvc.perform(post("/api/chatbots")
                .content("{\"websiteUrl\":\"" + maliciousUrl + "\"}"))
            .andExpect(status().isBadRequest());
    }
}
```

### 8.4 Rate Limiting Testing

```java
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitingSecurityTest {

    @Test
    @WithMockUser
    void shouldEnforceRateLimits() throws Exception {
        // Arrange: Make requests up to rate limit
        for (int i = 0; i < 100; i++) {
            mockMvc.perform(get("/api/chatbots"))
                .andExpect(status().isOk());
        }

        // Act & Assert: 101st request should be rate limited
        mockMvc.perform(get("/api/chatbots"))
            .andExpect(status().isTooManyRequests());
    }
}
```

---

## 9. Payment Testing (Stripe)

### 9.1 Test Card Numbers

**Success Scenarios:**
- `4242 4242 4242 4242` - Visa (success)
- `5555 5555 5555 4444` - Mastercard (success)
- `3782 822463 10005` - American Express (success)

**Failure Scenarios:**
- `4000 0000 0000 0002` - Card declined
- `4000 0000 0000 9995` - Insufficient funds
- `4000 0000 0000 0069` - Expired card
- `4000 0000 0000 0127` - Incorrect CVC

**Special Cases:**
- `4000 0000 0000 0341` - Attaching fails
- `4000 0000 0000 9235` - 3D Secure authentication required

### 9.2 Stripe Testing Strategy

```java
@SpringBootTest
@TestPropertySource(properties = {
    "stripe.api-key=${STRIPE_TEST_KEY}",
    "stripe.webhook-secret=${STRIPE_TEST_WEBHOOK_SECRET}"
})
class StripeIntegrationTest {

    @Autowired
    private StripeService stripeService;

    @Test
    void shouldCreateCheckoutSession() throws StripeException {
        // Arrange
        User testUser = createTestUser();

        // Act
        String checkoutUrl = stripeService.createCheckoutSession(testUser);

        // Assert
        assertThat(checkoutUrl).startsWith("https://checkout.stripe.com/");
        assertThat(checkoutUrl).contains("session");
    }

    @Test
    void shouldHandleSuccessfulPayment() {
        // Arrange
        String subscriptionId = "sub_test_123";
        com.stripe.model.Subscription mockSubscription = createMockSubscription();

        // Act
        stripeService.handlePaymentSuccess(subscriptionId);

        // Assert
        Subscription subscription = subscriptionRepository
            .findByStripeSubscriptionId(subscriptionId).get();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getPaymentRetryCount()).isEqualTo(0);
        assertThat(subscription.getGracePeriodEnd()).isNull();
    }

    @Test
    void shouldHandlePaymentFailure_withGracePeriod() {
        // Arrange
        String subscriptionId = "sub_test_123";
        String invoiceId = "in_test_123";

        // Act
        stripeService.handlePaymentFailure(subscriptionId, invoiceId);

        // Assert
        Subscription subscription = subscriptionRepository
            .findByStripeSubscriptionId(subscriptionId).get();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(subscription.getPaymentRetryCount()).isEqualTo(1);
        assertThat(subscription.getGracePeriodEnd()).isNotNull();
        assertThat(subscription.getGracePeriodEnd())
            .isAfter(LocalDateTime.now())
            .isBefore(LocalDateTime.now().plusDays(8));
    }

    @Test
    void shouldRevokeAccess_afterMaxRetries() {
        // Arrange: Create subscription with 2 failed attempts
        Subscription subscription = createSubscriptionWithFailures(2);

        // Act: Trigger 3rd failure
        stripeService.handlePaymentFailure(
            subscription.getStripeSubscriptionId(),
            "in_test_123"
        );

        // Assert
        Subscription updated = subscriptionRepository
            .findById(subscription.getId()).get();
        assertThat(updated.getStatus()).isEqualTo(SubscriptionStatus.UNPAID);
        assertThat(updated.getPaymentRetryCount()).isEqualTo(3);
    }
}
```

### 9.3 Webhook Testing

```java
@SpringBootTest
@AutoConfigureMockMvc
class StripeWebhookTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    @Test
    void shouldProcessSubscriptionCreated() throws Exception {
        // Arrange
        String payload = createWebhookPayload("customer.subscription.created");
        String signature = generateWebhookSignature(payload, webhookSecret);

        // Act & Assert
        mockMvc.perform(post("/stripe/webhook")
                .header("Stripe-Signature", signature)
                .content(payload)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        // Verify subscription was created in database
        Subscription subscription = subscriptionRepository
            .findByStripeSubscriptionId("sub_test_123").get();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
    }

    @Test
    void shouldRejectInvalidWebhookSignature() throws Exception {
        String payload = createWebhookPayload("customer.subscription.created");
        String invalidSignature = "invalid_signature";

        mockMvc.perform(post("/stripe/webhook")
                .header("Stripe-Signature", invalidSignature)
                .content(payload))
            .andExpect(status().isBadRequest());
    }
}
```

### 9.4 Subscription Lifecycle Testing

```java
@SpringBootTest
@Transactional
class SubscriptionLifecycleTest {

    @Test
    void shouldHandleCompleteSubscriptionLifecycle() {
        // 1. Create subscription
        Subscription subscription = createTestSubscription();
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);

        // 2. Upgrade plan
        stripeService.upgradeSubscription(
            subscription.getUserId(),
            "price_pro",
            SubscriptionPlan.PRO
        );
        Subscription upgraded = getSubscription(subscription.getId());
        assertThat(upgraded.getPlan()).isEqualTo(SubscriptionPlan.PRO);

        // 3. Payment failure
        stripeService.handlePaymentFailure(
            subscription.getStripeSubscriptionId(),
            "in_123"
        );
        Subscription pastDue = getSubscription(subscription.getId());
        assertThat(pastDue.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);

        // 4. Payment success (recovery)
        stripeService.handlePaymentSuccess(subscription.getStripeSubscriptionId());
        Subscription recovered = getSubscription(subscription.getId());
        assertThat(recovered.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(recovered.getPaymentRetryCount()).isEqualTo(0);

        // 5. Cancel subscription
        stripeService.cancelSubscription(subscription.getUserId());
        Subscription canceled = getSubscription(subscription.getId());
        assertThat(canceled.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(canceled.getCanceledAt()).isNotNull();
    }
}
```

---

## 10. OAuth 2.0 Testing (Google)

### 10.1 Mock OAuth Testing

```java
@SpringBootTest
@AutoConfigureMockMvc
class OAuth2AuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = "test@example.com")
    void shouldAuthenticateWithOAuth2() throws Exception {
        mockMvc.perform(get("/api/user/profile"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void shouldHandleOAuth2Login() throws Exception {
        // Simulate OAuth2 login with mock
        mockMvc.perform(get("/login/oauth2/code/google")
                .param("code", "mock_authorization_code")
                .param("state", "mock_state"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/dashboard"));
    }
}
```

### 10.2 Custom OAuth2 User Service Testing

```java
@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomOAuth2UserService oauth2UserService;

    @Test
    void shouldCreateNewUser_whenFirstTimeLogin() {
        // Arrange
        OAuth2UserRequest userRequest = createMockUserRequest();
        Map<String, Object> attributes = Map.of(
            "sub", "google_123",
            "email", "newuser@example.com",
            "name", "New User"
        );

        when(userRepository.findByEmail("newuser@example.com"))
            .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
            .thenAnswer(i -> i.getArgument(0));

        // Act
        OAuth2User result = oauth2UserService.loadUser(userRequest);

        // Assert
        assertThat(result.getAttribute("email")).isEqualTo("newuser@example.com");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void shouldLinkGoogleAccount_whenUserExists() {
        // Arrange
        User existingUser = new User("existing@example.com", "existinguser");
        when(userRepository.findByEmail("existing@example.com"))
            .thenReturn(Optional.of(existingUser));

        // Act
        OAuth2User result = oauth2UserService.loadUser(createMockUserRequest());

        // Assert
        assertThat(existingUser.getGoogleId()).isNotNull();
        assertThat(existingUser.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        verify(userRepository, times(1)).save(existingUser);
    }
}
```

### 10.3 OAuth2 Configuration Testing

```java
@SpringBootTest
@TestPropertySource(properties = {
    "spring.security.oauth2.client.registration.google.client-id=test-client-id",
    "spring.security.oauth2.client.registration.google.client-secret=test-secret"
})
class OAuth2ConfigurationTest {

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void shouldLoadGoogleClientRegistration() {
        ClientRegistration googleRegistration =
            clientRegistrationRepository.findByRegistrationId("google");

        assertThat(googleRegistration).isNotNull();
        assertThat(googleRegistration.getClientId()).isEqualTo("test-client-id");
        assertThat(googleRegistration.getScopes()).contains("email", "profile");
    }
}
```

### 10.4 Security Context Testing

```java
@SpringBootTest
@AutoConfigureMockMvc
class SecurityContextTest {

    @Test
    @WithOAuth2Login(
        attributes = {
            @WithOAuth2Login.Attribute(key = "email", value = "test@example.com"),
            @WithOAuth2Login.Attribute(key = "sub", value = "google_123")
        }
    )
    void shouldAccessProtectedResource_withOAuth2Login() throws Exception {
        mockMvc.perform(get("/api/chatbots"))
            .andExpect(status().isOk());
    }
}
```

---

## 11. Performance Testing

### 11.1 Load Testing Strategy

**Tools:**
- JMeter
- Gatling
- K6
- Locust

**Test Scenarios:**

1. **Normal Load Test**
   - Users: 100 concurrent
   - Duration: 10 minutes
   - Expected: < 200ms response time

2. **Stress Test**
   - Users: Gradually increase to 1000
   - Find breaking point
   - Verify graceful degradation

3. **Spike Test**
   - Sudden spike to 500 users
   - Verify system recovery
   - Check resource cleanup

4. **Endurance Test**
   - Users: 200 concurrent
   - Duration: 2 hours
   - Check for memory leaks

### 11.2 Performance Benchmarks

```java
@SpringBootTest
@AutoConfigureMockMvc
class PerformanceTest {

    @Test
    void chatbotResponseTime_shouldBeLessThan500ms() {
        long startTime = System.currentTimeMillis();

        mockMvc.perform(post("/api/chat/embed_123")
                .content("{\"message\":\"Hello\"}"))
            .andExpect(status().isOk());

        long duration = System.currentTimeMillis() - startTime;
        assertThat(duration).isLessThan(500);
    }
}
```

---

## 12. Test Data Management

### 12.1 Test Data Builders

```java
public class TestDataBuilder {

    public static User createTestUser() {
        return User.builder()
            .email("test" + UUID.randomUUID() + "@example.com")
            .username("testuser")
            .password(BCrypt.hashpw("password", BCrypt.gensalt()))
            .authProvider(AuthProvider.LOCAL)
            .build();
    }

    public static Chatbot createTestChatbot(User owner) {
        return Chatbot.builder()
            .name("Test Bot " + UUID.randomUUID())
            .description("Test chatbot")
            .owner(owner)
            .language("en")
            .isActive(true)
            .build();
    }

    public static Subscription createActiveSubscription(User user) {
        return Subscription.builder()
            .user(user)
            .stripeCustomerId("cus_test_" + UUID.randomUUID())
            .stripeSubscriptionId("sub_test_" + UUID.randomUUID())
            .status(SubscriptionStatus.ACTIVE)
            .plan(SubscriptionPlan.BASIC)
            .currentPeriodEnd(LocalDateTime.now().plusMonths(1))
            .build();
    }
}
```

### 12.2 Test Data Cleanup

```java
@TestConfiguration
public class TestDataCleanup {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ChatbotRepository chatbotRepository;

    @AfterEach
    public void cleanup() {
        chatbotRepository.deleteAll();
        userRepository.deleteAll();
    }
}
```

---

## 13. CI/CD Integration

### 13.1 GitHub Actions Configuration

```yaml
name: Test Suite

on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Run unit tests
        run: mvn test -Dtest=**/*Test.java

  integration-tests:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_PASSWORD: test
        options: >-
          --health-cmd pg_isready
          --health-interval 10s
          --health-timeout 5s
          --health-retries 5
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK 17
        uses: actions/setup-java@v3
      - name: Run integration tests
        run: mvn verify -Dtest=**/*IT.java

  security-scan:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run OWASP Dependency Check
        run: mvn dependency-check:check
```

### 13.2 Maven Configuration

```xml
<build>
    <plugins>
        <!-- Separate unit and integration tests -->
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <includes>
                    <include>**/*Test.java</include>
                </includes>
                <excludes>
                    <exclude>**/*IT.java</exclude>
                </excludes>
            </configuration>
        </plugin>

        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-failsafe-plugin</artifactId>
            <configuration>
                <includes>
                    <include>**/*IT.java</include>
                </includes>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>integration-test</goal>
                        <goal>verify</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>

        <!-- Code coverage -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.11</version>
            <executions>
                <execution>
                    <goals>
                        <goal>prepare-agent</goal>
                    </goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

---

## 14. Test Coverage Goals

### 14.1 Coverage Targets

| Component | Target Coverage | Priority |
|-----------|----------------|----------|
| Services | 90%+ | Critical |
| Controllers | 85%+ | High |
| Repositories | 80%+ | Medium |
| Security | 95%+ | Critical |
| Utils | 90%+ | High |
| Overall | 85%+ | High |

### 14.2 Critical Paths (100% Coverage Required)

- Authentication & Authorization logic
- Payment processing
- Subscription management
- Grace period & retry logic
- Fraud detection algorithms
- Security filters
- Input validation
- Audit logging

---

## 15. Best Practices

### 15.1 General Best Practices

✅ **DO:**
- Write tests before or alongside code (TDD)
- Use descriptive test names (should/when/then)
- Follow AAA pattern (Arrange, Act, Assert)
- Test one thing per test
- Use Testcontainers for realistic database testing
- Mock external dependencies
- Clean up test data after tests
- Run tests in CI/CD pipeline
- Maintain high code coverage (85%+)
- Review test failures immediately
- Keep tests fast (< 5 minutes for full suite)
- Use test slices for integration tests
- Parametrize similar tests
- Test edge cases and error conditions
- Document complex test setups

❌ **DON'T:**
- Use H2 for integration tests
- Hit real external APIs
- Share state between tests
- Use Thread.sleep() - use proper waits
- Ignore flaky tests - fix them
- Skip tests to make builds pass
- Test implementation details
- Write brittle tests tied to exact implementations
- Leave commented-out tests
- Commit failing tests

### 15.2 Naming Conventions

**Test Classes:**
- Unit tests: `ServiceNameTest`
- Integration tests: `ServiceNameIT` or `ServiceNameIntegrationTest`
- E2E tests: `UserJourneyE2ETest`

**Test Methods:**
```java
// Pattern: should[ExpectedResult]_when[Condition]
shouldReturnUser_whenValidIdProvided()
shouldThrowException_whenUserNotFound()
shouldRejectAccess_whenSubscriptionInactive()
```

### 15.3 Test Organization

```
src/test/java/
├── unit/
│   ├── service/
│   ├── util/
│   └── validator/
├── integration/
│   ├── repository/
│   ├── controller/
│   └── api/
├── security/
│   ├── authentication/
│   ├── authorization/
│   └── input/
├── payment/
│   └── stripe/
├── e2e/
│   └── journey/
└── helpers/
    ├── builders/
    └── utils/
```

---

## 16. Testing Checklist

### Pre-Implementation Checklist
- [ ] Review requirements and acceptance criteria
- [ ] Identify testable scenarios
- [ ] Plan test data requirements
- [ ] Set up test environment
- [ ] Configure Testcontainers
- [ ] Set up WireMock for external APIs

### During Implementation
- [ ] Write unit tests for new services
- [ ] Write integration tests for APIs
- [ ] Test happy path scenarios
- [ ] Test error scenarios
- [ ] Test edge cases
- [ ] Verify input validation
- [ ] Test security controls
- [ ] Check code coverage (85%+ goal)

### Pre-Deployment Checklist
- [ ] All tests passing locally
- [ ] All tests passing in CI/CD
- [ ] Code coverage meets targets (85%+)
- [ ] Security tests passing
- [ ] Integration tests with real database
- [ ] Stripe tests in test mode
- [ ] OAuth tests with mock
- [ ] Performance benchmarks met
- [ ] No flaky tests
- [ ] Test documentation updated

### Stripe-Specific Testing
- [ ] Test successful payment flow
- [ ] Test payment failure with grace period
- [ ] Test payment retry logic
- [ ] Test subscription upgrade
- [ ] Test subscription downgrade
- [ ] Test subscription cancellation
- [ ] Test webhook signature verification
- [ ] Test webhook event processing
- [ ] Test all webhook event types
- [ ] Verify database state after each event

### OAuth-Specific Testing
- [ ] Test successful OAuth login
- [ ] Test OAuth error handling
- [ ] Test new user creation
- [ ] Test existing user linking
- [ ] Test OAuth token validation
- [ ] Test OAuth token expiration
- [ ] Test multiple OAuth providers (if applicable)
- [ ] Test OAuth state parameter
- [ ] Test redirect URI validation

### Security Testing
- [ ] Test authentication required
- [ ] Test authorization checks
- [ ] Test input validation
- [ ] Test SQL injection prevention
- [ ] Test XSS prevention
- [ ] Test SSRF prevention
- [ ] Test CSRF protection
- [ ] Test rate limiting
- [ ] Test audit logging
- [ ] Test fraud detection

---

## Research Sources

This testing strategy is based on the following authoritative sources:

### Spring Boot Testing
- [Testing in Spring Boot | Baeldung](https://www.baeldung.com/spring-boot-testing)
- [Spring Boot Testing: Best Practices Guide - DEV Community](https://dev.to/ankitdevcode/spring-boot-testing-a-comprehensive-best-practices-guide-1do6)
- [Spring Boot Integration Testing Best Practices 2025](https://toxigon.com/spring-boot-integration-testing-best-practices)
- [Optimizing Spring Integration Tests | Baeldung](https://www.baeldung.com/spring-tests)
- [Oliver Drotbohm - Rethinking Spring Application Integration Testing](http://odrotbohm.github.io/2025/12/rethinking-spring-application-integration-testing/)

### Stripe Payment Testing
- [Test card numbers | Stripe Documentation](https://docs.stripe.com/testing)
- [A Complete Guide to Stripe Test Cards](https://www.frugaltesting.com/blog/a-complete-guide-to-stripe-test-cards-for-payment-gateway-testing)
- [Integration security guide | Stripe Documentation](https://docs.stripe.com/security/guide)
- [Stripe Payment Gateway Integration Best Practices 2025](https://www.dhiwise.com/post/stripe-payment-gateway-integration)

### OAuth 2.0 Testing
- [Testing OAuth 2.0 :: Spring Security](https://docs.spring.io/spring-security/reference/reactive/test/web/oauth2.html)
- [Spring Security – OAuth2 Login | Baeldung](https://www.baeldung.com/spring-security-5-oauth2-login)

---

*This testing strategy document should be reviewed and updated regularly as testing practices evolve and new requirements emerge.*
