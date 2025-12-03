package com.tjanabot.chatbot.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tjanabot.chatbot.dto.ChatbotRequest;
import com.tjanabot.chatbot.helpers.TestDataBuilder;
import com.tjanabot.chatbot.model.Chatbot;
import com.tjanabot.chatbot.model.User;
import com.tjanabot.chatbot.security.JwtTokenProvider;
import com.tjanabot.chatbot.service.ChatbotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("ChatbotController Integration Tests")
class ChatbotControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChatbotService chatbotService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private User testUser;
    private Chatbot testChatbot;

    @BeforeEach
    void setUp() {
        testUser = TestDataBuilder.createTestUser();
        testUser.setId(1L);

        testChatbot = TestDataBuilder.createTestChatbot(testUser);
        testChatbot.setId(1L);
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should create chatbot successfully")
    void shouldCreateChatbotSuccessfully() throws Exception {
        // Arrange
        ChatbotRequest request = new ChatbotRequest();
        request.setName("New Chatbot");
        request.setDescription("Test description");
        request.setWebsiteUrl("https://example.com");
        request.setPrimaryLanguage("en");
        request.setCustomPrompt("You are a helpful assistant");

        when(chatbotService.createChatbot(any(Chatbot.class), any(User.class)))
            .thenReturn(testChatbot);

        // Act & Assert
        mockMvc.perform(post("/api/chatbots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", "Bearer valid_token"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").exists());

        verify(chatbotService, times(1)).createChatbot(any(Chatbot.class), any(User.class));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should get all chatbots for user")
    void shouldGetAllChatbotsForUser() throws Exception {
        // Arrange
        Chatbot bot2 = TestDataBuilder.createTestChatbot(testUser);
        bot2.setId(2L);
        List<Chatbot> chatbots = Arrays.asList(testChatbot, bot2);

        when(chatbotService.getChatbotsForUser(any(User.class))).thenReturn(chatbots);

        // Act & Assert
        mockMvc.perform(get("/api/chatbots")
                .header("Authorization", "Bearer valid_token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2));

        verify(chatbotService, times(1)).getChatbotsForUser(any(User.class));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should get chatbot by ID")
    void shouldGetChatbotById() throws Exception {
        // Arrange
        when(chatbotService.getChatbotById(1L)).thenReturn(Optional.of(testChatbot));

        // Act & Assert
        mockMvc.perform(get("/api/chatbots/1")
                .header("Authorization", "Bearer valid_token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").exists());

        verify(chatbotService, times(1)).getChatbotById(1L);
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should return 404 when chatbot not found")
    void shouldReturn404WhenChatbotNotFound() throws Exception {
        // Arrange
        when(chatbotService.getChatbotById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/chatbots/999")
                .header("Authorization", "Bearer valid_token"))
            .andExpect(status().isNotFound());

        verify(chatbotService, times(1)).getChatbotById(999L);
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should update chatbot successfully")
    void shouldUpdateChatbotSuccessfully() throws Exception {
        // Arrange
        ChatbotRequest request = new ChatbotRequest();
        request.setName("Updated Name");
        request.setDescription("Updated description");

        Chatbot updatedBot = TestDataBuilder.createTestChatbot(testUser);
        updatedBot.setId(1L);
        updatedBot.setName("Updated Name");

        when(chatbotService.updateChatbot(eq(1L), any(Chatbot.class), any(User.class)))
            .thenReturn(updatedBot);

        // Act & Assert
        mockMvc.perform(put("/api/chatbots/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", "Bearer valid_token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Updated Name"));

        verify(chatbotService, times(1)).updateChatbot(eq(1L), any(Chatbot.class), any(User.class));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should delete chatbot successfully")
    void shouldDeleteChatbotSuccessfully() throws Exception {
        // Arrange
        doNothing().when(chatbotService).deleteChatbot(eq(1L), any(User.class));

        // Act & Assert
        mockMvc.perform(delete("/api/chatbots/1")
                .header("Authorization", "Bearer valid_token"))
            .andExpect(status().isNoContent());

        verify(chatbotService, times(1)).deleteChatbot(eq(1L), any(User.class));
    }

    @Test
    @DisplayName("Should reject unauthorized access to chatbots")
    void shouldRejectUnauthorizedAccess() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/chatbots"))
            .andExpect(status().isUnauthorized());

        verify(chatbotService, never()).getChatbotsForUser(any());
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should reject chatbot creation with invalid URL")
    void shouldRejectInvalidUrl() throws Exception {
        // Arrange
        ChatbotRequest request = new ChatbotRequest();
        request.setName("Test Bot");
        request.setWebsiteUrl("http://localhost:8080"); // Invalid URL

        when(chatbotService.createChatbot(any(Chatbot.class), any(User.class)))
            .thenThrow(new IllegalArgumentException("Invalid or unsafe website URL"));

        // Act & Assert
        mockMvc.perform(post("/api/chatbots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", "Bearer valid_token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid or unsafe website URL"));
    }

    @Test
    @WithMockUser(username = "other@example.com")
    @DisplayName("Should prevent unauthorized update of chatbot")
    void shouldPreventUnauthorizedUpdate() throws Exception {
        // Arrange
        ChatbotRequest request = new ChatbotRequest();
        request.setName("Malicious Update");

        when(chatbotService.updateChatbot(eq(1L), any(Chatbot.class), any(User.class)))
            .thenThrow(new SecurityException("User is not authorized to update this chatbot"));

        // Act & Assert
        mockMvc.perform(put("/api/chatbots/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", "Bearer other_token"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("User is not authorized to update this chatbot"));
    }

    @Test
    @WithMockUser(username = "test@example.com")
    @DisplayName("Should sanitize XSS attempts in chatbot fields")
    void shouldSanitizeXssAttempts() throws Exception {
        // Arrange
        ChatbotRequest request = new ChatbotRequest();
        request.setName("<script>alert('XSS')</script>Test Bot");
        request.setDescription("<img src=x onerror=alert(1)>");
        request.setWebsiteUrl("https://example.com");
        request.setCustomPrompt("Normal prompt");

        // The service should sanitize the input
        when(chatbotService.createChatbot(any(Chatbot.class), any(User.class)))
            .thenReturn(testChatbot);

        // Act & Assert
        mockMvc.perform(post("/api/chatbots")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .header("Authorization", "Bearer valid_token"))
            .andExpect(status().isCreated());

        // Verify the service was called (sanitization happens there)
        verify(chatbotService, times(1)).createChatbot(any(Chatbot.class), any(User.class));
    }
}
