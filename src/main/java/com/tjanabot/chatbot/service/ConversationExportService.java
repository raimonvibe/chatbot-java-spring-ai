package com.tjanabot.chatbot.service;

import com.tjanabot.chatbot.model.Conversation;
import com.tjanabot.chatbot.model.Message;
import com.tjanabot.chatbot.repository.ConversationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.StringWriter;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * NEW FEATURE: Conversation Export Service
 * Exports conversation history in various formats (JSON, CSV)
 * 
 * @author ChatWeave Development Team
 */
@Service
public class ConversationExportService {

    private final ConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    public ConversationExportService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Export single conversation to JSON
     */
    public String exportConversationToJson(Long conversationId) throws Exception {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        Map<String, Object> exportData = buildConversationExportData(conversation);
        return objectMapper.writeValueAsString(exportData);
    }

    /**
     * Export multiple conversations to JSON
     */
    public String exportConversationsToJson(Long chatbotId) throws Exception {
        List<Conversation> conversations = conversationRepository.findByChatbotId(chatbotId);
        
        List<Map<String, Object>> exportDataList = new ArrayList<>();
        for (Conversation conversation : conversations) {
            exportDataList.add(buildConversationExportData(conversation));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("chatbot_id", chatbotId);
        result.put("export_date", new Date());
        result.put("total_conversations", conversations.size());
        result.put("conversations", exportDataList);

        return objectMapper.writeValueAsString(result);
    }

    /**
     * Export single conversation to CSV
     */
    public String exportConversationToCsv(Long conversationId) throws Exception {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        return buildCsvFromConversation(conversation);
    }

    /**
     * Export multiple conversations to CSV
     */
    public String exportConversationsToCsv(Long chatbotId) throws Exception {
        List<Conversation> conversations = conversationRepository.findByChatbotId(chatbotId);
        
        StringWriter writer = new StringWriter();
        
        // CSV Header
        writer.append("Conversation ID,User IP,Language,Created At,Last Message At,Message Count,")
              .append("Message ID,Timestamp,Sender,Message Type,Content,Response Time\n");

        // CSV Data
        for (Conversation conversation : conversations) {
            for (Message message : conversation.getMessages()) {
                writer.append(csvEscape(conversation.getId().toString())).append(",");
                writer.append(csvEscape(conversation.getUserIp())).append(",");
                writer.append(csvEscape(conversation.getUserLanguage())).append(",");
                writer.append(csvEscape(conversation.getCreatedAt().format(dateFormatter))).append(",");
                writer.append(csvEscape(conversation.getEndedAt() != null ?
                    conversation.getEndedAt().format(dateFormatter) : "")).append(",");
                writer.append(String.valueOf(conversation.getMessages().size())).append(",");
                writer.append(csvEscape(message.getId().toString())).append(",");
                writer.append(csvEscape(message.getCreatedAt().format(dateFormatter))).append(",");
                writer.append(message.getIsUserMessage() ? "User" : "Bot").append(",");
                writer.append(csvEscape(message.getType().toString())).append(",");
                writer.append(csvEscape(message.getContent())).append(",");
                writer.append(message.getResponseTimeMs() != null ? message.getResponseTimeMs().toString() : "").append("\n");
            }
        }

        return writer.toString();
    }

    /**
     * Build conversation export data map
     */
    private Map<String, Object> buildConversationExportData(Conversation conversation) {
        Map<String, Object> data = new HashMap<>();
        data.put("conversation_id", conversation.getId());
        data.put("user_ip", conversation.getUserIp());
        data.put("user_agent", conversation.getUserAgent());
        data.put("language", conversation.getUserLanguage());
        data.put("created_at", conversation.getCreatedAt());
        data.put("ended_at", conversation.getEndedAt());
        data.put("is_active", conversation.getIsActive());

        List<Map<String, Object>> messages = new ArrayList<>();
        for (Message message : conversation.getMessages()) {
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("message_id", message.getId());
            messageData.put("timestamp", message.getCreatedAt());
            messageData.put("sender", message.getIsUserMessage() ? "user" : "bot");
            messageData.put("type", message.getType());
            messageData.put("content", message.getContent());
            messageData.put("response_time_ms", message.getResponseTimeMs());
            messages.add(messageData);
        }
        data.put("messages", messages);
        data.put("message_count", messages.size());

        return data;
    }

    /**
     * Build CSV from single conversation
     */
    private String buildCsvFromConversation(Conversation conversation) {
        StringWriter writer = new StringWriter();

        // CSV Header
        writer.append("Message ID,Timestamp,Sender,Message Type,Content,Response Time (ms)\n");

        // CSV Data
        for (Message message : conversation.getMessages()) {
            writer.append(csvEscape(message.getId().toString())).append(",");
            writer.append(csvEscape(message.getCreatedAt().format(dateFormatter))).append(",");
            writer.append(message.getIsUserMessage() ? "User" : "Bot").append(",");
            writer.append(csvEscape(message.getType().toString())).append(",");
            writer.append(csvEscape(message.getContent())).append(",");
            writer.append(message.getResponseTimeMs() != null ? message.getResponseTimeMs().toString() : "").append("\n");
        }

        return writer.toString();
    }

    /**
     * Escape CSV special characters
     */
    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
