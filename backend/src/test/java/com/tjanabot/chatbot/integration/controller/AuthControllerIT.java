package com.tjanabot.chatbot.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tjanabot.chatbot.helpers.TestAuthenticationHelper;
import com.tjanabot.chatbot.helpers.TestDataBuilder;
import com.tjanabot.chatbot.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.tjanabot.chatbot.config.MockAiConfiguration;
import com.tjanabot.chatbot.config.TestSecurityConfig;
import com.tjanabot.chatbot.config.TestJacksonConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController Integration Tests
 * 
 * Note: Email/password login has been removed. Only OAuth2 (Google) login is supported.
 * This test suite focuses on the /api/auth/me endpoint which returns current user info.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({MockAiConfiguration.class, TestSecurityConfig.class, TestJacksonConfiguration.class})
@DisplayName("AuthController Integration Tests")
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = TestDataBuilder.createTestUser("test@example.com");
        testUser.setId(1L);
        testUser.setAuthProvider(User.AuthProvider.GOOGLE); // OAuth2 user
    }

    @Test
    @DisplayName("Should return current user info for authenticated OAuth2 user")
    void shouldReturnCurrentUserInfo() throws Exception {
        // Arrange - Use OAuth2 authentication
        var auth = TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser);

        // Act & Assert
        mockMvc.perform(get("/api/auth/me")
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(testUser.getId()))
            .andExpect(jsonPath("$.username").value(testUser.getUsername()))
            .andExpect(jsonPath("$.email").value(testUser.getEmail()))
            .andExpect(jsonPath("$.roles").exists())
            .andExpect(jsonPath("$.authProvider").value("GOOGLE"));
    }

    @Test
    @DisplayName("Should return 401 for unauthenticated request")
    void shouldReturn401ForUnauthenticatedRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return user info with correct roles")
    void shouldReturnUserInfoWithCorrectRoles() throws Exception {
        // Arrange
        testUser.getRoles().add("ADMIN");
        var auth = TestAuthenticationHelper.createCustomOAuth2UserAuthentication(testUser);

        // Act & Assert
        mockMvc.perform(get("/api/auth/me")
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roles").isArray())
            .andExpect(jsonPath("$.roles[0]").exists());
    }
}
