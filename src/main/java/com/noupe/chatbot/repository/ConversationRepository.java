package com.noupe.chatbot.repository;

import com.noupe.chatbot.model.Chatbot;
import com.noupe.chatbot.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Conversation entities
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    
    /**
     * Find conversation by chatbot and session ID
     */
    Optional<Conversation> findByChatbotAndSessionId(Chatbot chatbot, String sessionId);
    
    /**
     * Find all conversations for a chatbot
     */
    List<Conversation> findByChatbot(Chatbot chatbot);
    
    /**
     * Find active conversations for a chatbot
     */
    List<Conversation> findByChatbotAndIsActiveTrue(Chatbot chatbot);
    
    /**
     * Find conversations by language
     */
    List<Conversation> findByChatbotAndUserLanguage(Chatbot chatbot, String userLanguage);
    
    /**
     * Find conversations created in date range
     */
    @Query("SELECT c FROM Conversation c WHERE c.chatbot = :chatbot AND c.createdAt BETWEEN :startDate AND :endDate")
    List<Conversation> findByChatbotAndCreatedAtBetween(@Param("chatbot") Chatbot chatbot,
                                                        @Param("startDate") LocalDateTime startDate,
                                                        @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get conversation statistics
     */
    @Query("SELECT COUNT(c) FROM Conversation c WHERE c.chatbot = :chatbot")
    Long countByChatbot(@Param("chatbot") Chatbot chatbot);
    
    @Query("SELECT COUNT(c) FROM Conversation c WHERE c.chatbot = :chatbot AND c.isActive = true")
    Long countActiveByChatbot(@Param("chatbot") Chatbot chatbot);
    
    /**
     * Find conversations with minimum message count
     */
    @Query("SELECT c FROM Conversation c WHERE c.chatbot = :chatbot AND SIZE(c.messages) >= :minMessages")
    List<Conversation> findByChatbotWithMinMessages(@Param("chatbot") Chatbot chatbot, @Param("minMessages") Integer minMessages);
}
