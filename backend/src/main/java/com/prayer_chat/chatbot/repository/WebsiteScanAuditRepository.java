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
     * Count scans for a user in a rolling window that starts at {@code date}.
     * Uses native query for H2 and PostgreSQL compatibility.
     */
    @Query(value = "SELECT COUNT(*) FROM website_scan_audits wsa " +
           "WHERE wsa.user_id = :userId " +
           "AND wsa.scan_date >= :date", nativeQuery = true)
    Long countScansByUserAndDateAfter(@Param("userId") Long userId, @Param("date") LocalDateTime date);
    
    /**
     * Count scans today for a user
     * Uses native query for H2 and PostgreSQL compatibility
     */
    @Query(value = "SELECT COUNT(*) FROM website_scan_audits wsa " +
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

    /**
     * Count website scans by user since the given date (e.g. start of current month).
     */
    @Query("SELECT COUNT(wsa) FROM WebsiteScanAudit wsa WHERE wsa.user.id = :userId AND wsa.scanDate >= :date")
    long countScansByUserAndScanDateAfter(@Param("userId") Long userId, @Param("date") LocalDateTime date);
}

