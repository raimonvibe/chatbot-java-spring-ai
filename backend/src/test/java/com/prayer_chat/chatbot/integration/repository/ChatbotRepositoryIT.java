package com.prayer_chat.chatbot.integration.repository;

import com.prayer_chat.chatbot.helpers.TestDataBuilder;
import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.User;
import com.prayer_chat.chatbot.repository.ChatbotRepository;
import com.prayer_chat.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
@ContextConfiguration(classes = com.prayer_chat.chatbot.AiChatbotApplication.class)
@AutoConfigureTestDatabase(replace = org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("ChatbotRepository Integration Tests")
class ChatbotRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private ChatbotRepository chatbotRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        chatbotRepository.deleteAll();
        userRepository.deleteAll();

        testUser = TestDataBuilder.createTestUser();
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("Should save and find chatbot by ID")
    void shouldSaveAndFindById() {
        // Arrange
        Chatbot chatbot = TestDataBuilder.createTestChatbot(testUser);

        // Act
        Chatbot saved = chatbotRepository.save(chatbot);
        Optional<Chatbot> found = chatbotRepository.findById(saved.getId());

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo(chatbot.getName());
        assertThat(found.get().getOwner().getId()).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("Should find all chatbots by owner ID")
    void shouldFindAllByOwnerId() {
        // Arrange
        Chatbot bot1 = TestDataBuilder.createTestChatbot(testUser);
        bot1.setName("Bot 1");
        Chatbot bot2 = TestDataBuilder.createTestChatbot(testUser);
        bot2.setName("Bot 2");

        chatbotRepository.save(bot1);
        chatbotRepository.save(bot2);

        // Act
        List<Chatbot> userBots = chatbotRepository.findByOwnerId(testUser.getId());

        // Assert
        assertThat(userBots).hasSize(2);
        assertThat(userBots).extracting(Chatbot::getName).containsExactlyInAnyOrder("Bot 1", "Bot 2");
    }

    @Test
    @DisplayName("Should find by id and owner id only when both match (owner-scoped delete)")
    void shouldFindByIdAndOwnerId() {
        Chatbot chatbot = TestDataBuilder.createTestChatbot(testUser);
        Chatbot saved = chatbotRepository.save(chatbot);
        User other = TestDataBuilder.createTestUser("other-owner@example.com");
        other = userRepository.save(other);

        assertThat(chatbotRepository.findByIdAndOwner_Id(saved.getId(), testUser.getId())).isPresent();
        assertThat(chatbotRepository.findByIdAndOwner_Id(saved.getId(), other.getId())).isEmpty();
    }

    @Test
    @DisplayName("Should find only active chatbots")
    void shouldFindOnlyActiveChatbots() {
        // Arrange
        Chatbot activeBot = TestDataBuilder.createTestChatbot(testUser);
        activeBot.setActive(true);
        activeBot.setName("Active Bot");

        Chatbot inactiveBot = TestDataBuilder.createTestChatbot(testUser);
        inactiveBot.setActive(false);
        inactiveBot.setName("Inactive Bot");

        chatbotRepository.save(activeBot);
        chatbotRepository.save(inactiveBot);

        // Act
        List<Chatbot> activeBots = chatbotRepository.findByOwnerId(testUser.getId()).stream()
            .filter(Chatbot::isActive)
            .toList();

        // Assert
        assertThat(activeBots).hasSize(1);
        assertThat(activeBots.get(0).getName()).isEqualTo("Active Bot");
        assertThat(activeBots.get(0).isActive()).isTrue();
    }

    @Test
    @DisplayName("Should find chatbots by language")
    void shouldFindChatbotsByLanguage() {
        // Arrange
        Chatbot englishBot = TestDataBuilder.createTestChatbot(testUser);
        englishBot.setPrimaryLanguage("en");
        englishBot.setName("English Bot");

        Chatbot dutchBot = TestDataBuilder.createTestChatbot(testUser);
        dutchBot.setPrimaryLanguage("nl");
        dutchBot.setName("Dutch Bot");

        chatbotRepository.save(englishBot);
        chatbotRepository.save(dutchBot);

        // Act
        List<Chatbot> englishBots = chatbotRepository.findByOwnerId(testUser.getId()).stream()
            .filter(bot -> "en".equals(bot.getPrimaryLanguage()))
            .toList();

        // Assert
        assertThat(englishBots).hasSize(1);
        assertThat(englishBots.get(0).getPrimaryLanguage()).isEqualTo("en");
        assertThat(englishBots.get(0).getName()).isEqualTo("English Bot");
    }

    @Test
    @DisplayName("Should update chatbot properties")
    void shouldUpdateChatbotProperties() {
        // Arrange
        Chatbot chatbot = TestDataBuilder.createTestChatbot(testUser);
        chatbot = chatbotRepository.save(chatbot);

        // Act
        chatbot.setName("Updated Name");
        chatbot.setDescription("Updated Description");
        chatbot.setCustomPrompt("Updated Prompt");
        Chatbot updated = chatbotRepository.save(chatbot);

        // Assert
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getDescription()).isEqualTo("Updated Description");
        assertThat(updated.getCustomPrompt()).isEqualTo("Updated Prompt");
    }

    @Test
    @DisplayName("Should delete chatbot by ID")
    void shouldDeleteChatbotById() {
        // Arrange
        Chatbot chatbot = TestDataBuilder.createTestChatbot(testUser);
        chatbot = chatbotRepository.save(chatbot);
        Long chatbotId = chatbot.getId();

        // Act
        chatbotRepository.deleteById(chatbotId);

        // Assert
        Optional<Chatbot> found = chatbotRepository.findById(chatbotId);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should count chatbots by owner")
    void shouldCountChatbotsByOwner() {
        // Arrange
        chatbotRepository.save(TestDataBuilder.createTestChatbot(testUser));
        chatbotRepository.save(TestDataBuilder.createTestChatbot(testUser));
        chatbotRepository.save(TestDataBuilder.createTestChatbot(testUser));

        // Act
        long count = chatbotRepository.findByOwnerId(testUser.getId()).size();

        // Assert
        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("Should maintain referential integrity with User")
    void shouldMaintainReferentialIntegrityWithUser() {
        // Arrange
        Chatbot chatbot = TestDataBuilder.createTestChatbot(testUser);
        chatbot = chatbotRepository.save(chatbot);

        // Act & Assert - Deleting chatbot should not delete user
        chatbotRepository.delete(chatbot);
        Optional<User> userStillExists = userRepository.findById(testUser.getId());
        assertThat(userStillExists).isPresent();
    }

    @Test
    @DisplayName("Should handle chatbot with long text fields")
    void shouldHandleLongTextFields() {
        // Arrange
        Chatbot chatbot = TestDataBuilder.createTestChatbot(testUser);
        String longDescription = "A".repeat(5000); // Very long description
        String longPrompt = "B".repeat(10000); // Very long custom prompt
        chatbot.setDescription(longDescription);
        chatbot.setCustomPrompt(longPrompt);

        // Act
        Chatbot saved = chatbotRepository.save(chatbot);
        Optional<Chatbot> found = chatbotRepository.findById(saved.getId());

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getDescription()).hasSize(5000);
        assertThat(found.get().getCustomPrompt()).hasSize(10000);
    }

    @Test
    @DisplayName("Should persist creation and update timestamps")
    void shouldPersistTimestamps() {
        // Arrange
        Chatbot chatbot = TestDataBuilder.createTestChatbot(testUser);

        // Act
        Chatbot saved = chatbotRepository.save(chatbot);

        // Assert
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
