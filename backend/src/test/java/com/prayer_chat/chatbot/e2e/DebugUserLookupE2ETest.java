package com.prayer_chat.chatbot.e2e;

import com.prayer_chat.chatbot.helpers.E2ETestBase;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.UserRepository;
import com.prayer_chat.chatbot.security.JwtTokenProvider;
import com.prayer_chat.chatbot.service.CustomUserDetailsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Debug test to understand why user lookup fails in JWT authentication
 */
class DebugUserLookupE2ETest extends E2ETestBase {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("Debug: Test user lookup after creation")
    void debugUserLookup() {
        String email = "debug@example.com";
        
        // Step 1: Create user
        System.out.println("=== Step 1: Creating user ===");
        String token = createOAuth2User(email);
        System.out.println("Token generated: " + (token != null ? token.substring(0, Math.min(20, token.length())) + "..." : "null"));
        
        // Step 2: Verify user exists in repository
        System.out.println("\n=== Step 2: Verifying user in repository ===");
        Optional<User> userOpt = userRepository.findByEmail(email);
        System.out.println("User found by email: " + userOpt.isPresent());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            System.out.println("User ID: " + user.getId());
            System.out.println("User email: " + user.getEmail());
            System.out.println("User username: " + user.getUsername());
        } else {
            System.out.println("ERROR: User not found in repository!");
            fail("User should exist in repository");
        }
        
        // Step 3: Extract email from token
        System.out.println("\n=== Step 3: Extracting email from token ===");
        String emailFromToken = jwtTokenProvider.getUsernameFromToken(token);
        System.out.println("Email from token: " + emailFromToken);
        System.out.println("Email matches: " + email.equals(emailFromToken));
        
        // Step 4: Try to load user by email using UserDetailsService
        System.out.println("\n=== Step 4: Loading user via UserDetailsService ===");
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);
            System.out.println("SUCCESS: User loaded via UserDetailsService");
            System.out.println("UserDetails username: " + userDetails.getUsername());
            System.out.println("UserDetails class: " + userDetails.getClass().getSimpleName());
        } catch (UsernameNotFoundException e) {
            System.out.println("ERROR: UsernameNotFoundException: " + e.getMessage());
            e.printStackTrace();
            fail("User should be loadable via UserDetailsService");
        } catch (Exception e) {
            System.out.println("ERROR: Unexpected exception: " + e.getClass().getSimpleName());
            System.out.println("Message: " + e.getMessage());
            e.printStackTrace();
            fail("Unexpected exception: " + e.getMessage());
        }
        
        // Step 5: Try to load user by email from token
        System.out.println("\n=== Step 5: Loading user via UserDetailsService with email from token ===");
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(emailFromToken);
            System.out.println("SUCCESS: User loaded via UserDetailsService with email from token");
            System.out.println("UserDetails username: " + userDetails.getUsername());
        } catch (UsernameNotFoundException e) {
            System.out.println("ERROR: UsernameNotFoundException: " + e.getMessage());
            e.printStackTrace();
            fail("User should be loadable via UserDetailsService with email from token");
        } catch (Exception e) {
            System.out.println("ERROR: Unexpected exception: " + e.getClass().getSimpleName());
            System.out.println("Message: " + e.getMessage());
            e.printStackTrace();
            fail("Unexpected exception: " + e.getMessage());
        }
        
        System.out.println("\n=== Test completed successfully ===");
    }

    @Test
    @DisplayName("Debug: Test HTTP request with JWT token")
    void debugHttpRequest() {
        String email = "debughttp@example.com";
        
        // Step 1: Create user and get token
        System.out.println("=== Step 1: Creating user and getting token ===");
        String token = createOAuth2User(email);
        createActiveSubscriptionForUser(email);
        System.out.println("Token generated: " + (token != null ? token.substring(0, Math.min(20, token.length())) + "..." : "null"));
        
        // Step 2: Verify user exists
        System.out.println("\n=== Step 2: Verifying user exists ===");
        Optional<User> userOpt = userRepository.findByEmail(email);
        System.out.println("User found: " + userOpt.isPresent());
        assertTrue(userOpt.isPresent(), "User should exist");
        
        // Step 3: Extract email from token
        System.out.println("\n=== Step 3: Extracting email from token ===");
        String emailFromToken = jwtTokenProvider.getUsernameFromToken(token);
        System.out.println("Email from token: " + emailFromToken);
        
        // Step 4: Try to load user via UserDetailsService (simulating what filter does)
        System.out.println("\n=== Step 4: Loading user via UserDetailsService (simulating filter) ===");
        try {
            UserDetails userDetails = userDetailsService.loadUserByUsername(emailFromToken);
            System.out.println("SUCCESS: User loaded: " + userDetails.getUsername());
        } catch (Exception e) {
            System.out.println("ERROR: Failed to load user: " + e.getMessage());
            e.printStackTrace();
            fail("User should be loadable");
        }
        
        // Step 5: Make HTTP request with token
        System.out.println("\n=== Step 5: Making HTTP request with token ===");
        System.out.println("Using WebTestClient to GET /api/chatbots");
        System.out.println("Token: " + token.substring(0, Math.min(30, token.length())) + "...");
        
        AtomicReference<Integer> statusCodeRef = new AtomicReference<>();
        AtomicReference<String> responseBodyRef = new AtomicReference<>();
        
        try {
            webApiClient.withAuth(token).getChatbots()
                .expectBody()
                .consumeWith(result -> {
                    int status = result.getStatus().value();
                    statusCodeRef.set(status);
                    byte[] body = result.getResponseBody();
                    if (body != null) {
                        responseBodyRef.set(new String(body));
                    }
                    System.out.println("Response status: " + status);
                    System.out.println("Response body: " + (body != null ? new String(body).substring(0, Math.min(200, body.length)) : "null"));
                });
        } catch (Exception e) {
            System.out.println("ERROR: Exception during HTTP request: " + e.getClass().getSimpleName());
            System.out.println("Message: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("\n=== Final Status ===");
        System.out.println("Status code: " + statusCodeRef.get());
        if (statusCodeRef.get() == 401) {
            System.out.println("ERROR: Got 401 UNAUTHORIZED - authentication failed");
            System.out.println("This suggests the JWT filter could not authenticate the user");
        } else if (statusCodeRef.get() == 200) {
            System.out.println("SUCCESS: Got 200 OK - authentication worked!");
        }
        
        // This test is for debugging, so we don't fail it
        // Just print the results
    }
}
