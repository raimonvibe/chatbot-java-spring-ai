package com.prayer_chat.chatbot.repository;

import com.prayer_chat.chatbot.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for User entities
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by username
     */
    Optional<User> findByUsername(String username);

    /**
     * Find user by email
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if username exists
     */
    boolean existsByUsername(String username);

    /**
     * Check if email exists
     */
    boolean existsByEmail(String email);

    /**
     * Find user by Google ID
     */
    Optional<User> findByGoogleId(String googleId);

    /**
     * Find user by ID with pessimistic lock for concurrent cost updates.
     * This prevents race conditions when multiple requests try to update cost simultaneously.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM com.prayer_chat.chatbot.model.User u WHERE u.id = :id")
    Optional<User> findByIdWithLock(@Param("id") Long id);
}
