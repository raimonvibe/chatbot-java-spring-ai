package com.tjanabot.chatbot.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tjanabot.chatbot.dto.ChatbotRequest;
import com.tjanabot.chatbot.dto.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Input Validation Security Tests")
class InputValidationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @ParameterizedTest
    @ValueSource(strings = {
        "<script>alert('XSS')</script>",
        "<img src=x onerror=alert(1)>",
        "<svg/onload=alert('XSS')>",
        "javascript:alert(1)",
        "<iframe src=\"javascript:alert('XSS')\"></iframe>"
    })
    @DisplayName("Should sanitize XSS attempts in registration")
    void shouldSanitizeXssInRegistration(String xssPayload) throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setUsername(xssPayload);
        request.setPassword("Test1234!");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest()); // Should reject malicious input
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "' OR '1'='1",
        "'; DROP TABLE users; --",
        "admin'--",
        "' UNION SELECT * FROM users--",
        "1' AND '1'='1"
    })
    @DisplayName("Should prevent SQL injection attempts")
    void shouldPreventSqlInjection(String sqlInjection) throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setUsername(sqlInjection);
        request.setPassword("Test1234!");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest()); // Should reject SQL injection attempts
    }

    @Test
    @DisplayName("Should reject email with null byte injection")
    void shouldRejectNullByteInjection() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com\u0000malicious.com");
        request.setUsername("testuser");
        request.setPassword("Test1234!");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject excessively long input")
    void shouldRejectExcessivelyLongInput() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setUsername("a".repeat(10000)); // Very long username
        request.setPassword("Test1234!");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should sanitize chatbot system prompt")
    void shouldSanitizeChatbotSystemPrompt() throws Exception {
        ChatbotRequest request = new ChatbotRequest();
        request.setName("Test Bot");
        request.setDescription("Normal description");
        request.setWebsiteUrl("https://example.com");
        request.setCustomPrompt("<script>malicious code</script>You are a helpful assistant");
        request.setPrimaryLanguage("en");

        mockMvc.perform(post("/api/chatbots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", "Bearer valid_token"))
            .andExpect(status().isBadRequest()); // Should reject or sanitize
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost/",
        "http://127.0.0.1/",
        "http://169.254.169.254/", // AWS metadata
        "http://metadata.google.internal/", // GCP metadata
        "http://10.0.0.1/", // Private IP
        "http://192.168.1.1/", // Private IP
        "file:///etc/passwd",
        "ftp://example.com"
    })
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should reject SSRF attempts in chatbot URL")
    void shouldRejectSsrfAttempts(String maliciousUrl) throws Exception {
        ChatbotRequest request = new ChatbotRequest();
        request.setName("Test Bot");
        request.setDescription("Description");
        request.setWebsiteUrl(maliciousUrl);
        request.setCustomPrompt("You are helpful");
        request.setPrimaryLanguage("en");

        mockMvc.perform(post("/api/chatbots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", "Bearer valid_token"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should validate email format strictly")
    void shouldValidateEmailFormatStrictly() throws Exception {
        String[] invalidEmails = {
            "not-an-email",
            "@example.com",
            "test@",
            "test space@example.com",
            "test@example",
            "test..double@example.com"
        };

        for (String invalidEmail : invalidEmails) {
            RegisterRequest request = new RegisterRequest();
            request.setEmail(invalidEmail);
            request.setUsername("testuser");
            request.setPassword("Test1234!");

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("Should reject control characters in input")
    void shouldRejectControlCharacters() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@example.com");
        request.setUsername("test\r\nuser"); // Control characters
        request.setPassword("Test1234!");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should enforce password complexity")
    void shouldEnforcePasswordComplexity() throws Exception {
        String[] weakPasswords = {
            "short",
            "alllowercase",
            "ALLUPPERCASE",
            "12345678",
            "NoSpecialChar1"
        };

        for (String weakPassword : weakPasswords) {
            RegisterRequest request = new RegisterRequest();
            request.setEmail("test@example.com");
            request.setUsername("testuser");
            request.setPassword(weakPassword);

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("Should reject common password patterns")
    void shouldRejectCommonPasswords() throws Exception {
        String[] commonPasswords = {
            "Password123!",
            "Welcome123!",
            "Admin123!",
            "Qwerty123!"
        };

        for (String commonPassword : commonPasswords) {
            RegisterRequest request = new RegisterRequest();
            request.setEmail("test@example.com");
            request.setUsername("testuser");
            request.setPassword(commonPassword);

            mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should prevent NoSQL injection in search")
    void shouldPreventNoSqlInjection() throws Exception {
        String noSqlInjection = "{\"$ne\": null}";

        mockMvc.perform(get("/api/chatbots/search")
                .param("query", noSqlInjection)
                .header("Authorization", "Bearer valid_token"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should reject requests with malformed JSON")
    void shouldRejectMalformedJson() throws Exception {
        String malformedJson = "{\"email\": \"test@example.com\", invalid json}";

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should prevent XML external entity (XXE) attacks")
    void shouldPreventXxeAttacks() throws Exception {
        String xxePayload = """
        <?xml version="1.0"?>
        <!DOCTYPE foo [
          <!ENTITY xxe SYSTEM "file:///etc/passwd">
        ]>
        <user>
          <email>&xxe;</email>
        </user>
        """;

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_XML)
                .content(xxePayload))
            .andExpect(status().isUnsupportedMediaType()); // Should not accept XML
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should validate numeric input ranges")
    void shouldValidateNumericRanges() throws Exception {
        // Test with invalid ID (negative number)
        mockMvc.perform(get("/api/chatbots/-1")
                .header("Authorization", "Bearer valid_token"))
            .andExpect(status().isBadRequest());

        // Test with excessively large ID
        mockMvc.perform(get("/api/chatbots/999999999999999")
                .header("Authorization", "Bearer valid_token"))
            .andExpect(status().isNotFound()); // Should handle gracefully
    }
}
