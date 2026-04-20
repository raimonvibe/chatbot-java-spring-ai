package com.prayer_chat.chatbot.repository;

import com.prayer_chat.chatbot.model.Chatbot;
import com.prayer_chat.chatbot.model.WebsiteContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for WebsiteContent entities
 */
@Repository
public interface WebsiteContentRepository extends JpaRepository<WebsiteContent, Long> {
    
    /**
     * Find all website content for a specific chatbot
     */
    List<WebsiteContent> findByChatbot(Chatbot chatbot);

    /**
     * Find website content for a chatbot with pagination (for memory-efficient batch indexing).
     */
    Page<WebsiteContent> findByChatbot(Chatbot chatbot, Pageable pageable);

    /**
     * Delete all website content for a chatbot (used before re-scan to avoid duplicates).
     */
    void deleteByChatbot(Chatbot chatbot);
    
    /**
     * Find indexed content for a chatbot
     */
    List<WebsiteContent> findByChatbotAndIsIndexedTrue(Chatbot chatbot);
    
    /**
     * Find content by URL
     */
    WebsiteContent findByUrl(String url);
    
    /**
     * Find content by chatbot and URL
     */
    WebsiteContent findByChatbotAndUrl(Chatbot chatbot, String url);
    
    /**
     * Get content statistics for a chatbot
     */
    @Query("SELECT COUNT(wc) FROM WebsiteContent wc WHERE wc.chatbot = :chatbot")
    Long countByChatbot(@Param("chatbot") Chatbot chatbot);
    
    /**
     * Get indexed content count for a chatbot
     */
    @Query("SELECT COUNT(wc) FROM WebsiteContent wc WHERE wc.chatbot = :chatbot AND wc.isIndexed = true")
    Long countIndexedByChatbot(@Param("chatbot") Chatbot chatbot);
    
    /**
     * Find content by language
     */
    List<WebsiteContent> findByChatbotAndLanguage(Chatbot chatbot, String language);
    
    /**
     * Find content with minimum word count
     */
    @Query("SELECT wc FROM WebsiteContent wc WHERE wc.chatbot = :chatbot AND wc.wordCount >= :minWords")
    List<WebsiteContent> findByChatbotWithMinWords(@Param("chatbot") Chatbot chatbot, @Param("minWords") Integer minWords);

    /**
     * Keyword search scoped to a chatbot. Used as a fallback when vector retrieval is unavailable (e.g. in-memory store wiped on restart).
     * SECURITY: query is parameterized and always scoped to the given chatbot.
     */
    @Query("""
        SELECT wc FROM WebsiteContent wc
        WHERE wc.chatbot = :chatbot
          AND (
            LOWER(wc.title) LIKE CONCAT('%', LOWER(:q), '%')
            OR LOWER(wc.url) LIKE CONCAT('%', LOWER(:q), '%')
            OR LOWER(wc.content) LIKE CONCAT('%', LOWER(:q), '%')
          )
        ORDER BY
          CASE WHEN LOWER(wc.url) LIKE CONCAT('%/', LOWER(:q), '/%') THEN 0 ELSE 1 END,
          CASE WHEN LOWER(wc.url) LIKE CONCAT('%', LOWER(:q), '%') THEN 0 ELSE 1 END,
          CASE WHEN LOWER(wc.title) LIKE CONCAT('%', LOWER(:q), '%') THEN 0 ELSE 1 END,
          CASE WHEN LOWER(wc.content) LIKE CONCAT('%', LOWER(:q), '%') THEN 0 ELSE 1 END,
          wc.wordCount DESC,
          wc.id DESC
        """)
    Page<WebsiteContent> searchByChatbotAndKeyword(@Param("chatbot") Chatbot chatbot, @Param("q") String q, Pageable pageable);
    
    /**
     * Count scans today for a chatbot (based on chatbot owner)
     * This counts how many times a chatbot's website was scanned today
     * Uses native query for H2 and PostgreSQL compatibility
     */
    @Query(value = "SELECT COUNT(DISTINCT wc.chatbot_id) FROM website_contents wc " +
           "INNER JOIN chatbots c ON wc.chatbot_id = c.id " +
           "WHERE c.owner_id = :userId " +
           "AND CAST(wc.created_at AS DATE) = CURRENT_DATE", nativeQuery = true)
    Long countScansTodayByUserId(@Param("userId") Long userId);
    
    /**
     * Count scans today for a specific chatbot
     * Uses native query for H2 and PostgreSQL compatibility
     */
    @Query(value = "SELECT COUNT(DISTINCT CAST(wc.created_at AS DATE)) FROM website_contents wc " +
           "WHERE wc.chatbot_id = :chatbotId " +
           "AND CAST(wc.created_at AS DATE) = CURRENT_DATE", nativeQuery = true)
    Long countScansTodayByChatbot(@Param("chatbotId") Long chatbotId);
    
    /**
     * Count website scans by a user after a specific date.
     * A "scan" is represented by a WebsiteContent entry created on a specific date.
     * This counts distinct dates when scans occurred.
     * Uses native query for H2 and PostgreSQL compatibility
     */
    @Query(value = "SELECT COUNT(DISTINCT CAST(wc.created_at AS DATE)) FROM website_contents wc " +
           "INNER JOIN chatbots c ON wc.chatbot_id = c.id " +
           "WHERE c.owner_id = :userId " +
           "AND wc.created_at >= :date", nativeQuery = true)
    Long countScansByUserAndDateAfter(@Param("userId") Long userId, @Param("date") java.time.LocalDateTime date);
}
