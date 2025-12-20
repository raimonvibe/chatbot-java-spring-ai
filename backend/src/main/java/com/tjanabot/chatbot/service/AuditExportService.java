package com.tjanabot.chatbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tjanabot.chatbot.model.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for exporting audit logs in various formats
 */
@Service
public class AuditExportService {

    @Autowired
    private AuditService auditService;

    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Export audit logs as CSV
     */
    public byte[] exportAsCSV(Long userId, LocalDateTime start, LocalDateTime end) throws IOException {
        List<AuditLog> logs = auditService.getAllAuditLogsForExport(userId, start, end);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8));

        // Write CSV header
        writer.println("Timestamp,User Email,Event Type,Severity,Action,Description,Resource Type,Resource ID,IP Address,User Agent");

        // Write data rows
        for (AuditLog log : logs) {
            writer.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                escapeCSV(formatDate(log.getCreatedAt())),
                escapeCSV(log.getUser() != null ? log.getUser().getEmail() : "N/A"),
                escapeCSV(log.getEventType().name()),
                escapeCSV(log.getSeverity().name()),
                escapeCSV(log.getAction()),
                escapeCSV(log.getDescription()),
                escapeCSV(log.getResourceType()),
                escapeCSV(log.getResourceId()),
                escapeCSV(log.getIpAddress()),
                escapeCSV(log.getUserAgent())
            );
        }

        writer.flush();
        return baos.toByteArray();
    }

    /**
     * Export audit logs as JSON
     */
    public byte[] exportAsJSON(Long userId, LocalDateTime start, LocalDateTime end) throws IOException {
        List<AuditLog> logs = auditService.getAllAuditLogsForExport(userId, start, end);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Create export DTO to avoid circular references
        List<AuditLogExportDTO> exportLogs = logs.stream()
            .map(this::toExportDTO)
            .toList();

        return mapper.writeValueAsBytes(exportLogs);
    }

    /**
     * Convert AuditLog to export DTO
     */
    private AuditLogExportDTO toExportDTO(AuditLog log) {
        AuditLogExportDTO dto = new AuditLogExportDTO();
        dto.id = log.getId();
        dto.timestamp = log.getCreatedAt();
        dto.userEmail = log.getUser() != null ? log.getUser().getEmail() : null;
        dto.eventType = log.getEventType().name();
        dto.severity = log.getSeverity().name();
        dto.action = log.getAction();
        dto.description = log.getDescription();
        dto.resourceType = log.getResourceType();
        dto.resourceId = log.getResourceId();
        dto.ipAddress = log.getIpAddress();
        dto.userAgent = log.getUserAgent();
        dto.metadata = log.getMetadata();
        return dto;
    }

    /**
     * Escape CSV special characters
     */
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }

        // If value contains comma, quote, or newline, wrap in quotes and escape quotes
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }

        return value;
    }

    /**
     * Format date for CSV
     */
    private String formatDate(LocalDateTime date) {
        if (date == null) {
            return "";
        }
        return date.format(DATE_FORMATTER);
    }

    // Additional methods for test support (String-based exports)

    /**
     * Export audit logs to CSV format (String output for tests)
     */
    public String exportToCsv(LocalDateTime start, LocalDateTime end) {
        List<AuditLog> logs = auditService.getAuditLogsBetween(start, end);
        return convertToCsvString(logs);
    }

    /**
     * Export user-specific audit logs to CSV format (String output for tests)
     */
    public String exportUserLogsToCsv(Long userId) {
        List<AuditLog> logs = auditService.getAuditLogsForUser(userId);
        return convertToCsvString(logs);
    }

    /**
     * Export audit logs to JSON format (String output for tests)
     */
    public String exportToJson(LocalDateTime start, LocalDateTime end) {
        List<AuditLog> logs = auditService.getAuditLogsBetween(start, end);
        return convertToJsonString(logs);
    }

    /**
     * Convert audit logs to CSV string
     */
    private String convertToCsvString(List<AuditLog> logs) {
        StringBuilder csv = new StringBuilder();
        csv.append("ID,Timestamp,User ID,Event Type,Severity,Description,IP Address\n");

        for (AuditLog log : logs) {
            csv.append(log.getId()).append(",");
            csv.append(formatDate(log.getCreatedAt())).append(",");
            csv.append(log.getUser() != null ? log.getUser().getId() : "").append(",");
            csv.append(log.getEventType()).append(",");
            csv.append(log.getSeverity()).append(",");
            csv.append(escapeCSV(log.getDescription())).append(",");
            csv.append(log.getIpAddress() != null ? log.getIpAddress() : "");
            csv.append("\n");
        }

        return csv.toString();
    }

    /**
     * Convert audit logs to JSON string
     */
    private String convertToJsonString(List<AuditLog> logs) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            List<AuditLogExportDTO> exportLogs = logs.stream()
                .map(this::toExportDTO)
                .toList();

            return mapper.writeValueAsString(exportLogs);
        } catch (IOException e) {
            throw new RuntimeException("Failed to convert audit logs to JSON", e);
        }
    }

    /**
     * DTO for audit log export (avoids circular references and includes only necessary fields)
     */
    public static class AuditLogExportDTO {
        public Long id;
        public LocalDateTime timestamp;
        public String userEmail;
        public String eventType;
        public String severity;
        public String action;
        public String description;
        public String resourceType;
        public String resourceId;
        public String ipAddress;
        public String userAgent;
        public java.util.Map<String, String> metadata;
    }
}
