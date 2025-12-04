package com.tjanabot.chatbot.unit.service;

import com.tjanabot.chatbot.helpers.TestDataBuilder;
import com.tjanabot.chatbot.model.Chatbot;
import com.tjanabot.chatbot.model.User;
import com.tjanabot.chatbot.repository.ChatbotRepository;
import com.tjanabot.chatbot.service.AuditService;
import com.tjanabot.chatbot.service.ChatbotService;
import com.tjanabot.chatbot.service.UrlValidationService;
import com.tjanabot.chatbot.util.XssSanitizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatbotService Unit Tests")
class ChatbotServiceTest {

    @Mock
    private ChatbotRepository chatbotRepository;

    @Mock
    private UrlValidationService urlValidationService;

    @Mock
    private AuditService auditService;

    @Mock
    private XssSanitizer xssSanitizer;

    @InjectMocks
    private ChatbotService chatbotService;

    private User testUser;
    private Chatbot testChatbot;

    @BeforeEach
    void setUp() {
        testUser = TestDataBuilder.createTestUser();
        testUser.setId(1L);

        testChatbot = TestDataBuilder.createTestChatbot(testUser);
        testChatbot.setId(1L);

        // Default behavior: return input as-is for sanitization (lenient for tests that don't use it)
        lenient().when(xssSanitizer.sanitize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Should create chatbot successfully")
    void shouldCreateChatbotSuccessfully() {
        // Arrange
        when(urlValidationService.isValid(testChatbot.getWebsiteUrl())).thenReturn(true);
        when(chatbotRepository.save(any(Chatbot.class))).thenReturn(testChatbot);

        // Act
        Chatbot created = chatbotService.createChatbot(testChatbot, testUser);

        // Assert
        assertThat(created).isNotNull();
        assertThat(created.getOwner()).isEqualTo(testUser);
        verify(chatbotRepository, times(1)).save(testChatbot);
        verify(auditService, times(1)).log(any(), any(), anyString(), eq(testUser), isNull(), isNull());
    }

    @Test
    @DisplayName("Should reject chatbot with invalid URL")
    void shouldRejectChatbotWithInvalidUrl() {
        // Arrange
        testChatbot.setWebsiteUrl("http://localhost:8080");
        when(urlValidationService.isValid("http://localhost:8080")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> chatbotService.createChatbot(testChatbot, testUser))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid or unsafe website URL");

        verify(chatbotRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update chatbot successfully")
    void shouldUpdateChatbotSuccessfully() {
        // Arrange
        Chatbot updates = new Chatbot();
        updates.setName("Updated Bot Name");
        updates.setDescription("Updated description");
        updates.setWebsiteUrl("https://updated-site.com");

        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));
        when(urlValidationService.isValid("https://updated-site.com")).thenReturn(true);
        when(chatbotRepository.save(any(Chatbot.class))).thenReturn(testChatbot);

        // Act
        Chatbot updated = chatbotService.updateChatbot(1L, updates, testUser);

        // Assert
        assertThat(updated.getName()).isEqualTo("Updated Bot Name");
        assertThat(updated.getDescription()).isEqualTo("Updated description");
        assertThat(updated.getWebsiteUrl()).isEqualTo("https://updated-site.com");
        verify(chatbotRepository, times(1)).save(testChatbot);
        verify(auditService, times(1)).log(any(), any(), anyString(), eq(testUser), isNull(), isNull());
    }

    @Test
    @DisplayName("Should prevent unauthorized update")
    void shouldPreventUnauthorizedUpdate() {
        // Arrange
        User unauthorizedUser = TestDataBuilder.createTestUser();
        unauthorizedUser.setId(2L);

        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));

        // Act & Assert
        assertThatThrownBy(() -> chatbotService.updateChatbot(1L, new Chatbot(), unauthorizedUser))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("not authorized");

        verify(chatbotRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should delete chatbot successfully")
    void shouldDeleteChatbotSuccessfully() {
        // Arrange
        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));

        // Act
        chatbotService.deleteChatbot(1L, testUser);

        // Assert
        verify(chatbotRepository, times(1)).delete(testChatbot);
        verify(auditService, times(1)).log(any(), any(), anyString(), eq(testUser), isNull(), isNull());
    }

    @Test
    @DisplayName("Should prevent unauthorized deletion")
    void shouldPreventUnauthorizedDeletion() {
        // Arrange
        User unauthorizedUser = TestDataBuilder.createTestUser();
        unauthorizedUser.setId(2L);

        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));

        // Act & Assert
        assertThatThrownBy(() -> chatbotService.deleteChatbot(1L, unauthorizedUser))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("not authorized");

        verify(chatbotRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Should get all chatbots for user")
    void shouldGetAllChatbotsForUser() {
        // Arrange
        Chatbot bot2 = TestDataBuilder.createTestChatbot(testUser);
        bot2.setId(2L);

        List<Chatbot> userBots = Arrays.asList(testChatbot, bot2);
        when(chatbotRepository.findByOwnerId(1L)).thenReturn(userBots);

        // Act
        List<Chatbot> result = chatbotService.getChatbotsForUser(testUser);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result).contains(testChatbot, bot2);
        verify(chatbotRepository, times(1)).findByOwnerId(1L);
    }

    @Test
    @DisplayName("Should get chatbot by ID")
    void shouldGetChatbotById() {
        // Arrange
        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));

        // Act
        Optional<Chatbot> result = chatbotService.getChatbotById(1L);

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testChatbot);
        verify(chatbotRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Should return empty when chatbot not found")
    void shouldReturnEmptyWhenChatbotNotFound() {
        // Arrange
        when(chatbotRepository.findById(999L)).thenReturn(Optional.empty());

        // Act
        Optional<Chatbot> result = chatbotService.getChatbotById(999L);

        // Assert
        assertThat(result).isEmpty();
        verify(chatbotRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("Should toggle chatbot active status")
    void shouldToggleChatbotActiveStatus() {
        // Arrange
        testChatbot.setActive(true);
        when(chatbotRepository.findById(1L)).thenReturn(Optional.of(testChatbot));
        when(chatbotRepository.save(any(Chatbot.class))).thenReturn(testChatbot);

        // Act
        Chatbot result = chatbotService.toggleChatbotStatus(1L, testUser);

        // Assert
        assertThat(result.isActive()).isFalse();
        verify(chatbotRepository, times(1)).save(testChatbot);
        verify(auditService, times(1)).log(any(), any(), anyString(), eq(testUser), isNull(), isNull());
    }

    @Test
    @DisplayName("Should validate required fields when creating chatbot")
    void shouldValidateRequiredFieldsWhenCreating() {
        // Arrange
        Chatbot invalidBot = new Chatbot();
        invalidBot.setName("");
        invalidBot.setWebsiteUrl("https://example.com");

        // Act & Assert
        assertThatThrownBy(() -> chatbotService.createChatbot(invalidBot, testUser))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("required");

        verify(chatbotRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should sanitize user input in chatbot fields")
    void shouldSanitizeUserInput() {
        // Arrange
        Chatbot botWithScript = TestDataBuilder.createTestChatbot(testUser);
        botWithScript.setDescription("<script>alert('XSS')</script>Normal description");

        // Mock sanitization to remove script tags (simulating real XSS sanitization)
        when(xssSanitizer.sanitize("<script>alert('XSS')</script>Normal description"))
            .thenReturn("Normal description");
        when(xssSanitizer.sanitize(botWithScript.getName())).thenReturn(botWithScript.getName());

        when(urlValidationService.isValid(anyString())).thenReturn(true);
        ArgumentCaptor<Chatbot> captor = ArgumentCaptor.forClass(Chatbot.class);
        when(chatbotRepository.save(captor.capture())).thenReturn(botWithScript);

        // Act
        chatbotService.createChatbot(botWithScript, testUser);

        // Assert
        verify(xssSanitizer, atLeastOnce()).sanitize(anyString());
        Chatbot saved = captor.getValue();
        assertThat(saved.getDescription()).doesNotContain("<script>");
        assertThat(saved.getDescription()).contains("Normal description");
    }
}
