package com.tjanabot.chatbot.integration.repository;

import com.tjanabot.chatbot.helpers.TestDataBuilder;
import com.tjanabot.chatbot.model.User;
import com.tjanabot.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("UserRepository Integration Tests")
class UserRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should save and find user by email")
    void shouldSaveAndFindByEmail() {
        // Arrange
        User user = TestDataBuilder.createTestUser("test@example.com");

        // Act
        User saved = userRepository.save(user);
        Optional<User> found = userRepository.findByEmail("test@example.com");

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Should find user by Google ID")
    void shouldFindUserByGoogleId() {
        // Arrange
        User googleUser = TestDataBuilder.createGoogleUser("google@example.com", "google_123");
        userRepository.save(googleUser);

        // Act
        Optional<User> found = userRepository.findByGoogleId("google_123");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getGoogleId()).isEqualTo("google_123");
        assertThat(found.get().getAuthProvider()).isEqualTo(User.AuthProvider.GOOGLE);
    }

    @Test
    @DisplayName("Should find user by username")
    void shouldFindUserByUsername() {
        // Arrange
        User user = TestDataBuilder.createTestUser();
        user.setUsername("uniqueuser");
        userRepository.save(user);

        // Act
        Optional<User> found = userRepository.findByUsername("uniqueuser");

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("uniqueuser");
    }

    @Test
    @DisplayName("Should check if email exists")
    void shouldCheckIfEmailExists() {
        // Arrange
        User user = TestDataBuilder.createTestUser("exists@example.com");
        userRepository.save(user);

        // Act
        boolean exists = userRepository.existsByEmail("exists@example.com");
        boolean notExists = userRepository.existsByEmail("notexists@example.com");

        // Assert
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("Should check if username exists")
    void shouldCheckIfUsernameExists() {
        // Arrange
        User user = TestDataBuilder.createTestUser();
        user.setUsername("existinguser");
        userRepository.save(user);

        // Act
        boolean exists = userRepository.existsByUsername("existinguser");
        boolean notExists = userRepository.existsByUsername("nonexistent");

        // Assert
        assertThat(exists).isTrue();
        assertThat(notExists).isFalse();
    }

    @Test
    @DisplayName("Should enforce unique email constraint")
    void shouldEnforceUniqueEmailConstraint() {
        // Arrange
        User user1 = TestDataBuilder.createTestUser("duplicate@example.com");
        userRepository.save(user1);

        User user2 = TestDataBuilder.createTestUser("duplicate@example.com");
        user2.setUsername("different_username");

        // Act & Assert
        assertThatThrownBy(() -> {
            userRepository.save(user2);
            userRepository.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should enforce unique username constraint")
    void shouldEnforceUniqueUsernameConstraint() {
        // Arrange
        User user1 = TestDataBuilder.createTestUser();
        user1.setUsername("uniquename");
        userRepository.save(user1);

        User user2 = TestDataBuilder.createTestUser("different@example.com");
        user2.setUsername("uniquename");

        // Act & Assert
        assertThatThrownBy(() -> {
            userRepository.save(user2);
            userRepository.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should enforce unique Google ID constraint")
    void shouldEnforceUniqueGoogleIdConstraint() {
        // Arrange
        User user1 = TestDataBuilder.createGoogleUser("user1@gmail.com", "google_unique");
        userRepository.save(user1);

        User user2 = TestDataBuilder.createGoogleUser("user2@gmail.com", "google_unique");

        // Act & Assert
        assertThatThrownBy(() -> {
            userRepository.save(user2);
            userRepository.flush();
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should update user last login timestamp")
    void shouldUpdateUserLastLogin() {
        // Arrange
        User user = TestDataBuilder.createTestUser();
        user = userRepository.save(user);
        assertThat(user.getLastLogin()).isNull();

        // Act
        user.setLastLogin(java.time.LocalDateTime.now());
        User updated = userRepository.save(user);

        // Assert
        assertThat(updated.getLastLogin()).isNotNull();
    }

    @Test
    @DisplayName("Should delete user by ID")
    void shouldDeleteUserById() {
        // Arrange
        User user = TestDataBuilder.createTestUser();
        user = userRepository.save(user);
        Long userId = user.getId();

        // Act
        userRepository.deleteById(userId);

        // Assert
        Optional<User> found = userRepository.findById(userId);
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("Should support both LOCAL and GOOGLE auth providers")
    void shouldSupportMultipleAuthProviders() {
        // Arrange
        User localUser = TestDataBuilder.createTestUser("local@example.com");
        User googleUser = TestDataBuilder.createGoogleUser("google@example.com", "google_456");

        // Act
        userRepository.save(localUser);
        userRepository.save(googleUser);

        // Assert
        Optional<User> foundLocal = userRepository.findByEmail("local@example.com");
        Optional<User> foundGoogle = userRepository.findByEmail("google@example.com");

        assertThat(foundLocal).isPresent();
        assertThat(foundLocal.get().getAuthProvider()).isEqualTo(User.AuthProvider.LOCAL);
        assertThat(foundLocal.get().getPassword()).isNotNull();

        assertThat(foundGoogle).isPresent();
        assertThat(foundGoogle.get().getAuthProvider()).isEqualTo(User.AuthProvider.GOOGLE);
        assertThat(foundGoogle.get().getGoogleId()).isNotNull();
    }
}
