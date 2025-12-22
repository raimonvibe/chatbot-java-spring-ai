package com.prayer_chat.chatbot.repository;

import com.prayer_chat.chatbot.model.WebsiteScanAudit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Repository for WebsiteScanAudit entities
 * Used to track website scans independently of chatbots to prevent abuse
 */
@Repository
public interface WebsiteScanAuditRepository extends JpaRepository<WebsiteScanAudit, Long> {
    
    /**
     * Count distinct scan dates for a user after a specific date.
     * This counts how many different days the user has scanned websites.
     * Uses native query for H2 and PostgreSQL compatibility
     * 
     * @param userId The user ID
     * @param date The date to count from
     * @return Number of distinct scan dates
     */
    @Query(value = "SELECT COUNT(DISTINCT CAST(wsa.scan_date AS DATE)) FROM website_scan_audits wsa " +
           "WHERE wsa.user_id = :userId " +
           "AND wsa.scan_date >= :date", nativeQuery = true)
    Long countDistinctScanDatesByUserAndDateAfter(@Param("userId") Long userId, @Param("date") LocalDateTime date);
    
    /**
     * Count scans today for a user
     * Uses native query for H2 and PostgreSQL compatibility
     */
    @Query(value = "SELECT COUNT(DISTINCT CAST(wsa.scan_date AS DATE)) FROM website_scan_audits wsa " +
           "WHERE wsa.user_id = :userId " +
           "AND CAST(wsa.scan_date AS DATE) = CURRENT_DATE", nativeQuery = true)
    Long countScansTodayByUserId(@Param("userId") Long userId);
    
    /**
     * Get total cost of scans for a user in the current month
     */
    @Query("SELECT COALESCE(SUM(wsa.estimatedCost), 0) FROM WebsiteScanAudit wsa " +
           "WHERE wsa.user.id = :userId " +
           "AND YEAR(wsa.scanDate) = YEAR(CURRENT_DATE) " +
           "AND MONTH(wsa.scanDate) = MONTH(CURRENT_DATE)")
    java.math.BigDecimal getTotalCostThisMonthByUserId(@Param("userId") Long userId);
}

