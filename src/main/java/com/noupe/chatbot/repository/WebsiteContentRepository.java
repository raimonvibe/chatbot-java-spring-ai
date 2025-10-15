package com.noupe.chatbot.repository;

import com.noupe.chatbot.model.Chatbot;
import com.noupe.chatbot.model.WebsiteContent;
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
}
