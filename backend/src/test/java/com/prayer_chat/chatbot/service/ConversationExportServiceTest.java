package com.prayer_chat.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.Conversation;
import com.prayer_chat.chatbot.model.Message;
import com.prayer_chat.chatbot.model.Message.MessageType;
import com.prayer_chat.chatbot.repository.ConversationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ConversationExportService
 * Tests conversation export to JSON and CSV formats
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Conversation Export Service Tests")
class ConversationExportServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    private ConversationExportService exportService;
    private ObjectMapper objectMapper;
    private Chatbot testChatbot;

    @BeforeEach
    void setUp() {
        exportService = new ConversationExportService(conversationRepository);
        objectMapper = new ObjectMapper();
        testChatbot = createTestChatbot();
    }

    // ========== JSON Export Tests - Single Conversation ==========

    @Test
    @DisplayName("Should export single conversation to JSON successfully")
    void shouldExportSingleConversationToJson() throws Exception {
        // Arrange
        Conversation conversation = createTestConversation(1L, testChatbot);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        // Act
        String json = exportService.exportConversationToJson(1L);

        // Assert
        assertThat(json).isNotNull();
        assertThat(json).isNotEmpty();

        JsonNode jsonNode = objectMapper.readTree(json);
        assertThat(jsonNode.get("conversation_id").asLong()).isEqualTo(1L);
        assertThat(jsonNode.get("user_ip").asText()).isEqualTo("192.168.1.1");
        assertThat(jsonNode.get("language").asText()).isEqualTo("en");
        assertThat(jsonNode.get("is_active").asBoolean()).isTrue();
        assertThat(jsonNode.get("message_count").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should include messages in JSON export")
    void shouldIncludeMessages_inJsonExport() throws Exception {
        // Arrange
        Conversation conversation = createTestConversation(1L, testChatbot);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        // Act
        String json = exportService.exportConversationToJson(1L);

        // Assert
        JsonNode jsonNode = objectMapper.readTree(json);
        JsonNode messages = jsonNode.get("messages");

        assertThat(messages).isNotNull();
        assertThat(messages.isArray()).isTrue();
        assertThat(messages.size()).isEqualTo(2);

        JsonNode firstMessage = messages.get(0);
        assertThat(firstMessage.get("sender").asText()).isEqualTo("user");
        assertThat(firstMessage.get("content").asText()).isEqualTo("Hello chatbot");
        assertThat(firstMessage.get("type").asText()).isEqualTo("TEXT");
    }

    @Test
    @DisplayName("Should throw exception when conversation not found for JSON export")
    void shouldThrowException_whenConversationNotFoundForJson() {
        // Arrange
        when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> exportService.exportConversationToJson(999L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Conversation not found");
    }

    // ========== JSON Export Tests - Multiple Conversations ==========

    @Test
    @DisplayName("Should export multiple conversations to JSON successfully")
    void shouldExportMultipleConversationsToJson() throws Exception {
        // Arrange
        List<Conversation> conversations = Arrays.asList(
            createTestConversation(1L, testChatbot),
            createTestConversation(2L, testChatbot)
        );
        when(conversationRepository.findByChatbotId(1L)).thenReturn(conversations);

        // Act
        String json = exportService.exportConversationsToJson(1L);

        // Assert
        assertThat(json).isNotNull();
        assertThat(json).isNotEmpty();

        JsonNode jsonNode = objectMapper.readTree(json);
        assertThat(jsonNode.get("chatbot_id").asLong()).isEqualTo(1L);
        assertThat(jsonNode.get("total_conversations").asInt()).isEqualTo(2);

        JsonNode conversationArray = jsonNode.get("conversations");
        assertThat(conversationArray).isNotNull();
        assertThat(conversationArray.isArray()).isTrue();
        assertThat(conversationArray.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should handle empty conversation list in JSON export")
    void shouldHandleEmptyConversationList_inJsonExport() throws Exception {
        // Arrange
        when(conversationRepository.findByChatbotId(1L)).thenReturn(Arrays.asList());

        // Act
        String json = exportService.exportConversationsToJson(1L);

        // Assert
        assertThat(json).isNotNull();
        JsonNode jsonNode = objectMapper.readTree(json);
        assertThat(jsonNode.get("total_conversations").asInt()).isEqualTo(0);
        assertThat(jsonNode.get("conversations").size()).isEqualTo(0);
    }

    // ========== CSV Export Tests - Single Conversation ==========

    @Test
    @DisplayName("Should export single conversation to CSV successfully")
    void shouldExportSingleConversationToCsv() throws Exception {
        // Arrange
        Conversation conversation = createTestConversation(1L, testChatbot);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        // Act
        String csv = exportService.exportConversationToCsv(1L);

        // Assert
        assertThat(csv).isNotNull();
        assertThat(csv).isNotEmpty();
        assertThat(csv).contains("Message ID,Timestamp,Sender,Message Type,Content,Response Time (ms)");
        assertThat(csv).contains("User,TEXT,Hello chatbot");
        assertThat(csv).contains("Bot,TEXT,Hi there!");
    }

    @Test
    @DisplayName("Should properly escape CSV special characters")
    void shouldEscapeCsvSpecialCharacters() throws Exception {
        // Arrange
        Conversation conversation = createConversationWithSpecialCharacters(1L, testChatbot);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        // Act
        String csv = exportService.exportConversationToCsv(1L);

        // Assert
        assertThat(csv).contains("\"Hello, chatbot\""); // Comma should be quoted
        assertThat(csv).contains("\"Line 1\nLine 2\""); // Newline should be quoted
    }

    @Test
    @DisplayName("Should throw exception when conversation not found for CSV export")
    void shouldThrowException_whenConversationNotFoundForCsv() {
        // Arrange
        when(conversationRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> exportService.exportConversationToCsv(999L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Conversation not found");
    }

    // ========== CSV Export Tests - Multiple Conversations ==========

    @Test
    @DisplayName("Should export multiple conversations to CSV successfully")
    void shouldExportMultipleConversationsToCsv() throws Exception {
        // Arrange
        List<Conversation> conversations = Arrays.asList(
            createTestConversation(1L, testChatbot),
            createTestConversation(2L, testChatbot)
        );
        when(conversationRepository.findByChatbotId(1L)).thenReturn(conversations);

        // Act
        String csv = exportService.exportConversationsToCsv(1L);

        // Assert
        assertThat(csv).isNotNull();
        assertThat(csv).isNotEmpty();
        assertThat(csv).contains("Conversation ID,User IP,Language,Created At,Last Message At,Message Count");
        assertThat(csv).contains("192.168.1.1");
        assertThat(csv).contains("Hello chatbot");
    }

    @Test
    @DisplayName("Should handle empty conversation list in CSV export")
    void shouldHandleEmptyConversationList_inCsvExport() throws Exception {
        // Arrange
        when(conversationRepository.findByChatbotId(1L)).thenReturn(Arrays.asList());

        // Act
        String csv = exportService.exportConversationsToCsv(1L);

        // Assert
        assertThat(csv).isNotNull();
        assertThat(csv).contains("Conversation ID,User IP,Language");
    }

    // ========== Edge Cases ==========

    @Test
    @DisplayName("Should handle conversation with no messages")
    void shouldHandleConversation_withNoMessages() throws Exception {
        // Arrange
        Conversation conversation = createEmptyConversation(1L, testChatbot);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        // Act
        String json = exportService.exportConversationToJson(1L);

        // Assert
        JsonNode jsonNode = objectMapper.readTree(json);
        assertThat(jsonNode.get("message_count").asInt()).isEqualTo(0);
        assertThat(jsonNode.get("messages").size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should handle conversation with null user agent")
    void shouldHandleConversation_withNullUserAgent() throws Exception {
        // Arrange
        Conversation conversation = createTestConversation(1L, testChatbot);
        conversation.setUserAgent(null);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        // Act
        String json = exportService.exportConversationToJson(1L);

        // Assert
        JsonNode jsonNode = objectMapper.readTree(json);
        assertThat(jsonNode.get("user_agent").isNull()).isTrue();
    }

    @Test
    @DisplayName("Should handle message with null response time")
    void shouldHandleMessage_withNullResponseTime() throws Exception {
        // Arrange
        Conversation conversation = createTestConversation(1L, testChatbot);
        conversation.getMessages().get(0).setResponseTimeMs(null);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        // Act
        String csv = exportService.exportConversationToCsv(1L);

        // Assert
        assertThat(csv).isNotNull();
        String[] lines = csv.split("\n");
        assertThat(lines.length).isGreaterThan(1);
    }

    @Test
    @DisplayName("Should format timestamps correctly in CSV")
    void shouldFormatTimestamps_correctlyInCsv() throws Exception {
        // Arrange
        Conversation conversation = createTestConversation(1L, testChatbot);
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        // Act
        String csv = exportService.exportConversationToCsv(1L);

        // Assert
        assertThat(csv).matches("(?s).*\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.*");
    }

    // ========== Helper Methods ==========

    private Chatbot createTestChatbot() {
        Chatbot chatbot = new Chatbot();
        chatbot.setId(1L);
        chatbot.setName("Test Chatbot");
        chatbot.setWebsiteUrl("https://example.com");
        return chatbot;
    }

    private Conversation createTestConversation(Long id, Chatbot chatbot) {
        Conversation conversation = new Conversation(chatbot, "session-" + id);
        conversation.setId(id);
        conversation.setUserIp("192.168.1.1");
        conversation.setUserLanguage("en");
        conversation.setUserAgent("Mozilla/5.0");
        conversation.setCreatedAt(LocalDateTime.now().minusHours(1));
        conversation.setIsActive(true);

        // Create messages
        Message userMessage = new Message(conversation, MessageType.TEXT, "Hello chatbot", true);
        userMessage.setId(id * 10 + 1);
        userMessage.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        userMessage.setResponseTimeMs(100);

        Message botMessage = new Message(conversation, MessageType.TEXT, "Hi there!", false);
        botMessage.setId(id * 10 + 2);
        botMessage.setCreatedAt(LocalDateTime.now().minusMinutes(29));
        botMessage.setResponseTimeMs(250);

        conversation.setMessages(Arrays.asList(userMessage, botMessage));

        return conversation;
    }

    private Conversation createConversationWithSpecialCharacters(Long id, Chatbot chatbot) {
        Conversation conversation = new Conversation(chatbot, "session-" + id);
        conversation.setId(id);
        conversation.setUserIp("192.168.1.1");
        conversation.setUserLanguage("en");
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setIsActive(true);

        Message message1 = new Message(conversation, MessageType.TEXT, "Hello, chatbot", true);
        message1.setId(id * 10 + 1);
        message1.setCreatedAt(LocalDateTime.now());
        message1.setResponseTimeMs(100);

        Message message2 = new Message(conversation, MessageType.TEXT, "Line 1\nLine 2", false);
        message2.setId(id * 10 + 2);
        message2.setCreatedAt(LocalDateTime.now());
        message2.setResponseTimeMs(200);

        conversation.setMessages(Arrays.asList(message1, message2));

        return conversation;
    }

    private Conversation createEmptyConversation(Long id, Chatbot chatbot) {
        Conversation conversation = new Conversation(chatbot, "session-" + id);
        conversation.setId(id);
        conversation.setUserIp("192.168.1.1");
        conversation.setUserLanguage("en");
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setIsActive(true);
        conversation.setMessages(Arrays.asList());

        return conversation;
    }
}
