package com.prayer_chat.chatbot.helpers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.prayer_chat.chatbot.model.Subscription;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.SubscriptionRepository;
import com.prayer_chat.chatbot.repository.UserRepository;
import com.prayer_chat.chatbot.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.EntityManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.restassured.response.Response;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Base class for E2E tests.
 * Provides common setup for:
 * - Testcontainers (PostgreSQL database)
 * - WireMock (mocking external APIs)
 * - Spring Boot test server
 * - REST Assured configuration
 * - Test data builders
 *
 * E2E tests extend this class to test complete user journeys
 * across multiple components of the system.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS) // Clean up context after test class
public abstract class E2ETestBase {

    @LocalServerPort
    protected int port;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected SubscriptionRepository subscriptionRepository;

    @Autowired
    protected JwtTokenProvider jwtTokenProvider;
    
    @Autowired
    protected EntityManager entityManager;
    
    @Autowired
    protected TransactionTemplate transactionTemplate;


    /**
     * PostgreSQL container for realistic database testing
     */
    @Container
    protected static PostgreSQLContainer<?> postgresContainer =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("chatbot_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(false) // Don't reuse to avoid connection conflicts between test classes
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forListeningPort())
            .withStartupTimeout(java.time.Duration.ofSeconds(120));

    /**
     * WireMock server for mocking external APIs
     * (Google OAuth, Stripe, Anthropic, Cohere, etc.)
     */
    protected static WireMockServer wireMockServer;

    /**
     * API test client for making HTTP requests
     */
    protected ApiTestClient apiClient;

    /**
     * Start PostgreSQL container and WireMock server before all tests
     */
    @BeforeAll
    static void beforeAll() {
        // PostgreSQL container starts automatically via Testcontainers

        // Start WireMock server
        wireMockServer = new WireMockServer(
            WireMockConfiguration.options()
                .dynamicPort()
        );
        wireMockServer.start();

        // Configure WireMock client
        configureFor("localhost", wireMockServer.port());
    }

    /**
     * Stop WireMock server after all tests
     */
    @AfterAll
    static void afterAll() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    /**
     * Configure Spring properties dynamically for Testcontainers and WireMock
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL configuration - override H2 settings from application-test.yml
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        
        // HikariCP connection pool settings for Testcontainers
        // Increased pool size for concurrent E2E tests
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
        registry.add("spring.datasource.hikari.minimum-idle", () -> "5");
        registry.add("spring.datasource.hikari.connection-timeout", () -> "30000");
        registry.add("spring.datasource.hikari.idle-timeout", () -> "300000");
        registry.add("spring.datasource.hikari.max-lifetime", () -> "300000"); // 5 minutes
        registry.add("spring.datasource.hikari.leak-detection-threshold", () -> "60000");
        // Connection validation - ensure connections are valid before use
        registry.add("spring.datasource.hikari.connection-test-query", () -> "SELECT 1");
        registry.add("spring.datasource.hikari.validation-timeout", () -> "5000");
        // Initialize minimum idle connections on startup
        registry.add("spring.datasource.hikari.initialization-fail-timeout", () -> "60000");
        registry.add("spring.datasource.hikari.auto-commit", () -> "true");
        
        // Override JPA settings for PostgreSQL
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        
        // Configure Hibernate for PostgreSQL compatibility
        // Fix CLOB issue: PostgreSQL doesn't support createClob(), use TEXT instead
        registry.add("spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation", () -> "true");
        registry.add("spring.jpa.properties.hibernate.format_sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.use_sql_comments", () -> "false");
        registry.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "false");

        // WireMock configuration (for external API mocking)
        registry.add("external.api.base-url", () -> "http://localhost:" + wireMockServer.port());

        // Stripe configuration (pointing to WireMock)
        registry.add("stripe.api.base-url", () -> "http://localhost:" + wireMockServer.port());

        // Google OAuth configuration (pointing to WireMock)
        registry.add("spring.security.oauth2.client.provider.google.token-uri",
            () -> "http://localhost:" + wireMockServer.port() + "/oauth2/token");
        registry.add("spring.security.oauth2.client.provider.google.user-info-uri",
            () -> "http://localhost:" + wireMockServer.port() + "/oauth2/userinfo");

        // AI services configuration (pointing to WireMock)
        registry.add("anthropic.api.base-url", () -> "http://localhost:" + wireMockServer.port());
        registry.add("cohere.api.base-url", () -> "http://localhost:" + wireMockServer.port());
    }

    /**
     * Set up before each test
     */
    @BeforeEach
    void setUp() {
        // Reset WireMock stubs before each test
        wireMockServer.resetAll();

        // FIX 1: Reset REST Assured static configuration BEFORE initializing ApiTestClient
        // This prevents state pollution and ensures clean configuration
        // Critical for fixing GET request NPE issues
        io.restassured.RestAssured.reset();
        io.restassured.RestAssured.baseURI = "http://localhost";
        io.restassured.RestAssured.port = port;
        io.restassured.RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        
        // Initialize API client AFTER RestAssured reset
        // ApiTestClient constructor will set baseURI/port again, which is fine
        apiClient = new ApiTestClient(port);

        // Set up default mocks
        setupDefaultMocks();
    }

    /**
     * Clean up after each test to prevent connection leaks
     */
    @AfterEach
    void tearDown() {
        // FIX 1: Reset REST Assured static configuration after each test
        // This prevents state pollution between tests
        io.restassured.RestAssured.reset();
        io.restassured.RestAssured.requestSpecification = null;
        io.restassured.RestAssured.responseSpecification = null;
        
        // Clear API client auth
        if (apiClient != null) {
            apiClient.clearAuth();
        }
    }

    /**
     * Safely extract chatbot ID from response
     * Throws assertion error if ID is null or response is invalid
     */
    protected Long extractChatbotId(Response response) {
        // Verify response is successful
        int statusCode = response.getStatusCode();
        String responseBody = response.getBody().asString();
        
        assertTrue(statusCode == 200 || statusCode == 201, 
            "Expected status 200 or 201, got " + statusCode + ". Response: " + responseBody);
        
        // Check if response body is empty
        assertFalse(responseBody == null || responseBody.trim().isEmpty(), 
            "Response body should not be empty. Status: " + statusCode);
        
        // Extract ID - handle both Long and Integer types
        Object idObj = null;
        try {
            idObj = response.jsonPath().get("id");
        } catch (Exception e) {
            throw new AssertionError("Failed to extract 'id' from response. Response body: " + responseBody + ". Error: " + e.getMessage());
        }
        
        assertNotNull(idObj, "Chatbot ID should not be null. Response body: " + responseBody);
        
        // Convert to Long
        Long id;
        if (idObj instanceof Long) {
            id = (Long) idObj;
        } else if (idObj instanceof Integer) {
            id = ((Integer) idObj).longValue();
        } else if (idObj instanceof Number) {
            id = ((Number) idObj).longValue();
        } else {
            throw new AssertionError("Chatbot ID is not a number. Got: " + idObj.getClass() + ", value: " + idObj + ". Response body: " + responseBody);
        }
        
        assertNotNull(id, "Chatbot ID should not be null after conversion");
        return id;
    }

    /**
     * Set up default mocks for external services
     * Can be overridden by subclasses for specific test scenarios
     */
    protected void setupDefaultMocks() {
        // Mock Google OAuth token endpoint
        stubFor(post(urlEqualTo("/oauth2/token"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"access_token\": \"mock_access_token\", \"token_type\": \"Bearer\"}")));

        // Mock Google OAuth userinfo endpoint
        stubFor(get(urlEqualTo("/oauth2/userinfo"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"sub\": \"google_123\", \"email\": \"test@gmail.com\", \"name\": \"Test User\"}")));

        // Mock Anthropic AI endpoint
        stubFor(post(urlPathMatching("/v1/messages"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"content\": [{\"text\": \"Mock AI response\"}], \"role\": \"assistant\"}")));

        // Mock Cohere embeddings endpoint
        stubFor(post(urlPathMatching("/embed"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"embeddings\": [[0.1, 0.2, 0.3]]}")));

        // Mock Stripe customer creation
        stubFor(post(urlEqualTo("/v1/customers"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\": \"cus_mock123\", \"email\": \"test@example.com\"}")));

        // Mock Stripe checkout session creation
        // Use urlPathMatching to handle any query parameters or path variations
        stubFor(post(urlPathMatching(".*/v1/checkout/sessions.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"id\": \"cs_mock123\", \"url\": \"https://checkout.stripe.com/mock\"}")));
    }

    /**
     * Get the base URL for the test server
     */
    protected String getBaseUrl() {
        return "http://localhost:" + port;
    }

    /**
     * Get the WireMock base URL
     */
    protected String getWireMockUrl() {
        return "http://localhost:" + wireMockServer.port();
    }

    /**
     * Create an active subscription for a user (by email)
     * Useful for E2E tests that need subscriptions
     */
    protected Subscription createActiveSubscriptionForUser(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User with email " + email + " not found");
        }
        User user = userOpt.get();

        // Check if subscription already exists
        Optional<Subscription> existing = subscriptionRepository.findByUserId(user.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        // Create new active subscription
        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setStripeCustomerId("cus_test_" + UUID.randomUUID().toString().substring(0, 8));
        subscription.setStripeSubscriptionId("sub_test_" + UUID.randomUUID().toString().substring(0, 8));
        subscription.setStripePriceId("price_test_123");
        subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
        subscription.setPlan(Subscription.SubscriptionPlan.BASIC);
        subscription.setCurrentPeriodStart(LocalDateTime.now());
        subscription.setCurrentPeriodEnd(LocalDateTime.now().plusMonths(1));
        subscription.setPaymentRetryCount(0);

        return subscriptionRepository.save(subscription);
    }

    /**
     * Create a user via OAuth2 (simulates Google OAuth2 login)
     * This creates a user in the database as if they logged in via Google OAuth2
     * and returns a JWT token for authentication
     * 
     * @param email User email (from Google OAuth2)
     * @param googleId Google user ID (sub attribute)
     * @param name User name (from Google OAuth2)
     * @return JWT token for the created/authenticated user
     */
    protected String createOAuth2User(String email, String googleId, String name) {
        // Check if user already exists
        Optional<User> existingUser = userRepository.findByGoogleId(googleId);
        User user;
        
        if (existingUser.isPresent()) {
            user = existingUser.get();
            user.setLastLogin(LocalDateTime.now());
        } else {
            // Check if email already exists (link Google account)
            Optional<User> existingEmailUser = userRepository.findByEmail(email);
            if (existingEmailUser.isPresent()) {
                user = existingEmailUser.get();
                user.setGoogleId(googleId);
                user.setAuthProvider(User.AuthProvider.GOOGLE);
                user.setLastLogin(LocalDateTime.now());
            } else {
                // Create new user (as OAuth2 would)
                user = new User();
                user.setUsername(email); // Use email as username for OAuth2 users
                user.setEmail(email);
                user.setGoogleId(googleId);
                user.setAuthProvider(User.AuthProvider.GOOGLE);
                user.setEnabled(true);
                user.setLastLogin(LocalDateTime.now());
                
                // Set default USER role
                Set<String> roles = new HashSet<>();
                roles.add("USER");
                // First user becomes ADMIN
                if (userRepository.count() == 0) {
                    roles.add("ADMIN");
                }
                user.setRoles(roles);
            }
        }
        
        // Save user within a transaction to ensure proper persistence
        // Use TransactionTemplate to explicitly manage transaction boundaries
        final User userToSave = user; // Make effectively final for lambda
        User savedUser = transactionTemplate.execute(status -> {
            User u = userRepository.save(userToSave);
            entityManager.flush();
            entityManager.clear();
            return u;
        });
        
        // Small delay to ensure transaction is committed (for Testcontainers/PostgreSQL)
        // CI environments may need more time for database synchronization
        try {
            Thread.sleep(300); // Increased delay for CI environment
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verify user exists in a fresh query (simulating what HTTP request would do)
        Optional<User> verifyUser = userRepository.findByEmail(savedUser.getEmail());
        if (verifyUser.isEmpty()) {
            throw new IllegalStateException("User was not persisted before token generation. Email: " + savedUser.getEmail() + ", User ID: " + savedUser.getId());
        }
        
        // Generate JWT token for the user
        String token = jwtTokenProvider.generateToken(savedUser.getEmail());
        
        // Verify token can be validated immediately
        boolean isValid = jwtTokenProvider.validateToken(token);
        if (!isValid) {
            throw new IllegalStateException("Generated JWT token is invalid. This suggests JWT secret mismatch.");
        }
        
        // Verify username can be extracted from token
        String usernameFromToken = jwtTokenProvider.getUsernameFromToken(token);
        if (usernameFromToken == null || !savedUser.getEmail().equals(usernameFromToken)) {
            throw new IllegalStateException("Token username mismatch. Expected: " + savedUser.getEmail() + ", Got: " + usernameFromToken);
        }
        
        // Set token in API client
        apiClient.withAuth(token);
        
        return token;
    }

    /**
     * Create an OAuth2 user with default test values
     * Convenience method for tests
     * 
     * @param email User email
     * @return JWT token for the user
     */
    protected String createOAuth2User(String email) {
        String googleId = "google_" + UUID.randomUUID().toString().substring(0, 8);
        String name = email.split("@")[0]; // Use email prefix as name
        return createOAuth2User(email, googleId, name);
    }
}
