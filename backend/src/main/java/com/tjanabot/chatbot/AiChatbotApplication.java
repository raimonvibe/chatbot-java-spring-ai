package com.tjanabot.chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the AI Chatbot System
 * 
 * This application provides:
 * - Website content analysis and crawling
 * - AI-powered chatbot generation
 * - Multi-language support
 * - Custom branding and styling
 * - Conversation tracking and analytics
 * 
 * @author TjanaBot Development Team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class AiChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiChatbotApplication.class, args);
        System.out.println("\n🚀 TjanaBot AI Chatbot System is running!");
        System.out.println("📊 Frontend Dashboard: http://localhost:3000");
        System.out.println("🔧 Backend API: http://localhost:8081");
        System.out.println("💾 H2 Console: http://localhost:8081/h2-console\n");
    }
}
