package com.noupe.chatbot.repository;

import com.noupe.chatbot.model.Conversation;
import com.noupe.chatbot.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for Message entities
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    
    /**
     * Find messages by conversation
     */
    List<Message> findByConversation(Conversation conversation);
    
    /**
     * Find messages by conversation ordered by creation time
     */
    List<Message> findByConversationOrderByCreatedAtDesc(Conversation conversation);
    
    /**
     * Find messages by conversation ordered by creation time ascending
     */
    List<Message> findByConversationOrderByCreatedAtAsc(Conversation conversation);
    
    /**
     * Find user messages in a conversation
     */
    List<Message> findByConversationAndIsUserMessageTrue(Conversation conversation);
    
    /**
     * Find AI messages in a conversation
     */
    List<Message> findByConversationAndIsUserMessageFalse(Conversation conversation);
    
    /**
     * Find messages by type
     */
    List<Message> findByConversationAndType(Conversation conversation, Message.MessageType type);
    
    /**
     * Find messages created in date range
     */
    @Query("SELECT m FROM Message m WHERE m.conversation = :conversation AND m.createdAt BETWEEN :startDate AND :endDate")
    List<Message> findByConversationAndCreatedAtBetween(@Param("conversation") Conversation conversation,
                                                       @Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);
    
    /**
     * Get message statistics
     */
    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation = :conversation")
    Long countByConversation(@Param("conversation") Conversation conversation);
    
    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation = :conversation AND m.isUserMessage = true")
    Long countUserMessagesByConversation(@Param("conversation") Conversation conversation);
    
    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation = :conversation AND m.isUserMessage = false")
    Long countAiMessagesByConversation(@Param("conversation") Conversation conversation);
    
    /**
     * Get average response time for AI messages
     */
    @Query("SELECT AVG(m.responseTimeMs) FROM Message m WHERE m.conversation = :conversation AND m.isUserMessage = false AND m.responseTimeMs > 0")
    Double getAverageResponseTimeByConversation(@Param("conversation") Conversation conversation);
}
