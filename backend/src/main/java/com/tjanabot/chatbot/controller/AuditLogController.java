package com.tjanabot.chatbot.controller;

import com.tjanabot.chatbot.model.AuditLog;
import com.tjanabot.chatbot.model.User;
import com.tjanabot.chatbot.security.CustomOAuth2User;
import com.tjanabot.chatbot.service.AuditExportService;
import com.tjanabot.chatbot.service.AuditService;
import com.tjanabot.chatbot.util.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Controller for audit log access and export
 */
@RestController
@RequestMapping("/api/audit")
public class AuditLogController {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogController.class);

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditExportService auditExportService;

    /**
     * Get user's audit logs with pagination
     */
    @GetMapping
    public ResponseEntity<Page<AuditLog>> getAuditLogs(
            @AuthenticationPrincipal CustomOAuth2User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        User user = currentUser.getUser();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<AuditLog> logs;
        if (startDate != null && endDate != null) {
            logs = auditService.getAuditLogsByDateRange(user.getId(), startDate, endDate, pageable);
        } else {
            logs = auditService.getUserAuditLogs(user.getId(), pageable);
        }

        logger.info("Retrieved {} audit logs for user: {}",
            logs.getTotalElements(), LogSanitizer.sanitize(user.getEmail()));

        // Log the export request itself
        auditService.log(
            AuditLog.EventType.SECURITY_DATA_EXPORT,
            AuditLog.Severity.INFO,
            "Audit logs viewed",
            user,
            String.format("Viewed page %d of audit logs", page),
            null
        );

        return ResponseEntity.ok(logs);
    }

    /**
     * Export audit logs as CSV
     */
    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportAsCSV(
            @AuthenticationPrincipal CustomOAuth2User currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        User user = currentUser.getUser();

        try {
            byte[] csvData = auditExportService.exportAsCSV(user.getId(), startDate, endDate);

            // Log the export
            auditService.log(
                AuditLog.EventType.SECURITY_DATA_EXPORT,
                AuditLog.Severity.INFO,
                "Audit logs exported as CSV",
                user,
                String.format("Exported audit logs from %s to %s", startDate, endDate),
                null
            );

            logger.info("Exported audit logs as CSV for user: {}",
                LogSanitizer.sanitize(user.getEmail()));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv"));
            headers.setContentDispositionFormData("attachment",
                String.format("audit-logs-%s.csv", LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));

            return new ResponseEntity<>(csvData, headers, HttpStatus.OK);

        } catch (IOException e) {
            logger.error("Error exporting audit logs as CSV for user {}: {}",
                LogSanitizer.sanitize(user.getEmail()),
                LogSanitizer.sanitizeException(e));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Export audit logs as JSON
     */
    @GetMapping("/export/json")
    public ResponseEntity<byte[]> exportAsJSON(
            @AuthenticationPrincipal CustomOAuth2User currentUser,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        User user = currentUser.getUser();

        try {
            byte[] jsonData = auditExportService.exportAsJSON(user.getId(), startDate, endDate);

            // Log the export
            auditService.log(
                AuditLog.EventType.SECURITY_DATA_EXPORT,
                AuditLog.Severity.INFO,
                "Audit logs exported as JSON",
                user,
                String.format("Exported audit logs from %s to %s", startDate, endDate),
                null
            );

            logger.info("Exported audit logs as JSON for user: {}",
                LogSanitizer.sanitize(user.getEmail()));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDispositionFormData("attachment",
                String.format("audit-logs-%s.json", LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))));

            return new ResponseEntity<>(jsonData, headers, HttpStatus.OK);

        } catch (IOException e) {
            logger.error("Error exporting audit logs as JSON for user {}: {}",
                LogSanitizer.sanitize(user.getEmail()),
                LogSanitizer.sanitizeException(e));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get security events (for admin/monitoring)
     */
    @GetMapping("/security-events")
    public ResponseEntity<Page<AuditLog>> getSecurityEvents(
            @AuthenticationPrincipal CustomOAuth2User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        User user = currentUser.getUser();

        // Only allow users to see their own security events (admins could see all, but that's a future enhancement)
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AuditLog> logs = auditService.getUserAuditLogs(user.getId(), pageable);

        logger.info("Retrieved security events for user: {}",
            LogSanitizer.sanitize(user.getEmail()));

        return ResponseEntity.ok(logs);
    }
}
