package com.prayer_chat.chatbot.repository;

import com.prayer_chat.chatbot.model.Conversation;
import com.prayer_chat.chatbot.model.Message;
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
    
    /**
     * Count user messages sent today by a chatbot owner
     * Counts distinct user messages (isUserMessage = true) created today
     * for all conversations belonging to chatbots owned by the user
     */
    @Query(value = "SELECT COUNT(m.id) FROM messages m " +
           "INNER JOIN conversations c ON m.conversation_id = c.id " +
           "INNER JOIN chatbots cb ON c.chatbot_id = cb.id " +
           "WHERE cb.owner_id = :userId " +
           "AND m.is_user_message = true " +
           "AND CAST(m.created_at AS DATE) = CURRENT_DATE", nativeQuery = true)
    Long countUserMessagesTodayByUserId(@Param("userId") Long userId);
    
    /**
     * Count user messages sent in the last 24 hours by a chatbot owner
     * Counts distinct user messages (isUserMessage = true) created in the last 24 hours
     * for all conversations belonging to chatbots owned by the user
     */
    @Query(value = "SELECT COUNT(m.id) FROM messages m " +
           "INNER JOIN conversations c ON m.conversation_id = c.id " +
           "INNER JOIN chatbots cb ON c.chatbot_id = cb.id " +
           "WHERE cb.owner_id = :userId " +
           "AND m.is_user_message = true " +
           "AND m.created_at >= :since", nativeQuery = true)
    Long countUserMessagesByUserIdSince(@Param("userId") Long userId, @Param("since") java.time.LocalDateTime since);

    /**
     * Count user messages sent today from a specific end-user IP (across all chatbot owners).
     * Used as an additional guardrail against multi-account abuse from the same network.
     */
    @Query(value = "SELECT COUNT(m.id) FROM messages m " +
           "INNER JOIN conversations c ON m.conversation_id = c.id " +
           "WHERE c.user_ip = :userIp " +
           "AND m.is_user_message = true " +
           "AND CAST(m.created_at AS DATE) = CURRENT_DATE", nativeQuery = true)
    Long countUserMessagesTodayByUserIp(@Param("userIp") String userIp);
}
