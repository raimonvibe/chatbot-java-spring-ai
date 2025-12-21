package com.prayer_chat.chatbot.repository;

import com.prayer_chat.chatbot.model.Chatbot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Chatbot entities
 */
@Repository
public interface ChatbotRepository extends JpaRepository<Chatbot, Long> {
    
    /**
     * Find chatbots by active status
     */
    List<Chatbot> findByIsActiveTrue();
    
    /**
     * Find chatbot by embed code
     */
    Optional<Chatbot> findByEmbedCode(String embedCode);
    
    /**
     * Find chatbots by website URL
     */
    List<Chatbot> findByWebsiteUrl(String websiteUrl);
    
    /**
     * Find chatbots by name containing (case insensitive)
     */
    List<Chatbot> findByNameContainingIgnoreCase(String name);
    
    /**
     * Get chatbot statistics
     */
    @Query("SELECT COUNT(c) FROM Chatbot c")
    Long countAllChatbots();
    
    @Query("SELECT COUNT(c) FROM Chatbot c WHERE c.isActive = true")
    Long countActiveChatbots();
    
    /**
     * Find chatbots created in date range
     */
    @Query("SELECT c FROM Chatbot c WHERE c.createdAt BETWEEN :startDate AND :endDate")
    List<Chatbot> findByCreatedAtBetween(@Param("startDate") java.time.LocalDateTime startDate,
                                        @Param("endDate") java.time.LocalDateTime endDate);

    /**
     * Find chatbots by owner ID
     */
    List<Chatbot> findByOwnerId(Long ownerId);
    
    /**
     * Count chatbots by owner
     */
    @Query("SELECT COUNT(c) FROM Chatbot c WHERE c.owner.id = :ownerId")
    Long countByOwner(@Param("ownerId") Long ownerId);
    
    /**
     * Reset sequence for PostgreSQL (when sequence exists)
     */
    @Modifying
    @Transactional
    @Query(value = "ALTER SEQUENCE chatbots_id_seq RESTART WITH 1", nativeQuery = true)
    void resetSequencePostgreSQL();
    
    /**
     * Reset sequence for H2 (when no sequence exists)
     */
    @Modifying
    @Transactional
    @Query(value = "ALTER TABLE chatbots ALTER COLUMN id RESTART WITH 1", nativeQuery = true)
    void resetSequenceH2();
}
